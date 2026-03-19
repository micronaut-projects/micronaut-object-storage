CREATE TABLE storage_container_metadata (
    id VARCHAR2(64) NOT NULL,
    tenant_id VARCHAR2(255) NOT NULL,
    storage_name VARCHAR2(255) NOT NULL,
    provider_id VARCHAR2(255) NOT NULL,
    logical_container VARCHAR2(255) NOT NULL,
    provider_namespace VARCHAR2(255),
    provider_container VARCHAR2(1024) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_storage_container_metadata PRIMARY KEY (id),
    CONSTRAINT uk_storage_container_metadata_scope UNIQUE (tenant_id, storage_name, logical_container)
);

CREATE TABLE storage_container_metadata_attr (
    container_metadata_id VARCHAR2(64) NOT NULL,
    attr_key VARCHAR2(255) NOT NULL,
    attr_value VARCHAR2(4000) NOT NULL,
    CONSTRAINT pk_storage_container_metadata_attr PRIMARY KEY (container_metadata_id, attr_key),
    CONSTRAINT fk_storage_container_metadata_attr_parent FOREIGN KEY (container_metadata_id)
        REFERENCES storage_container_metadata (id) ON DELETE CASCADE
);

CREATE TABLE storage_object_metadata (
    id VARCHAR2(64) NOT NULL,
    tenant_id VARCHAR2(255) NOT NULL,
    storage_name VARCHAR2(255) NOT NULL,
    provider_id VARCHAR2(255) NOT NULL,
    logical_container VARCHAR2(255) NOT NULL,
    provider_namespace VARCHAR2(255),
    provider_container VARCHAR2(1024) NOT NULL,
    object_key VARCHAR2(1024) NOT NULL,
    object_key_hash VARCHAR2(64) NOT NULL,
    content_type VARCHAR2(255),
    content_length NUMBER(19),
    etag VARCHAR2(512),
    provider_version_id VARCHAR2(512),
    checksum VARCHAR2(512),
    reconciliation_state VARCHAR2(32) NOT NULL,
    last_error_summary VARCHAR2(2000),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_storage_object_metadata PRIMARY KEY (id),
    CONSTRAINT uk_storage_object_metadata_scope UNIQUE (tenant_id, storage_name, logical_container, object_key_hash)
);

CREATE TABLE storage_object_metadata_attr (
    object_metadata_id VARCHAR2(64) NOT NULL,
    attr_key VARCHAR2(255) NOT NULL,
    attr_value VARCHAR2(4000) NOT NULL,
    CONSTRAINT pk_storage_object_metadata_attr PRIMARY KEY (object_metadata_id, attr_key),
    CONSTRAINT fk_storage_object_metadata_attr_parent FOREIGN KEY (object_metadata_id)
        REFERENCES storage_object_metadata (id) ON DELETE CASCADE
);

CREATE TABLE storage_metadata_outbox (
    id VARCHAR2(64) NOT NULL,
    tenant_id VARCHAR2(255) NOT NULL,
    storage_name VARCHAR2(255) NOT NULL,
    logical_container VARCHAR2(255) NOT NULL,
    object_key VARCHAR2(1024),
    object_key_hash VARCHAR2(64),
    destination_object_key VARCHAR2(1024),
    destination_object_key_hash VARCHAR2(64),
    operation_type VARCHAR2(32) NOT NULL,
    reconciliation_id VARCHAR2(128),
    payload_version NUMBER(10) NOT NULL,
    status VARCHAR2(32) NOT NULL,
    attempt_count NUMBER(10) NOT NULL,
    next_attempt_at TIMESTAMP NOT NULL,
    last_error_summary VARCHAR2(2000),
    idempotency_key VARCHAR2(512) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_storage_metadata_outbox PRIMARY KEY (id),
    CONSTRAINT uk_storage_metadata_outbox_idempotency UNIQUE (idempotency_key)
);

