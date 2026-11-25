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

import java.util.concurrent.Semaphore;

/**
 * Caps the bytes held in read windows across all downloads of a storage. A download takes budget for its current
 * window, waiting if needed, and only prefetches when budget is free, so a download never waits while holding a
 * prefetched window.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.2.0
 */
@Internal
public final class ByteBudget {

    private final Semaphore permits;
    private final int capacity;

    /**
     * @param maxBytes The maximum number of bytes held at once.
     */
    public ByteBudget(long maxBytes) {
        this.capacity = (int) Math.max(1, Math.min(Integer.MAX_VALUE, maxBytes));
        this.permits = new Semaphore(capacity, true);
    }

    /**
     * Takes budget for a window, waiting until it is available.
     *
     * @param bytes The window size.
     * @return The amount taken, to pass to {@link #release(int)}.
     * @throws InterruptedException If interrupted while waiting.
     */
    public int acquire(int bytes) throws InterruptedException {
        int amount = clamp(bytes);
        permits.acquire(amount);
        return amount;
    }

    /**
     * Takes budget for a window if it is available now.
     *
     * @param bytes The window size.
     * @return The amount taken, or {@code 0} if none was available.
     */
    public int tryAcquire(int bytes) {
        int amount = clamp(bytes);
        return permits.tryAcquire(amount) ? amount : 0;
    }

    /**
     * Returns budget.
     *
     * @param amount The amount returned by {@link #acquire(int)} or {@link #tryAcquire(int)}.
     */
    public void release(int amount) {
        if (amount > 0) {
            permits.release(amount);
        }
    }

    /**
     * @return The budget available now, in bytes.
     */
    public int available() {
        return permits.availablePermits();
    }

    /**
     * @return The total budget, in bytes.
     */
    public int capacity() {
        return capacity;
    }

    private int clamp(int bytes) {
        return Math.max(1, Math.min(bytes, capacity));
    }
}
