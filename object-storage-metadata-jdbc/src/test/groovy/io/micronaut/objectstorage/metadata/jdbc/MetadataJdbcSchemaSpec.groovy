package io.micronaut.objectstorage.metadata.jdbc

import spock.lang.Specification
import spock.lang.Unroll

class MetadataJdbcSchemaSpec extends Specification {

    @Unroll
    void "schema script #path defines v1 tables constraints and indexes"() {
        given:
        String ddl = load(path).toLowerCase(java.util.Locale.ROOT)

        expect:
        ddl.contains("create table storage_container_metadata")
        ddl.contains("create table storage_container_metadata_attr")
        ddl.contains("create table storage_object_metadata")
        ddl.contains("create table storage_object_metadata_attr")
        ddl.contains("create table storage_metadata_outbox")

        and:
        ddl.contains("tenant_id")
        ddl.contains("storage_name")
        ddl.contains("logical_container")
        ddl.contains("object_key_hash")

        and:
        ddl.contains("tenant_id varchar2(255) not null") || ddl.contains("tenant_id varchar(255) not null")
        ddl.contains("storage_name varchar2(255) not null") || ddl.contains("storage_name varchar(255) not null")
        ddl.contains("logical_container varchar2(255) not null") || ddl.contains("logical_container varchar(255) not null")
        ddl.contains("object_key_hash varchar2(64) not null") || ddl.contains("object_key_hash varchar(64) not null")

        and:
        ddl.contains("constraint uk_storage_container_metadata_scope unique (tenant_id, storage_name, logical_container)")
        ddl.contains("constraint uk_storage_object_metadata_scope unique (tenant_id, storage_name, logical_container, object_key_hash)")
        if (path.contains('postgresql')) {
            assert ddl.contains("create index idx_scoped_container_metadata")
            assert ddl.contains("create index idx_scoped_object_metadata")
        }

        and:
        !ddl.contains(" json")
        !ddl.contains("json_")

        where:
        path << [
            "db/metadata/oracle/schema-v1.sql",
            "db/metadata/postgresql/schema-v1.sql"
        ]
    }

    private static String load(String path) {
        InputStream stream = MetadataJdbcSchemaSpec.classLoader.getResourceAsStream(path)
        assert stream != null: "Missing schema resource: $path"
        stream.getText("UTF-8")
    }
}
