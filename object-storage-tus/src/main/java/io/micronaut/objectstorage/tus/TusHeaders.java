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

/**
 * tus protocol constants.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.0.0
 */
@Internal
public final class TusHeaders {

    public static final String VERSION = "1.0.0";
    public static final String EXTENSIONS = "creation,termination";
    public static final String RESUMABLE = "Tus-Resumable";
    public static final String VERSION_HEADER = "Tus-Version";
    public static final String EXTENSION_HEADER = "Tus-Extension";
    public static final String OFFSET = "Upload-Offset";
    public static final String LENGTH = "Upload-Length";
    public static final String METADATA = "Upload-Metadata";

    private TusHeaders() {
    }
}
