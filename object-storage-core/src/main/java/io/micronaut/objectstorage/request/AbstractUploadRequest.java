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
package io.micronaut.objectstorage.request;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

import java.util.Map;

/**
 * Base class for some {@link UploadRequest} implementations.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 1.1.0
 */
@Internal
public abstract class AbstractUploadRequest implements UploadRequest {

    @NonNull
    protected Map<String, String> metadata;

    @Nullable
    protected String contentType;

    @Override
    @NonNull
    public Map<String, String> getMetadata() {
        return this.metadata;
    }

    @Override
    public void setMetadata(@NonNull Map<String, String> metadata) {
        this.metadata = metadata;
    }

    @Override
    public void setContentType(@NonNull String contentType) {
        this.contentType = contentType;
    }

}
