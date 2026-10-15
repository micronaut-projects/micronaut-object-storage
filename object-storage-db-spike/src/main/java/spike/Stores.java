package spike;

import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.TransactionOperations;
import org.postgresql.PGConnection;
import org.postgresql.largeobject.LargeObject;
import org.postgresql.largeobject.LargeObjectManager;

import javax.sql.DataSource;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.Executor;

/**
 * Spike content stores. Everything runs on Micronaut Data JDBC's runtime pieces: the datasource's
 * {@link TransactionOperations} for transactions and connections, and plain SQL templates chosen by dialect.
 */
final class Stores {

    enum Dialect { POSTGRES, ORACLE }

    record ObjectRef(long id, long length) { }

    abstract static class Store {
        final String name;
        final Dialect dialect;
        final TransactionOperations<Connection> tx;
        final Executor executor;
        final int windowSize;

        Store(String name, Dialect dialect, TransactionOperations<Connection> tx, Executor executor, int windowSize) {
            this.name = name;
            this.dialect = dialect;
            this.tx = tx;
            this.executor = executor;
            this.windowSize = windowSize;
        }

        abstract void write(Connection c, long objectId, InputStream in) throws Exception;

        abstract InputStream open(ObjectRef ref);

        void beforeDelete(Connection c, long objectId) throws SQLException { }

