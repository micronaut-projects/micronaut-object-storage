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
package io.micronaut.objectstorage.bucket;

import org.jspecify.annotations.NonNull;

/**
 * Portable bucket/container lookup result.
 *
 * @param name The logical bucket/container name.
 * @param nativeEntry The provider-native bucket/container representation.
 * @param <T> The provider-native bucket/container representation type.
 * @author Álvaro Sánchez-Mariscal
 * @since 3.0.0
 */
public record BucketEntry<T>(
    @NonNull String name,
    @NonNull T nativeEntry
) {
}
