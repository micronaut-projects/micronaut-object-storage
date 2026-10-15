package spike;

import com.zaxxer.hikari.HikariDataSource;
import io.micronaut.context.ApplicationContext;
import io.micronaut.core.type.Argument;
import io.micronaut.data.jdbc.config.DataJdbcConfiguration;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.objectstorage.ObjectStorageEntry;
import io.micronaut.objectstorage.ObjectStorageOperations;
import io.micronaut.objectstorage.request.UploadRequest;
import io.micronaut.transaction.TransactionOperations;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.CRC32C;

/**
 * Streaming benchmark for the database-backed object storage spike.
 * Usage: Bench &lt;store&gt; [bigMB] [clients] [smallMB] [clientMBps]
 * Stores: pg-chunked, pg-lo, ora-chunked, ora-blob, ora-blob-held, local.
 */
public final class Bench {

    private static final int MB = 1024 * 1024;
    private static final String BUCKET = "bench";

    interface Target {
        long upload(String key, InputStream in, long length);

        InputStream open(String key);
    }

    public static void main(String[] args) throws Exception {
        String store = args.length > 0 ? args[0] : "pg-lo";
        int bigMb = args.length > 1 ? Integer.parseInt(args[1]) : 1024;
        int clients = args.length > 2 ? Integer.parseInt(args[2]) : 50;
        int smallMb = args.length > 3 ? Integer.parseInt(args[3]) : 64;
        int clientMbps = args.length > 4 ? Integer.parseInt(args[4]) : 8;
        int window = Integer.getInteger("window", store.contains("chunked") ? MB : 4 * MB);

        Map<String, Object> props = new HashMap<>();
        props.put("datasources.pg.url", "jdbc:postgresql://localhost:5432/spike");
        props.put("datasources.pg.username", "spike");
        props.put("datasources.pg.password", "spike");
        props.put("datasources.pg.dialect", "POSTGRES");
        props.put("datasources.pg.maximum-pool-size", 10);
        props.put("datasources.pg.connection-timeout", 30000);
        props.put("datasources.oracle.url", "jdbc:oracle:thin:@localhost:1521/FREEPDB1");
        props.put("datasources.oracle.username", "spike");
        props.put("datasources.oracle.password", "spike");
        props.put("datasources.oracle.dialect", "ORACLE");
        props.put("datasources.oracle.maximum-pool-size", 10);
        props.put("datasources.oracle.connection-timeout", 30000);
        props.put("micronaut.object-storage.local.default.path", "/tmp/spike-local");

        long startNanos = System.nanoTime();
        try (ApplicationContext ctx = ApplicationContext.run(props)) {
            System.out.printf("context started in %d ms%n", (System.nanoTime() - startNanos) / 1_000_000);
            ExecutorService prefetch = Executors.newVirtualThreadPerTaskExecutor();
            String ds = store.startsWith("pg") ? "pg" : "oracle";
            HikariDataSource hikari = null;
            Target target;
            if (store.equals("local")) {
                ObjectStorageOperations<?, ?, ?> local = ctx.getBean(ObjectStorageOperations.class);
                target = new Target() {
                    @Override
                    public long upload(String key, InputStream in, long length) {
                        local.upload(UploadRequest.fromInputStream(in, key, length));
                        return length;
                    }

                    @Override
                    public InputStream open(String key) {
                        return local.<ObjectStorageEntry<?>>retrieve(key).orElseThrow().getInputStream();
                    }
                };
            } else {
                DataJdbcConfiguration cfg = ctx.getBean(DataJdbcConfiguration.class, Qualifiers.byName(ds));
                System.out.printf("dialect for datasource '%s' resolved from configuration: %s%n", ds, cfg.getDialect());
                DataSource dataSource = ctx.getBean(DataSource.class, Qualifiers.byName(ds));
                hikari = dataSource.unwrap(HikariDataSource.class);
                @SuppressWarnings("unchecked")
                TransactionOperations<Connection> tx = ctx.getBean(Argument.of(TransactionOperations.class, Connection.class), Qualifiers.byName(ds));
                Stores.Dialect dialect = ds.equals("pg") ? Stores.Dialect.POSTGRES : Stores.Dialect.ORACLE;
                Stores.createSchema(hikari, dialect);
                Stores.Store s = switch (store) {
                    case "pg-chunked", "ora-chunked" -> new Stores.ChunkedStore(dialect, tx, prefetch, window);
                    case "pg-lo" -> new Stores.PgLargeObjectStore(tx, prefetch, window);
                    case "ora-blob" -> new Stores.OracleBlobStore(tx, hikari, prefetch, window, false);
                    case "ora-blob-held" -> new Stores.OracleBlobStore(tx, hikari, prefetch, window, true);
                    default -> throw new IllegalArgumentException(store);
                };
                target = new Target() {
                    @Override
                    public long upload(String key, InputStream in, long length) {
                        return s.upload(BUCKET, key, in);
                    }

                    @Override
                    public InputStream open(String key) {
                        return s.retrieve(BUCKET, key);
                    }
                };
            }
            System.out.printf("store=%s window=%dKB heapMax=%dMB%n", store, window / 1024, Runtime.getRuntime().maxMemory() / MB);

            // 1. Big upload and download
            long bigBytes = (long) bigMb * MB;
            CRC32C expected = new CRC32C();
            resetHeapPeak();
            long t0 = System.nanoTime();
            long stored = target.upload("big", new GeneratedInputStream(bigBytes, expected), bigBytes);
            double upSecs = secs(t0);
            long upHeap = heapPeakMb();
            check(stored == bigBytes, "stored " + stored);

            resetHeapPeak();
            t0 = System.nanoTime();
            CRC32C actual = new CRC32C();
            long read;
            try (InputStream in = target.open("big")) {
                read = drain(in, actual, 0);
            }
            double downSecs = secs(t0);
            long downHeap = heapPeakMb();
            check(read == bigBytes && actual.getValue() == expected.getValue(), "download mismatch");
            System.out.printf("RESULT %s big=%dMB upload=%.1fs (%.0f MB/s, heap peak %d MB) download=%.1fs (%.0f MB/s, heap peak %d MB)%n",
                store, bigMb, upSecs, bigMb / upSecs, upHeap, downSecs, bigMb / downSecs, downHeap);

            // 2. Many slow concurrent clients
            long smallBytes = (long) smallMb * MB;
            CRC32C smallCrc = new CRC32C();
            target.upload("small", new GeneratedInputStream(smallBytes, smallCrc), smallBytes);
            HikariDataSource pool = hikari;
            AtomicInteger maxActive = new AtomicInteger();
            AtomicBoolean monitoring = new AtomicBoolean(true);
            Thread monitor = Thread.ofPlatform().daemon().start(() -> {
                while (monitoring.get()) {
                    if (pool != null) {
                        maxActive.accumulateAndGet(pool.getHikariPoolMXBean().getActiveConnections(), Math::max);
                    }
                    try {
                        Thread.sleep(2);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            });
            resetHeapPeak();
            ExecutorService clientPool = Executors.newFixedThreadPool(clients);
            List<Future<Long>> futures = new ArrayList<>();
            t0 = System.nanoTime();
            long nanosPer64k = (long) (64.0 * 1024 / (clientMbps * (double) MB) * 1e9);
            for (int i = 0; i < clients; i++) {
                futures.add(clientPool.submit(() -> {
                    long c0 = System.nanoTime();
                    CRC32C crc = new CRC32C();
                    try (InputStream in = target.open("small")) {
                        drain(in, crc, nanosPer64k);
                    }
                    check(crc.getValue() == smallCrc.getValue(), "slow client checksum mismatch");
                    return System.nanoTime() - c0;
                }));
            }
            int failures = 0;
            long slowest = 0;
            String firstError = null;
            for (Future<Long> f : futures) {
                try {
                    slowest = Math.max(slowest, f.get(10, TimeUnit.MINUTES));
                } catch (Exception e) {
                    failures++;
                    if (firstError == null) {
                        firstError = rootMessage(e);
                    }
                }
            }
            double wall = secs(t0);
            monitoring.set(false);
            monitor.join();
            clientPool.shutdownNow();
            System.out.printf("RESULT %s slowClients=%d x %dMB at %d MB/s each: wall=%.1fs slowestClient=%.1fs (ideal %.1fs) failures=%d maxActiveConnections=%s/10 heapPeak=%d MB%s%n",
                store, clients, smallMb, clientMbps, wall, slowest / 1e9, (double) smallMb / clientMbps, failures,
                pool == null ? "n/a" : maxActive.get(), heapPeakMb(), firstError == null ? "" : " firstError=" + firstError);
            prefetch.shutdownNow();
        }
    }

    private static long drain(InputStream in, CRC32C crc, long nanosPer64k) throws IOException, InterruptedException {
        byte[] buf = new byte[64 * 1024];
        long total = 0;
        int n;
        while ((n = in.readNBytes(buf, 0, buf.length)) > 0) {
            crc.update(buf, 0, n);
            total += n;
            if (nanosPer64k > 0) {
                Thread.sleep(nanosPer64k / 1_000_000, (int) (nanosPer64k % 1_000_000));
            }
        }
        return total;
    }

    private static void resetHeapPeak() {
        System.gc();
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getType() == MemoryType.HEAP) {
                pool.resetPeakUsage();
            }
        }
    }

