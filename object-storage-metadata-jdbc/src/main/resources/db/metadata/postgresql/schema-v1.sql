CREATE TABLE storage_container_metadata (
    id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(255) NOT NULL,
    storage_name VARCHAR(255) NOT NULL,
    provider_id VARCHAR(255) NOT NULL,
    logical_container VARCHAR(255) NOT NULL,
    provider_namespace VARCHAR(255),
    provider_container VARCHAR(1024) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_storage_container_metadata PRIMARY KEY (id),
    CONSTRAINT uk_storage_container_metadata_scope UNIQUE (tenant_id, storage_name, logical_container)
);

CREATE TABLE storage_container_metadata_attr (
    container_metadata_id VARCHAR(64) NOT NULL,
    attr_key VARCHAR(255) NOT NULL,
    attr_value VARCHAR(4000) NOT NULL,
    CONSTRAINT pk_storage_container_metadata_attr PRIMARY KEY (container_metadata_id, attr_key),
    CONSTRAINT fk_storage_container_metadata_attr_parent FOREIGN KEY (container_metadata_id)
        REFERENCES storage_container_metadata (id) ON DELETE CASCADE
);

CREATE TABLE storage_object_metadata (
    id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(255) NOT NULL,
    storage_name VARCHAR(255) NOT NULL,
    provider_id VARCHAR(255) NOT NULL,
    logical_container VARCHAR(255) NOT NULL,
    provider_namespace VARCHAR(255),
    provider_container VARCHAR(1024) NOT NULL,
    object_key VARCHAR(1024) NOT NULL,
    object_key_hash VARCHAR(64) NOT NULL,
    content_type VARCHAR(255),
    content_length BIGINT,
    etag VARCHAR(512),
    provider_version_id VARCHAR(512),
    checksum VARCHAR(512),
    reconciliation_state VARCHAR(32) NOT NULL,
    last_error_summary VARCHAR(2000),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_storage_object_metadata PRIMARY KEY (id),
    CONSTRAINT uk_storage_object_metadata_scope UNIQUE (tenant_id, storage_name, logical_container, object_key_hash)
);

CREATE TABLE storage_object_metadata_attr (
    object_metadata_id VARCHAR(64) NOT NULL,
    attr_key VARCHAR(255) NOT NULL,
    attr_value VARCHAR(4000) NOT NULL,
    CONSTRAINT pk_storage_object_metadata_attr PRIMARY KEY (object_metadata_id, attr_key),
    CONSTRAINT fk_storage_object_metadata_attr_parent FOREIGN KEY (object_metadata_id)
        REFERENCES storage_object_metadata (id) ON DELETE CASCADE
);

CREATE TABLE storage_metadata_outbox (
    id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(255) NOT NULL,
    storage_name VARCHAR(255) NOT NULL,
    logical_container VARCHAR(255) NOT NULL,
    object_key VARCHAR(1024),
    object_key_hash VARCHAR(64),
    destination_object_key VARCHAR(1024),
    destination_object_key_hash VARCHAR(64),
    operation_type VARCHAR(32) NOT NULL,
    reconciliation_id VARCHAR(128),
    payload_version INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempt_count INT NOT NULL,
    next_attempt_at TIMESTAMP NOT NULL,
    last_error_summary VARCHAR(2000),
    idempotency_key VARCHAR(512) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_storage_metadata_outbox PRIMARY KEY (id),
    CONSTRAINT uk_storage_metadata_outbox_idempotency UNIQUE (idempotency_key)
);

CREATE INDEX idx_scoped_container_metadata
    ON storage_container_metadata (tenant_id, storage_name, logical_container);

CREATE INDEX idx_scoped_object_metadata
    ON storage_object_metadata (tenant_id, storage_name, logical_container, object_key_hash);
