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
package io.micronaut.objectstorage.s3compat;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpStatus;
import org.jspecify.annotations.NonNull;

/**
 * Internal exception that carries an S3-compatible HTTP status and error code.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.0.0
 */
@Internal
final class S3CompatibilityException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    S3CompatibilityException(@NonNull HttpStatus status, @NonNull String code, @NonNull String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    @NonNull
    HttpStatus getStatus() {
        return status;
    }

    @NonNull
    String getCode() {
        return code;
    }
}
