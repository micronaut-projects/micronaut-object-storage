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
package io.micronaut.objectstorage.local;

import java.nio.file.Path;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

final class LocalStorageLocks {

    private static final int LOCK_STRIPES = 256;
    private static final ReentrantLock[] OBJECT_MUTATION_LOCKS = createMutationLocks();
    private static final ReentrantReadWriteLock[] BUCKET_LOCKS = createBucketLocks();

    private LocalStorageLocks() {
    }

    static ReentrantLock objectMutationLock(Path file) {
        return objectMutationLock(objectMutationLockIndex(file));
    }

    static ReentrantLock objectMutationLock(int index) {
        return OBJECT_MUTATION_LOCKS[index];
    }

    static int objectMutationLockIndex(Path file) {
        return lockIndex(file);
    }

    static Lock bucketReadLock(Path bucketPath) {
        return bucketLock(bucketPath).readLock();
    }

    static Lock bucketWriteLock(Path bucketPath) {
        return bucketLock(bucketPath).writeLock();
    }

    private static ReentrantReadWriteLock bucketLock(Path bucketPath) {
        return BUCKET_LOCKS[lockIndex(bucketPath)];
    }

    private static ReentrantLock[] createMutationLocks() {
        ReentrantLock[] locks = new ReentrantLock[LOCK_STRIPES];
        for (int i = 0; i < locks.length; i++) {
            locks[i] = new ReentrantLock();
        }
        return locks;
    }

    private static ReentrantReadWriteLock[] createBucketLocks() {
        ReentrantReadWriteLock[] locks = new ReentrantReadWriteLock[LOCK_STRIPES];
        for (int i = 0; i < locks.length; i++) {
            locks[i] = new ReentrantReadWriteLock(true);
        }
        return locks;
    }

    private static int lockIndex(Path path) {
        return Math.floorMod(path.toAbsolutePath().normalize().hashCode(), LOCK_STRIPES);
    }
}
