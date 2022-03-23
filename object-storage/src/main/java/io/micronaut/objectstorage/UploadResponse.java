package io.micronaut.objectstorage;

/**
 * @author Pavol Gressa
 * @since 2.5
 */
public interface UploadResponse {

    String getETag();

    class UploadResponseImpl implements UploadResponse {

        private final String eTag;

        protected UploadResponseImpl(String eTag) {
            this.eTag = eTag;
        }

        @Override
        public String getETag() {
            return eTag;
        }
    }

    class Builder {

        private String eTag;


        public Builder withETag(String eTag) {
            this.eTag = eTag;
            return this;
        }

        public UploadResponse build() {
            return new UploadResponseImpl(eTag);
        }

    }
}
