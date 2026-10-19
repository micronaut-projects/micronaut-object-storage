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
import io.micronaut.objectstorage.ObjectStorageException;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads the shipped schema scripts and splits them into statements.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.2.0
 */
@Internal
public final class DbSchemaScripts {

    /**
     * Classpath directory of the plain schema scripts, one sub-directory per dialect.
     */
    public static final String SCRIPTS_LOCATION = "META-INF/micronaut-object-storage-db/";

    /**
     * Classpath directory of the Flyway migrations, one sub-directory per dialect.
     */
    public static final String FLYWAY_LOCATION = "db/micronaut-object-storage-db/";

    private DbSchemaScripts() {
    }

    /**
     * @param directory The dialect directory, such as {@code postgres}.
     * @return The statements of the schema script.
     */
    @NonNull
    public static List<String> load(@NonNull String directory) {
        String path = SCRIPTS_LOCATION + directory + "/schema.sql";
        ClassLoader classLoader = DbSchemaScripts.class.getClassLoader();
        try (InputStream in = classLoader.getResourceAsStream(path)) {
            if (in == null) {
                throw new ObjectStorageException("Schema script not found on the classpath: " + path);
            }
            return split(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new ObjectStorageException("Error reading schema script " + path, e);
        }
    }

    /**
     * Splits a script on semicolons outside quotes and comments, dropping comments and blank statements.
     *
     * @param script The script.
     * @return The statements.
     */
    @NonNull
    public static List<String> split(@NonNull String script) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int i = 0;
        int length = script.length();
        while (i < length) {
            char c = script.charAt(i);
            if (c == '-' && i + 1 < length && script.charAt(i + 1) == '-') {
                while (i < length && script.charAt(i) != '\n') {
                    i++;
                }
                continue;
            }
            if (c == '/' && i + 1 < length && script.charAt(i + 1) == '*') {
                int end = script.indexOf("*/", i + 2);
                i = end < 0 ? length : end + 2;
                continue;
            }
            if (c == '\'' || c == '"') {
                int end = i + 1;
                while (end < length) {
                    if (script.charAt(end) == c) {
                        if (end + 1 < length && script.charAt(end + 1) == c) {
                            end += 2;
                            continue;
                        }
                        break;
                    }
                    end++;
                }
                current.append(script, i, Math.min(end + 1, length));
                i = end + 1;
                continue;
            }
            if (c == ';') {
                addStatement(statements, current);
                current.setLength(0);
                i++;
                continue;
            }
            current.append(c);
            i++;
        }
        addStatement(statements, current);
        return statements;
    }

    private static void addStatement(List<String> statements, StringBuilder statement) {
        String trimmed = statement.toString().trim();
        if (!trimmed.isEmpty()) {
            statements.add(trimmed);
        }
    }
}
