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
package io.micronaut.objectstorage.supabase;

import io.micronaut.http.HttpStatus;
import io.micronaut.objectstorage.ObjectStorageException;
import io.micronaut.supabase.storage.StorageErrorSchema;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * An {@link ObjectStorageException} raised when the Supabase Storage API rejects a request.
 *
 * <p>Supabase Storage answers most rejected requests with HTTP {@code 400}; the real cause is in the body,
 * exposed here as {@link #getError()}, whose {@link StorageErrorSchema#code() code} is stable and is what
 * callers should branch on.</p>
 *
 * @author Sergio del Amo
 * @since 3.2.0
 */
public class SupabaseStorageException extends ObjectStorageException {

    /**
     * Error code of a request for an object that does not exist.
     */
    public static final String NO_SUCH_KEY = "NoSuchKey";

    /**
     * Error code of a request for a bucket that does not exist.
     */
    public static final String NO_SUCH_BUCKET = "NoSuchBucket";

    /**
     * Error code of an upload or copy whose destination already exists.
     */
    public static final String KEY_ALREADY_EXISTS = "KeyAlreadyExists";

    private final HttpStatus status;
    @Nullable
    private final transient StorageErrorSchema error;

    /**
     * @param message Description of the failed operation
     * @param status HTTP status of the response
     * @param error Error body, if the response carried one
     * @param cause Underlying client exception, if any
     */
    public SupabaseStorageException(@NonNull String message,
                                    @NonNull HttpStatus status,
                                    @Nullable StorageErrorSchema error,
                                    @Nullable Throwable cause) {
        super(describe(message, status, error), cause);
        this.status = status;
        this.error = error;
    }

    /**
     * @return HTTP status of the response
     */
    @NonNull
    public HttpStatus getStatus() {
        return status;
    }

    /**
     * @return Error body of the response, if it carried one
     */
    @NonNull
    public Optional<StorageErrorSchema> getError() {
        return Optional.ofNullable(error);
    }

    /**
     * @return The stable Supabase Storage error code, such as {@link #NO_SUCH_KEY}, if the response carried one
     */
    @NonNull
    public Optional<String> getCode() {
        return getError().map(StorageErrorSchema::code);
    }

    /**
     * @param code A Supabase Storage error code
     * @return Whether the response carried the given error code
     */
    public boolean hasCode(@NonNull String code) {
        return getCode().filter(code::equals).isPresent();
    }

    /**
     * @return Whether the response says the object or bucket does not exist
     */
    public boolean isNotFound() {
        return status == HttpStatus.NOT_FOUND || hasCode(NO_SUCH_KEY) || hasCode(NO_SUCH_BUCKET);
    }

    private static String describe(String message, HttpStatus status, @Nullable StorageErrorSchema error) {
        if (error == null) {
            return message + ": HTTP " + status.getCode();
        }
        return message + ": " + error.code() + " (" + error.message() + ")";
    }
}
