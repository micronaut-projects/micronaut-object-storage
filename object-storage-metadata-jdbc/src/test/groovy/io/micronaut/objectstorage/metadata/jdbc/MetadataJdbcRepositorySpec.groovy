package io.micronaut.objectstorage.metadata.jdbc

import io.micronaut.objectstorage.metadata.ObjectMetadataReconciliationState
import io.micronaut.objectstorage.metadata.ObjectMetadataSyncStatus
import io.micronaut.objectstorage.metadata.StorageContainerMetadata
import io.micronaut.objectstorage.metadata.StorageDescriptor
import io.micronaut.objectstorage.metadata.StorageObjectMetadata
import io.micronaut.objectstorage.metadata.jdbc.repository.JdbcStorageContainerMetadataRepository
import io.micronaut.objectstorage.metadata.jdbc.repository.JdbcStorageObjectMetadataRepository
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant

class MetadataJdbcRepositorySpec extends Specification {

    void "findByTenantAndObjectHash applies tenant predicate and hydrates object attrs"() {
        given:
        DataSource dataSource = Mock()
        Connection connection = Mock()
        PreparedStatement objectStatement = Mock()
        PreparedStatement attrStatement = Mock()
        ResultSet objectResultSet = Mock()
        ResultSet attrResultSet = Mock()
        JdbcStorageObjectMetadataRepository repository = new JdbcStorageObjectMetadataRepository(dataSource)
        StorageDescriptor descriptor = new StorageDescriptor("pictures", "oracle", "avatars", "ns-a", "bucket-a")
        Instant now = Instant.parse("2026-03-17T12:00:00Z")

        and:
        dataSource.getConnection() >> connection
        connection.getAutoCommit() >> true
        connection.prepareStatement({ String sql -> sql == JdbcStorageObjectMetadataRepository.FIND_SQL }) >> objectStatement
        connection.prepareStatement({ String sql -> sql == JdbcStorageObjectMetadataRepository.FIND_ATTRS_SQL }) >> attrStatement
        objectStatement.executeQuery() >> objectResultSet
        attrStatement.executeQuery() >> attrResultSet

        and:
        objectResultSet.next() >>> [true, false]
        objectResultSet.getString("id") >> "obj-1"
        objectResultSet.getString("tenant_id") >> "tenant-a"
        objectResultSet.getString("storage_name") >> "pictures"
        objectResultSet.getString("provider_id") >> "oracle"
        objectResultSet.getString("logical_container") >> "avatars"
        objectResultSet.getString("provider_namespace") >> "ns-a"
        objectResultSet.getString("provider_container") >> "bucket-a"
        objectResultSet.getString("object_key") >> "/avatars/a.png"
        objectResultSet.getString("object_key_hash") >> "hash-a"
        objectResultSet.getString("content_type") >> "image/png"
        objectResultSet.getLong("content_length") >> 321L
        objectResultSet.wasNull() >> false
        objectResultSet.getString("etag") >> "etag-a"
        objectResultSet.getString("provider_version_id") >> "v-1"
        objectResultSet.getString("checksum") >> "sha256:a"
        objectResultSet.getString("reconciliation_state") >> "SUCCEEDED"
        objectResultSet.getString("last_error_summary") >> null
        objectResultSet.getTimestamp("created_at") >> Timestamp.from(now)
        objectResultSet.getTimestamp("updated_at") >> Timestamp.from(now)

        and:
        attrResultSet.next() >>> [true, true, false]
        attrResultSet.getString("attr_key") >>> ["author", "source"]
        attrResultSet.getString("attr_value") >>> ["alice", "camera"]

        when:
        def result = repository.findByTenantAndObjectHash("tenant-a", descriptor, "hash-a")

        then:
        result.present
        result.get().metadata.tenantId == "tenant-a"
        result.get().metadata.objectKeyHash == "hash-a"
        result.get().attributes == [author: "alice", source: "camera"]

        and:
        1 * objectStatement.setString(1, "tenant-a")
        1 * objectStatement.setString(2, "pictures")
        1 * objectStatement.setString(3, "avatars")
        1 * objectStatement.setString(4, "hash-a")
        1 * attrStatement.setString(1, "obj-1")
        1 * connection.commit()
    }

