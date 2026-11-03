-- Micronaut Object Storage database storage: built-in tables for PostgreSQL 15+.
-- Keys use the "C" collation so that listing order is byte order. Content is stored either as Large Objects
-- (CONTENT_OID, with the PostgreSQL module) or as chunk rows (MN_OS_CHUNK). A trigger unlinks Large Objects when
-- their row is deleted or changed, so none is orphaned even when rows are deleted outside the library.

CREATE TABLE mn_os_bucket (
    bucket_name VARCHAR(255) NOT NULL PRIMARY KEY,
    metadata JSONB,
    attributes JSONB,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE mn_os_object (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    bucket VARCHAR(255) COLLATE "C" NOT NULL,
    object_key VARCHAR(1024) COLLATE "C" NOT NULL,
    content_type VARCHAR(255),
    content_length BIGINT,
    etag VARCHAR(64),
    metadata JSONB,
    attributes JSONB,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    content BYTEA,
    content_oid OID,
    CONSTRAINT mn_os_object_key_uq UNIQUE (bucket, object_key)
);

CREATE INDEX mn_os_object_metadata_idx ON mn_os_object USING GIN (metadata);

CREATE TABLE mn_os_chunk (
    content_id VARCHAR(36) NOT NULL,
    seq BIGINT NOT NULL,
    data BYTEA NOT NULL,
    CONSTRAINT mn_os_chunk_pk PRIMARY KEY (content_id, seq)
);

ALTER TABLE mn_os_chunk ALTER COLUMN data SET STORAGE EXTERNAL;

CREATE TABLE mn_os_upload (
    upload_id VARCHAR(36) NOT NULL PRIMARY KEY,
    bucket VARCHAR(255) NOT NULL,
    object_key VARCHAR(1024) NOT NULL,
    content_type VARCHAR(255),
    metadata JSONB,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX mn_os_upload_bucket_idx ON mn_os_upload (bucket);

CREATE TABLE mn_os_upload_part (
    upload_id VARCHAR(36) NOT NULL,
    part_number INT NOT NULL,
    content_id VARCHAR(36) NOT NULL,
    etag VARCHAR(64),
    part_size BIGINT,
    created_at TIMESTAMP NOT NULL,
    content BYTEA,
    content_oid OID,
    CONSTRAINT mn_os_upload_part_pk PRIMARY KEY (upload_id, part_number)
);

CREATE OR REPLACE FUNCTION mn_os_unlink_content() RETURNS trigger LANGUAGE plpgsql AS '
BEGIN
    IF OLD.content_oid IS NOT NULL AND (TG_OP = ''DELETE'' OR OLD.content_oid IS DISTINCT FROM NEW.content_oid) THEN
        PERFORM lo_unlink(OLD.content_oid);
    END IF;
    RETURN NULL;
END
';

CREATE TRIGGER mn_os_object_unlink AFTER UPDATE OF content_oid OR DELETE ON mn_os_object
    FOR EACH ROW EXECUTE FUNCTION mn_os_unlink_content();

CREATE TRIGGER mn_os_upload_part_unlink AFTER UPDATE OF content_oid OR DELETE ON mn_os_upload_part
    FOR EACH ROW EXECUTE FUNCTION mn_os_unlink_content();
