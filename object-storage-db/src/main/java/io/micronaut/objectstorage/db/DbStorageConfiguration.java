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
package io.micronaut.objectstorage.db;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.convert.format.ReadableBytes;
import io.micronaut.objectstorage.configuration.AbstractObjectStorageConfiguration;
import io.micronaut.objectstorage.configuration.EachPropertyContainsEntriesCondition;
import io.micronaut.objectstorage.configuration.ObjectStorageConfiguration;
import io.micronaut.transaction.TransactionDefinition.Propagation;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import static io.micronaut.objectstorage.db.DbStorageConfiguration.PREFIX;

/**
 * Configuration of a database-backed object storage.
 *
 * <p>Each named storage stores its objects in the tables of a Micronaut datasource. Several storages can
 * share one datasource and one set of tables; the {@link #getBucket() bucket} keeps their objects apart.</p>
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.2.0
 */
@EachProperty(PREFIX)
@Requires(condition = EachPropertyContainsEntriesCondition.class)
public class DbStorageConfiguration extends AbstractObjectStorageConfiguration {

    public static final String NAME = "db";

    public static final String PREFIX = ObjectStorageConfiguration.PREFIX + '.' + NAME;

    /**
     * Default size of chunk rows and read windows: 1 MiB.
     */
    public static final int DEFAULT_CHUNK_SIZE = 1024 * 1024;

    /**
     * Default name of the datasource.
     */
    public static final String DEFAULT_DATASOURCE = "default";

    private String datasource = DEFAULT_DATASOURCE;

    @Nullable
    private String bucket;

    private DbSchemaGenerate schemaGenerate = DbSchemaGenerate.NONE;

    @Nullable
    private DbContentLayout contentLayout;

    private int chunkSize = DEFAULT_CHUNK_SIZE;

    @Nullable
    private Long maxBufferedBytes;

    private Propagation transactionPropagation = Propagation.REQUIRED;

    @Nullable
    private Long maxObjectSize;

    private boolean validateMapping;

    private DbTableConfiguration table = new DbTableConfiguration();

    public DbStorageConfiguration(@Parameter String name) {
        super(name);
    }

    /**
     * Whether to enable or disable this object storage.
     */
    @Override
    public boolean isEnabled() {
        return this.enabled;
    }

    /**
     * @return The name of the Micronaut datasource that holds the objects.
     */
    @NonNull
    public String getDatasource() {
        return datasource;
    }

    /**
     * The name of the Micronaut datasource that holds the objects. Default value ({@value #DEFAULT_DATASOURCE}).
     *
     * @param datasource The datasource name.
     */
    public void setDatasource(@NonNull String datasource) {
        this.datasource = datasource;
    }

    /**
     * @return The logical bucket of this storage. Defaults to the storage name.
     */
    @NonNull
    public String getBucket() {
        return bucket != null ? bucket : name;
    }

    /**
     * The logical bucket of this storage. Storages sharing a datasource keep their objects apart by bucket.
     * Defaults to the storage name.
     *
     * @param bucket The bucket name.
     */
    public void setBucket(@Nullable String bucket) {
        this.bucket = bucket;
    }

    /**
     * @return Whether and how the built-in tables are created.
     */
    @NonNull
    public DbSchemaGenerate getSchemaGenerate() {
        return schemaGenerate;
    }

    /**
     * Whether and how the built-in tables are created on first use of the storage. Default value (NONE).
     *
     * @param schemaGenerate The schema generation mode.
     */
    public void setSchemaGenerate(@NonNull DbSchemaGenerate schemaGenerate) {
        this.schemaGenerate = schemaGenerate;
    }

    /**
     * @return The content layout, or {@code null} to use the default of the module that handles the dialect.
     */
    @Nullable
    public DbContentLayout getContentLayout() {
        return contentLayout;
    }

    /**
     * How the bytes of each object are stored. When not set, the generic module uses {@code CHUNKED}, the
     * PostgreSQL module uses {@code LARGE_OBJECT} and the Oracle module uses {@code SINGLE_COLUMN} (a SecureFile BLOB).
     *
     * @param contentLayout The content layout.
     */
    public void setContentLayout(@Nullable DbContentLayout contentLayout) {
        this.contentLayout = contentLayout;
    }

    /**
     * @return The size, in bytes, of chunk rows and read windows.
     */
    public int getChunkSize() {
        return chunkSize;
    }

