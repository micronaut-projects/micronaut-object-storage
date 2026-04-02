/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.objectstorage.response;

import org.jspecify.annotations.NonNull;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A complete pre-signed HTTP request that can upload a single object.
 *
 * @since 3.1.0
 */
public final class PresignedUpload {

    private final URI uri;
    private final String method;
    private final Map<String, List<String>> headers;
    private final Instant expiration;

    /**
     * @param uri the signed target URI
     * @param method the HTTP method the caller must use
     * @param headers the required request headers
     * @param expiration the point in time when this request expires
     */
    public PresignedUpload(@NonNull URI uri,
                           @NonNull String method,
                           @NonNull Map<String, List<String>> headers,
                           @NonNull Instant expiration) {
        this.uri = Objects.requireNonNull(uri, "uri");
        this.method = Objects.requireNonNull(method, "method");
        this.headers = copyHeaders(headers);
        this.expiration = Objects.requireNonNull(expiration, "expiration");
    }

    /**
     * @return the signed target URI
     */
    @NonNull
    public URI getUri() {
        return uri;
    }

    /**
     * @return the HTTP method the caller must use
     */
    @NonNull
    public String getMethod() {
        return method;
    }

    /**
     * @return the required request headers
     */
    @NonNull
    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    /**
     * @return when the signed request expires
     */
    @NonNull
    public Instant getExpiration() {
        return expiration;
    }

    @NonNull
    private static Map<String, List<String>> copyHeaders(@NonNull Map<String, List<String>> headers) {
        Objects.requireNonNull(headers, "headers");
        Map<String, List<String>> copy = new LinkedHashMap<>(headers.size());
        headers.forEach((name, values) -> copy.put(name, List.copyOf(values)));
        return Map.copyOf(copy);
    }
}
