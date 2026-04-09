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
package io.micronaut.objectstorage.tus;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.NonNull;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Encodes and decodes tus metadata headers.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.0.0
 */
@Internal
public final class TusMetadataCodec {

    private TusMetadataCodec() {
    }

    @NonNull
    public static Map<String, String> decode(@NonNull String headerValue) {
        Map<String, String> metadata = new LinkedHashMap<>();
        if (headerValue.isBlank()) {
            return metadata;
        }
        for (String entry : headerValue.split(",")) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] parts = trimmed.split(" ", 2);
            if (parts[0].isBlank()) {
                throw new IllegalArgumentException("Invalid Upload-Metadata entry: " + trimmed);
            }
            if (parts.length == 1 || parts[1].isEmpty()) {
                metadata.put(parts[0], "");
                continue;
            }
            byte[] decoded = Base64.getDecoder().decode(parts[1]);
            metadata.put(parts[0], new String(decoded, StandardCharsets.UTF_8));
        }
        return metadata;
    }

    @NonNull
    public static String encode(@NonNull Map<String, String> metadata) {
        StringJoiner joiner = new StringJoiner(",");
        metadata.forEach((key, value) -> {
            String encodedValue = Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
            joiner.add(key + " " + encodedValue);
        });
        return joiner.toString();
    }
}