    void "upsert object metadata scopes existence lookup by tenant and rewrites attrs"() {
        given:
        DataSource dataSource = Mock()
        Connection connection = Mock()
        PreparedStatement selectId = Mock()
        PreparedStatement existsStatement = Mock()
        PreparedStatement updateStatement = Mock()
        PreparedStatement deleteAttrsStatement = Mock()
        PreparedStatement insertAttrsStatement = Mock()
        ResultSet selectIdResultSet = Mock()
        ResultSet existsResultSet = Mock()
        JdbcStorageObjectMetadataRepository repository = new JdbcStorageObjectMetadataRepository(dataSource)

        and:
        StorageDescriptor descriptor = new StorageDescriptor("pictures", "oracle", "avatars", "ns-a", "bucket-a")
        StorageObjectMetadata metadata = new StorageObjectMetadata(
            "tenant-a",
            descriptor,
            "/avatars/a.png",
            "hash-a",
            "image/png",
            321L,
            "etag-a",
            "v-1",
            "sha256:a",
            Instant.parse("2026-03-17T12:00:00Z"),
            Instant.parse("2026-03-17T12:00:00Z"),
            new ObjectMetadataSyncStatus(ObjectMetadataReconciliationState.SUCCEEDED, null)
        )

        and:
        dataSource.getConnection() >> connection
        connection.getAutoCommit() >> true
        connection.prepareStatement({ String sql -> sql == JdbcStorageObjectMetadataRepository.SELECT_ID_SQL }) >> selectId
        connection.prepareStatement("SELECT id FROM storage_object_metadata WHERE id = ?") >> existsStatement
        connection.prepareStatement({ String sql -> sql == JdbcStorageObjectMetadataRepository.UPDATE_SQL }) >> updateStatement
        connection.prepareStatement({ String sql -> sql == JdbcStorageObjectMetadataRepository.DELETE_ATTRS_SQL }) >> deleteAttrsStatement
        connection.prepareStatement({ String sql -> sql == JdbcStorageObjectMetadataRepository.INSERT_ATTR_SQL }) >> insertAttrsStatement
        selectId.executeQuery() >> selectIdResultSet
        existsStatement.executeQuery() >> existsResultSet
        selectIdResultSet.next() >>> [true, false]
        selectIdResultSet.getString(1) >> "obj-1"
        existsResultSet.next() >>> [true, false]

        when:
        repository.upsert(metadata, [owner: "alice", source: "camera"])

        then:
        1 * selectId.setString(1, "tenant-a")
        1 * selectId.setString(2, "pictures")
        1 * selectId.setString(3, "avatars")
        1 * selectId.setString(4, "hash-a")
        1 * deleteAttrsStatement.setString(1, "obj-1")
        2 * insertAttrsStatement.addBatch()
        1 * insertAttrsStatement.executeBatch()
        1 * connection.commit()
    }

    void "findByTenantAndDescriptor applies tenant predicate and hydrates container attrs"() {
        given:
        DataSource dataSource = Mock()
        Connection connection = Mock()
        PreparedStatement containerStatement = Mock()
        PreparedStatement attrStatement = Mock()
        ResultSet containerResultSet = Mock()
        ResultSet attrResultSet = Mock()
        JdbcStorageContainerMetadataRepository repository = new JdbcStorageContainerMetadataRepository(dataSource)
        StorageDescriptor descriptor = new StorageDescriptor("pictures", "oracle", "avatars", null, "bucket-a")
        Instant now = Instant.parse("2026-03-17T12:00:00Z")

        and:
        dataSource.getConnection() >> connection
        connection.getAutoCommit() >> true
        connection.prepareStatement({ String sql -> sql == JdbcStorageContainerMetadataRepository.FIND_SQL }) >> containerStatement
        connection.prepareStatement({ String sql -> sql == JdbcStorageContainerMetadataRepository.FIND_ATTRS_SQL }) >> attrStatement
        containerStatement.executeQuery() >> containerResultSet
        attrStatement.executeQuery() >> attrResultSet

        and:
        containerResultSet.next() >>> [true, false]
        containerResultSet.getString("id") >> "container-1"
        containerResultSet.getString("tenant_id") >> "tenant-a"
        containerResultSet.getString("storage_name") >> "pictures"
        containerResultSet.getString("provider_id") >> "oracle"
        containerResultSet.getString("logical_container") >> "avatars"
        containerResultSet.getString("provider_namespace") >> null
        containerResultSet.getString("provider_container") >> "bucket-a"
        containerResultSet.getTimestamp("created_at") >> Timestamp.from(now)
        containerResultSet.getTimestamp("updated_at") >> Timestamp.from(now)

        and:
        attrResultSet.next() >>> [true, false]
        attrResultSet.getString("attr_key") >> "region"
        attrResultSet.getString("attr_value") >> "eu-frankfurt-1"

        when:
        def result = repository.findByTenantAndDescriptor("tenant-a", descriptor)

        then:
        result.present
        result.get().metadata.tenantId == "tenant-a"
        result.get().attributes == [region: "eu-frankfurt-1"]

        and:
        1 * containerStatement.setString(1, "tenant-a")
        1 * containerStatement.setString(2, "pictures")
        1 * containerStatement.setString(3, "avatars")
        1 * attrStatement.setString(1, "container-1")
        1 * connection.commit()
    }
}