    private static long heapPeakMb() {
        long peak = 0;
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getType() == MemoryType.HEAP) {
                peak += pool.getPeakUsage().getUsed();
            }
        }
        return peak / MB;
    }

    private static double secs(long t0) {
        return (System.nanoTime() - t0) / 1e9;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static String rootMessage(Throwable t) {
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return t.getClass().getSimpleName() + ": " + t.getMessage();
    }

    /** Deterministic pseudo-random content generated on the fly, so the payload never sits in memory. */
    static final class GeneratedInputStream extends InputStream {
        private static final byte[] BLOCK = new byte[MB];

        static {
            new Random(42).nextBytes(BLOCK);
        }

        private final long length;
        private final CRC32C crc;
        private long pos;

        GeneratedInputStream(long length, CRC32C crc) {
            this.length = length;
            this.crc = crc;
        }

        @Override
        public int read() {
            byte[] b = new byte[1];
            return read(b, 0, 1) < 0 ? -1 : b[0] & 0xff;
        }

        @Override
        public int read(byte[] b, int off, int len) {
            if (pos >= length) {
                return -1;
            }
            int blockOff = (int) ((pos + pos / MB) % MB);
            int n = (int) Math.min(Math.min(len, length - pos), MB - blockOff);
            System.arraycopy(BLOCK, blockOff, b, off, n);
            crc.update(b, off, n);
            pos += n;
            return n;
        }
    }
}