    /**
     * The size of each chunk row in chunked layouts, and of each read window in every layout. Default value (1MB).
     *
     * @param chunkSize The chunk size, for example {@code 1MB} or {@code 256KB}.
     */
    public void setChunkSize(@ReadableBytes int chunkSize) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunk-size must be greater than 0");
        }
        this.chunkSize = chunkSize;
    }

    /**
     * @return The maximum number of bytes held in read windows across all downloads of this storage, or
     * {@code null} to use a quarter of the maximum heap size.
     */
    @Nullable
    public Long getMaxBufferedBytes() {
        return maxBufferedBytes;
    }

    /**
     * Cap on the bytes held in read windows across all concurrent downloads of this storage. Downloads wait for
     * budget instead of exhausting the heap. Defaults to a quarter of the maximum heap size.
     *
     * @param maxBufferedBytes The maximum number of buffered bytes, for example {@code 64MB}.
     */
    public void setMaxBufferedBytes(@Nullable @ReadableBytes Long maxBufferedBytes) {
        this.maxBufferedBytes = maxBufferedBytes;
    }

    /**
     * @return The transaction propagation used by write operations.
     */
    @NonNull
    public Propagation getTransactionPropagation() {
        return transactionPropagation;
    }

    /**
     * The transaction propagation used by write operations ({@code upload}, {@code delete}, {@code copy}, metadata
     * and bucket changes, and multipart operations). Default value (REQUIRED).
     *
     * @param transactionPropagation The propagation.
     */
    public void setTransactionPropagation(@NonNull Propagation transactionPropagation) {
        this.transactionPropagation = transactionPropagation;
    }

    /**
     * @return The maximum object size in bytes, or {@code null} for no limit.
     */
    @Nullable
    public Long getMaxObjectSize() {
        return maxObjectSize;
    }

    /**
     * Optional limit on the size of uploaded objects and multipart parts. Uploads above it fail.
     *
     * @param maxObjectSize The maximum size, for example {@code 100MB}.
     */
    public void setMaxObjectSize(@Nullable @ReadableBytes Long maxObjectSize) {
        this.maxObjectSize = maxObjectSize;
    }

    /**
     * @return Whether the table mapping is validated on first use.
     */
    public boolean isValidateMapping() {
        return validateMapping;
    }

    /**
     * Whether to validate the table mapping against the database metadata on first use of the storage. Intended
     * for development and tests. Default value (false).
     *
     * @param validateMapping Whether to validate the mapping.
     */
    public void setValidateMapping(boolean validateMapping) {
        this.validateMapping = validateMapping;
    }

    /**
     * @return The table mapping.
     */
    @NonNull
    public DbTableConfiguration getTable() {
        return table;
    }

    /**
     * @param table The table mapping.
     */
    public void setTable(@NonNull DbTableConfiguration table) {
        this.table = table;
    }

    /**
     * Maps a storage onto an existing table. When {@link #getName() name} is not set, the built-in tables are used.
     */
    @ConfigurationProperties("table")
    public static class DbTableConfiguration {

        @Nullable
        private String name;

        private DbColumnsConfiguration columns = new DbColumnsConfiguration();

        /**
         * @return The name of an existing table that holds the objects, or {@code null} for the built-in tables.
         */
        @Nullable
        public String getName() {
            return name;
        }

        /**
         * The name of an existing table that holds the objects, optionally qualified with a schema. Its DDL is owned by
         * the application: the module never creates or alters it.
         *
         * @param name The table name.
         */
        public void setName(@Nullable String name) {
            this.name = name;
        }

        /**
         * @return The column mapping.
         */
        @NonNull
        public DbColumnsConfiguration getColumns() {
            return columns;
        }

        /**
         * @param columns The column mapping.
         */
        public void setColumns(@NonNull DbColumnsConfiguration columns) {
            this.columns = columns;
        }

        /**
         * Column names of a mapped table. Only {@code key} and {@code content} are required; each optional column that
         * is not mapped turns off the matching feature.
         */
        @ConfigurationProperties("columns")
        public static class DbColumnsConfiguration {

            @Nullable
            private String key;
            @Nullable
            private String content;
            @Nullable
            private String contentLength;
            @Nullable
            private String contentType;
            @Nullable
            private String etag;
            @Nullable
            private String metadata;
            @Nullable
            private String bucket;
            @Nullable
            private String updatedAt;

            /**
             * @return The column holding the object key.
             */
            @Nullable
            public String getKey() {
                return key;
            }

            /**
             * The column holding the object key (required).
             *
             * @param key The column name.
             */
            public void setKey(@Nullable String key) {
                this.key = key;
            }

            /**
             * @return The column holding the object bytes.
             */
            @Nullable
            public String getContent() {
                return content;
            }

            /**
             * The column holding the object bytes, a BLOB, BYTEA or VARBINARY column (required).
             *
             * @param content The column name.
             */
            public void setContent(@Nullable String content) {
                this.content = content;
            }

            /**
             * @return The column holding the content length.
             */
            @Nullable
            public String getContentLength() {
                return contentLength;
            }

            /**
             * The column holding the content length. Without it, downloads read until the end of the content.
             *
             * @param contentLength The column name.
             */
            public void setContentLength(@Nullable String contentLength) {
                this.contentLength = contentLength;
            }

            /**
             * @return The column holding the content type.
             */
            @Nullable
            public String getContentType() {
                return contentType;
            }

            /**
             * The column holding the content type. Without it, content types are not persisted.
             *
             * @param contentType The column name.
             */
            public void setContentType(@Nullable String contentType) {
                this.contentType = contentType;
            }

            /**
             * @return The column holding the entity tag.
             */
            @Nullable
            public String getEtag() {
                return etag;
            }

            /**
             * The column holding the entity tag (MD5 of the content, in hexadecimal). Without it, entity tags are not
             * persisted and downloads cannot detect a concurrent overwrite.
             *
             * @param etag The column name.
             */
            public void setEtag(@Nullable String etag) {
                this.etag = etag;
            }

            /**
             * @return The column holding user metadata as JSON text.
             */
            @Nullable
            public String getMetadata() {
                return metadata;
            }

            /**
             * The column holding user metadata as JSON text. Without it, user metadata is not persisted.
             *
             * @param metadata The column name.
             */
            public void setMetadata(@Nullable String metadata) {
                this.metadata = metadata;
            }

            /**
             * @return The column holding the bucket.
             */
            @Nullable
            public String getBucket() {
                return bucket;
            }

            /**
             * The column holding the bucket. Without it, the table holds a single bucket.
             *
             * @param bucket The column name.
             */
            public void setBucket(@Nullable String bucket) {
                this.bucket = bucket;
            }

            /**
             * @return The column holding the last modification time.
             */
            @Nullable
            public String getUpdatedAt() {
                return updatedAt;
            }

            /**
             * The column holding the last modification time, a TIMESTAMP column written in UTC.
             *
             * @param updatedAt The column name.
             */
            public void setUpdatedAt(@Nullable String updatedAt) {
                this.updatedAt = updatedAt;
            }
        }
    }
}
