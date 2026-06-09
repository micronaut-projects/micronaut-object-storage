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

import org.jspecify.annotations.NonNull;

import java.nio.file.Path;

/**
 * Native local multipart upload state.
 *
 * @param path The directory where local storage stores the multipart upload session state
 * @since 3.1.0
 */
public record LocalStorageMultipartUpload(@NonNull Path path) {
}