        /** Uploads in one transaction (propagation REQUIRED). Returns the number of bytes stored. */
        long upload(String bucket, String key, InputStream in) {
            return tx.execute(TransactionDefinition.DEFAULT, status -> {
                Connection c = status.getConnection();
                try {
                    Long existing = findId(c, bucket, key);
                    if (existing != null) {
                        beforeDelete(c, existing);
                        try (PreparedStatement ps = c.prepareStatement("delete from mn_os_object where id = ?")) {
                            ps.setLong(1, existing);
                            ps.executeUpdate();
                        }
                    }
                    long id = insertObject(c, bucket, key);
                    CountingInputStream counting = new CountingInputStream(in);
                    write(c, id, counting);
                    try (PreparedStatement ps = c.prepareStatement("update mn_os_object set content_length = ? where id = ?")) {
                        ps.setLong(1, counting.count);
                        ps.setLong(2, id);
                        ps.executeUpdate();
                    }
                    return counting.count;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }

        ObjectRef lookup(String bucket, String key) {
            return tx.executeRead(status -> {
                try (PreparedStatement ps = status.getConnection().prepareStatement(
                    "select id, content_length from mn_os_object where bucket = ? and object_key = ?")) {
                    ps.setString(1, bucket);
                    ps.setString(2, key);
                    try (ResultSet rs = ps.executeQuery()) {
                        return rs.next() ? new ObjectRef(rs.getLong(1), rs.getLong(2)) : null;
                    }
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            });
        }

        InputStream retrieve(String bucket, String key) {
            return open(lookup(bucket, key));
        }

        static Long findId(Connection c, String bucket, String key) throws SQLException {
            try (PreparedStatement ps = c.prepareStatement("select id from mn_os_object where bucket = ? and object_key = ?")) {
                ps.setString(1, bucket);
                ps.setString(2, key);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getLong(1) : null;
                }
            }
        }

        long insertObject(Connection c, String bucket, String key) throws SQLException {
            String sql = dialect == Dialect.ORACLE
                ? "insert into mn_os_object (bucket, object_key, content) values (?, ?, empty_blob())"
                : "insert into mn_os_object (bucket, object_key) values (?, ?)";
            try (PreparedStatement ps = c.prepareStatement(sql, new String[]{dialect == Dialect.ORACLE ? "ID" : "id"})) {
                ps.setString(1, bucket);
                ps.setString(2, key);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    rs.next();
                    return rs.getLong(1);
                }
            }
        }
    }

    /** Generic layout: fixed-size chunk rows, portable across dialects. */
    static final class ChunkedStore extends Store {
        ChunkedStore(Dialect dialect, TransactionOperations<Connection> tx, Executor executor, int chunkSize) {
            super("chunked", dialect, tx, executor, chunkSize);
        }

        @Override
        void write(Connection c, long objectId, InputStream in) throws Exception {
            try (PreparedStatement ps = c.prepareStatement("insert into mn_os_chunk (object_id, seq, data) values (?, ?, ?)")) {
                byte[] buf = new byte[windowSize];
                int seq = 0;
                int n;
                while ((n = in.readNBytes(buf, 0, windowSize)) > 0) {
                    ps.setLong(1, objectId);
                    ps.setInt(2, seq++);
                    if (n == windowSize) {
                        ps.setBytes(3, buf);
                    } else {
                        byte[] last = new byte[n];
                        System.arraycopy(buf, 0, last, 0, n);
                        ps.setBytes(3, last);
                    }
                    ps.executeUpdate();
                }
            }
        }

        @Override
        InputStream open(ObjectRef ref) {
            return new WindowedInputStream((offset, len) -> tx.executeRead(status -> {
                try (PreparedStatement ps = status.getConnection().prepareStatement(
                    "select data from mn_os_chunk where object_id = ? and seq = ?")) {
                    ps.setLong(1, ref.id());
                    ps.setInt(2, (int) (offset / windowSize));
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        return rs.getBytes(1);
                    }
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }), ref.length(), windowSize, executor);
        }
    }

    /** PostgreSQL module: Large Objects, windowed reads with lo_lseek64. */
    static final class PgLargeObjectStore extends Store {
        PgLargeObjectStore(TransactionOperations<Connection> tx, Executor executor, int windowSize) {
            super("pg-large-object", Dialect.POSTGRES, tx, executor, windowSize);
        }

        @Override
        void write(Connection c, long objectId, InputStream in) throws Exception {
            LargeObjectManager lom = c.unwrap(PGConnection.class).getLargeObjectAPI();
            long oid = lom.createLO(LargeObjectManager.READWRITE);
            LargeObject lo = lom.open(oid, LargeObjectManager.WRITE);
            try (OutputStream os = lo.getOutputStream()) {
                in.transferTo(os);
            }
            try (PreparedStatement ps = c.prepareStatement("update mn_os_object set content_oid = ? where id = ?")) {
                ps.setLong(1, oid);
                ps.setLong(2, objectId);
                ps.executeUpdate();
            }
        }

        @Override
        void beforeDelete(Connection c, long objectId) throws SQLException {
            try (PreparedStatement ps = c.prepareStatement(
                "select lo_unlink(content_oid) from mn_os_object where id = ? and content_oid is not null")) {
                ps.setLong(1, objectId);
                ps.executeQuery().close();
            }
        }

        @Override
        InputStream open(ObjectRef ref) {
            long oid = tx.executeRead(status -> {
                try (PreparedStatement ps = status.getConnection().prepareStatement("select content_oid from mn_os_object where id = ?")) {
                    ps.setLong(1, ref.id());
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        return rs.getLong(1);
                    }
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            });
            return new WindowedInputStream((offset, len) -> tx.executeRead(status -> {
                try {
                    LargeObjectManager lom = status.getConnection().unwrap(PGConnection.class).getLargeObjectAPI();
                    LargeObject lo = lom.open(oid, LargeObjectManager.READ);
                    try {
                        lo.seek64(offset, LargeObject.SEEK_SET);
                        return lo.read(len);
                    } finally {
                        lo.close();
                    }
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }), ref.length(), windowSize, executor);
        }
    }

    /** Oracle module: one SecureFile BLOB, windowed reads with getBinaryStream(pos, len). */
    static final class OracleBlobStore extends Store {
        private final boolean holdConnection;
        private final DataSource dataSource;

        OracleBlobStore(TransactionOperations<Connection> tx, DataSource dataSource, Executor executor, int windowSize, boolean holdConnection) {
            super(holdConnection ? "oracle-blob-held" : "oracle-blob-windowed", Dialect.ORACLE, tx, executor, windowSize);
            this.holdConnection = holdConnection;
            this.dataSource = dataSource;
        }

        @Override
        void write(Connection c, long objectId, InputStream in) throws Exception {
            try (PreparedStatement ps = c.prepareStatement("select content from mn_os_object where id = ? for update")) {
                ps.setLong(1, objectId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    Blob blob = rs.getBlob(1);
                    if (Boolean.getBoolean("oracle.stream")) {
                        try (OutputStream os = blob.setBinaryStream(1)) {
                            in.transferTo(os);
                        }
                    } else {
                        byte[] buf = new byte[windowSize];
                        long pos = 1;
                        int n;
                        while ((n = in.readNBytes(buf, 0, buf.length)) > 0) {
                            blob.setBytes(pos, buf, 0, n);
                            pos += n;
                        }
                    }
                    blob.free();
                }
            }
        }

        @Override
        InputStream open(ObjectRef ref) {
            if (holdConnection) {
                return openHeld(ref);
            }
            return new WindowedInputStream((offset, len) -> tx.executeRead(status -> {
                try (PreparedStatement ps = status.getConnection().prepareStatement("select content from mn_os_object where id = ?")) {
                    ps.setLong(1, ref.id());
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        Blob blob = rs.getBlob(1);
                        try {
                            if (Boolean.getBoolean("oracle.stream")) {
                                try (InputStream in = blob.getBinaryStream(offset + 1, len)) {
                                    return in.readNBytes(len);
                                }
                            }
                            return blob.getBytes(offset + 1, len);
                        } finally {
                            blob.free();
                        }
                    }
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }), ref.length(), windowSize, executor);
        }

        /** The naive alternative: one pooled connection held for the whole download. */
        private InputStream openHeld(ObjectRef ref) {
            try {
                Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement("select content from mn_os_object where id = ?");
                ps.setLong(1, ref.id());
                ResultSet rs = ps.executeQuery();
                rs.next();
                Blob blob = rs.getBlob(1);
                return new FilterInputStream(blob.getBinaryStream()) {
                    @Override
                    public void close() throws IOException {
                        super.close();
                        try {
                            blob.free();
                            rs.close();
                            ps.close();
                            c.close();
                        } catch (SQLException e) {
                            throw new IOException(e);
                        }
                    }
                };
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    static void createSchema(DataSource ds, Dialect dialect) throws SQLException {
        if (dialect == Dialect.POSTGRES) {
            try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
                s.execute("select lo_unlink(oid) from pg_largeobject_metadata");
            }
        }
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            if (dialect == Dialect.POSTGRES) {
                s.execute("drop table if exists mn_os_chunk");
                s.execute("drop table if exists mn_os_object");
                s.execute("create table mn_os_object (id bigserial primary key, bucket varchar(255) not null, "
                    + "object_key text collate \"C\" not null, content_length bigint, etag varchar(64), content_oid oid, "
                    + "unique (bucket, object_key))");
                s.execute("create table mn_os_chunk (object_id bigint not null references mn_os_object(id) on delete cascade, "
                    + "seq int not null, data bytea not null, primary key (object_id, seq))");
                s.execute("alter table mn_os_chunk alter column data set storage external");
            } else {
                s.execute("drop table if exists mn_os_chunk");
                s.execute("drop table if exists mn_os_object");
                s.execute("create table mn_os_object (id number generated by default as identity primary key, "
                    + "bucket varchar2(255) not null, object_key varchar2(1024) not null, content_length number, "
                    + "etag varchar2(64), content blob, constraint mn_os_object_uq unique (bucket, object_key)) "
                    + "lob (content) store as securefile")
                ;
                s.execute("create table mn_os_chunk (object_id number not null references mn_os_object(id) on delete cascade, "
                    + "seq number not null, data blob not null, primary key (object_id, seq)) lob (data) store as securefile");
            }
        }
    }

    static final class CountingInputStream extends FilterInputStream {
        long count;

        CountingInputStream(InputStream in) {
            super(in);
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b >= 0) {
                count++;
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = super.read(b, off, len);
            if (n > 0) {
                count += n;
            }
            return n;
        }
    }

    private Stores() { }
}
