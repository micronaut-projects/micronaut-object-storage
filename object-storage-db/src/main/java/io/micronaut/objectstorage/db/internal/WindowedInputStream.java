/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.objectstorage.db.internal;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.ref.Cleaner;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

/**
 * Reads an object as a sequence of windows. Each window is fetched by a short, independent database call, and the
 * next window is prefetched while the current one is consumed, when budget allows. At most two windows are held
 * in memory per stream and no connection is held between fetches.
 *
 * <p>Window memory is accounted in a {@link ByteBudget} shared by all downloads of a storage. A stream that is never
 * closed returns its budget when it is garbage collected.</p>
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.2.0
 */
@Internal
public final class WindowedInputStream extends InputStream {

    private static final Cleaner CLEANER = Cleaner.create();
    private static final byte[] EMPTY = new byte[0];

    private final WindowFetcher fetcher;
    private final long totalLength;
    private final int windowSize;
    private final @Nullable Executor prefetchExecutor;
    private final String description;
    private final State state;
    private final Cleaner.Cleanable cleanable;

    private byte[] current = EMPTY;
    private int pos;
    private long nextIndex;
    private long fetched;
    private boolean more;
    private boolean closed;

    /**
     * @param fetcher Fetches windows by index.
     * @param totalLength The content length, or {@code -1} if unknown, in which case a short window ends the stream.
     * @param windowSize The window size.
     * @param budget The memory budget shared by the downloads of the storage.
     * @param prefetchExecutor The executor fetching the next window ahead, or {@code null} to fetch on the reading thread
     *                         only.
     * @param description What is being read, for error messages.
     */
    public WindowedInputStream(@NonNull WindowFetcher fetcher,
                               long totalLength,
                               int windowSize,
                               @NonNull ByteBudget budget,
                               @Nullable Executor prefetchExecutor,
                               @NonNull String description) {
        this.fetcher = fetcher;
        this.totalLength = totalLength;
        this.windowSize = windowSize;
        this.prefetchExecutor = prefetchExecutor;
        this.description = description;
        this.state = new State(budget);
        this.cleanable = CLEANER.register(this, state);
        this.more = totalLength != 0;
        schedulePrefetch();
    }

    @Override
    public int read() throws IOException {
        byte[] b = new byte[1];
        int n = read(b, 0, 1);
        return n < 0 ? -1 : b[0] & 0xff;
    }

    @Override
    public int read(byte @NonNull [] b, int off, int len) throws IOException {
        if (closed) {
            throw new IOException("Stream closed: " + description);
        }
        if (len == 0) {
            return 0;
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
    public int available() {
        return closed ? 0 : current.length - pos;
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            current = EMPTY;
            cleanable.clean();
        }
    }

    private boolean advance() throws IOException {
        state.releaseCurrent();
        current = EMPTY;
        pos = 0;
        if (state.prefetch != null) {
            CompletableFuture<byte[]> prefetch = state.prefetch;
            state.currentPermits = state.prefetchPermits;
            state.prefetch = null;
            state.prefetchPermits = 0;
            current = await(prefetch);
        } else if (more) {
            try {
                state.currentPermits = state.budget.acquire(windowSize);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for read buffer budget: " + description, e);
            }
            current = fetch(nextIndex++);
        } else {
            return false;
        }
        fetched += current.length;
        if (current.length == 0) {
            more = false;
        } else if (totalLength >= 0) {
            more = fetched < totalLength;
        } else {
            more = current.length >= windowSize;
        }
        schedulePrefetch();
        return current.length > 0;
    }

    private void schedulePrefetch() {
        if (!more || prefetchExecutor == null || state.prefetch != null) {
            return;
        }
        int permits = state.budget.tryAcquire(windowSize);
        if (permits == 0) {
            return;
        }
        long index = nextIndex++;
        state.prefetchPermits = permits;
        try {
            state.prefetch = CompletableFuture.supplyAsync(() -> {
                try {
                    return fetch(index);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }, prefetchExecutor);
        } catch (RuntimeException e) {
            // executor rejected the task: read this window on the reading thread instead
            nextIndex = index;
            state.budget.release(permits);
            state.prefetchPermits = 0;
            state.prefetch = null;
        }
    }

    private byte[] fetch(long index) throws IOException {
        byte[] window = fetcher.fetch(index);
        if (window == null) {
            throw new IOException("The object changed or was deleted while it was being read: " + description);
        }
        return window;
    }

    private byte[] await(CompletableFuture<byte[]> future) throws IOException {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(false);
            throw new IOException("Interrupted while reading " + description, e);
        } catch (CancellationException e) {
            throw new IOException("Read cancelled: " + description, e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() instanceof CompletionException ce && ce.getCause() != null ? ce.getCause() : e.getCause();
            if (cause instanceof UncheckedIOException uio) {
                throw uio.getCause();
            }
            if (cause instanceof IOException io) {
                throw io;
            }
            throw new IOException("Error reading " + description + ": " + cause.getMessage(), cause);
        }
    }

    /**
     * Fetches one window.
     */
    @FunctionalInterface
    public interface WindowFetcher {

        /**
         * @param index The 0-based window index.
         * @return The window, empty past the end, or {@code null} if the object version no longer exists.
         * @throws IOException If reading fails.
         */
        byte @Nullable [] fetch(long index) throws IOException;
    }

    /**
     * The budget held by a stream, released on close or when the stream is garbage collected.
     */
    private static final class State implements Runnable {

        private final ByteBudget budget;
        private int currentPermits;
        private int prefetchPermits;
        private @Nullable CompletableFuture<byte[]> prefetch;

        private State(ByteBudget budget) {
            this.budget = budget;
        }

        private void releaseCurrent() {
            budget.release(currentPermits);
            currentPermits = 0;
        }

        @Override
        public void run() {
            releaseCurrent();
            CompletableFuture<byte[]> pending = prefetch;
            int pendingPermits = prefetchPermits;
            prefetch = null;
            prefetchPermits = 0;
            if (pending != null) {
                pending.cancel(false);
                pending.whenComplete((bytes, error) -> budget.release(pendingPermits));
            }
        }
    }
}
