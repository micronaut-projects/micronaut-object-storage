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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Converts metadata maps to and from JSON objects of string values. Kept dependency free so that the module does
 * not require a JSON library.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.2.0
 */
@Internal
public final class JsonMaps {

    private JsonMaps() {
    }

    /**
     * @param map A map of strings.
     * @return The JSON object, with keys sorted.
     */
    @NonNull
    public static String write(@NonNull Map<String, String> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : new TreeMap<>(map).entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            quote(sb, entry.getKey());
            sb.append(':');
            quote(sb, entry.getValue());
        }
        return sb.append('}').toString();
    }

    /**
     * @param json A JSON object of string values, or {@code null}.
     * @return The map, empty for {@code null}.
     * @throws IllegalArgumentException If the text is not a JSON object of strings.
     */
    @NonNull
    public static Map<String, String> read(@Nullable String json) {
        Map<String, String> map = new LinkedHashMap<>();
        if (json == null || json.isBlank()) {
            return map;
        }
        Parser parser = new Parser(json);
        parser.skipWhitespace();
        parser.expect('{');
        parser.skipWhitespace();
        if (parser.peek() == '}') {
            parser.pos++;
            return map;
        }
        while (true) {
            parser.skipWhitespace();
            String key = parser.string();
            parser.skipWhitespace();
            parser.expect(':');
            parser.skipWhitespace();
            String value = parser.peek() == 'n' ? parser.nullLiteral() : parser.string();
            if (value != null) {
                map.put(key, value);
            }
            parser.skipWhitespace();
            char c = parser.next();
            if (c == '}') {
                return map;
            }
            if (c != ',') {
                throw parser.error("Expected ',' or '}'");
            }
        }
    }

    private static void quote(StringBuilder sb, String value) {
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    private static final class Parser {
        private final String text;
        private int pos;

        private Parser(String text) {
            this.text = text;
        }

        private char peek() {
            if (pos >= text.length()) {
                throw error("Unexpected end of JSON");
            }
            return text.charAt(pos);
        }

        private char next() {
            char c = peek();
            pos++;
            return c;
        }

        private void expect(char expected) {
            if (next() != expected) {
                throw error("Expected '" + expected + "'");
            }
        }

        private void skipWhitespace() {
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
                pos++;
            }
        }

        @Nullable
        private String nullLiteral() {
            if (!text.startsWith("null", pos)) {
                throw error("Expected a string");
            }
            pos += 4;
            return null;
        }

        private String string() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') {
                    return sb.toString();
                }
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                char e = next();
                switch (e) {
                    case '"', '\\', '/' -> sb.append(e);
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'u' -> {
                        if (pos + 4 > text.length()) {
                            throw error("Invalid unicode escape");
                        }
                        sb.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                        pos += 4;
                    }
                    default -> throw error("Invalid escape");
                }
            }
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at position " + pos + " of metadata JSON");
        }
    }
}
