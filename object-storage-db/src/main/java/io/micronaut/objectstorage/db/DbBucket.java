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
package io.micronaut.objectstorage.db;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * A bucket row, exposed as the native type of database bucket operations.
 *
 * @param name The bucket name.
 * @param createdAt The creation time, if known.
 * @author Álvaro Sánchez-Mariscal
 * @since 3.2.0
 */
public record DbBucket(@NonNull String name, @Nullable Instant createdAt) {
}
