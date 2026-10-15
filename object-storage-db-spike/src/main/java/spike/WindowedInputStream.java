package spike;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

/**
 * Reads an object as a sequence of fixed-size windows. Each window is fetched by a short,
 * independent database call, and the next window is prefetched while the current one is consumed.
 * At most two windows are held in memory and no connection is held between window fetches.
 */
final class WindowedInputStream extends InputStream {

    interface WindowFetcher {
        byte[] fetch(long offset, int length) throws Exception;
    }

    private final WindowFetcher fetcher;
    private final long totalLength;
    private final int windowSize;
    private final Executor executor;

    private byte[] current = new byte[0];
    private int pos;
    private long nextOffset;
    private CompletableFuture<byte[]> prefetch;
    private boolean closed;

    WindowedInputStream(WindowFetcher fetcher, long totalLength, int windowSize, Executor executor) {
        this.fetcher = fetcher;
        this.totalLength = totalLength;
        this.windowSize = windowSize;
        this.executor = executor;
        this.prefetch = schedule();
    }

    private CompletableFuture<byte[]> schedule() {
        if (nextOffset >= totalLength) {
            return null;
        }
        long offset = nextOffset;
        int len = (int) Math.min(windowSize, totalLength - offset);
        nextOffset += len;
        return CompletableFuture.supplyAsync(() -> {
            try {
                return fetcher.fetch(offset, len);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }

    private boolean advance() throws IOException {
        if (prefetch == null) {
            return false;
        }
        try {
            current = prefetch.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        } catch (ExecutionException e) {
            throw new IOException(e.getCause());
        }
        pos = 0;
        prefetch = schedule();
        return true;
    }

    @Override
    public int read() throws IOException {
        byte[] b = new byte[1];
        int n = read(b, 0, 1);
        return n < 0 ? -1 : b[0] & 0xff;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (closed) {
            throw new IOException("closed");
        }
        while (pos >= current.length) {
            if (!advance()) {
                return -1;
            }
        }
        int n = Math.min(len, current.length - pos);
        System.arraycopy(current, pos, b, off, n);
        pos += n;
        return n;
    }

    @Override
    public void close() {
        closed = true;
        if (prefetch != null) {
            prefetch.cancel(false);
        }
        current = new byte[0];
    }
}
