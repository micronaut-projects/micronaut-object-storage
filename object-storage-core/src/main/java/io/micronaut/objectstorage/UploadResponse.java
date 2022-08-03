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
package io.micronaut.objectstorage;

/**
 * Object storage upload response.
 *
 * @author Pavol Gressa
 * @since 1.0
 */
public interface UploadResponse {

    /**
     * @return the entity tag of the object stored (an identifier for a specific version of the object).
     */
    String getETag();

    /**
     * Default implementation of {@link UploadResponse}.
     */
    class DefaultUploadResponse implements UploadResponse {

        private final String eTag;

        protected DefaultUploadResponse(String eTag) {
            this.eTag = eTag;
        }

        @Override
        public String getETag() {
            return eTag;
        }
    }

    /**
     * Builder for {@link UploadResponse}.
     */
    class Builder {

        private String eTag;

        public Builder withETag(String eTag) {
            this.eTag = eTag;
            return this;
        }

        public UploadResponse build() {
            return new DefaultUploadResponse(eTag);
        }

    }
}
