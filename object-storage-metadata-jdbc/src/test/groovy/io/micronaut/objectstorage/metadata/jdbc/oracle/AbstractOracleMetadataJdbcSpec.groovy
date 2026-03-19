package io.micronaut.objectstorage.metadata.jdbc.oracle

import io.micronaut.objectstorage.metadata.MetadataAwareObjectStorageOperations
import io.micronaut.objectstorage.metadata.ObjectMetadataReconciliationState
import io.micronaut.objectstorage.metadata.ObjectMetadataSyncStatus
import io.micronaut.objectstorage.metadata.ObjectStorageMetadataOperations
import io.micronaut.objectstorage.metadata.ObjectStorageOperationType
import io.micronaut.objectstorage.metadata.StorageContainerMetadata
import io.micronaut.objectstorage.metadata.StorageDescriptor
import io.micronaut.objectstorage.metadata.StorageObjectMetadata
import io.micronaut.objectstorage.metadata.TenantResolver
import io.micronaut.objectstorage.metadata.jdbc.repository.JdbcStorageContainerMetadataRepository
import io.micronaut.objectstorage.metadata.jdbc.repository.JdbcStorageMetadataOutboxRepository
import io.micronaut.objectstorage.metadata.jdbc.repository.JdbcStorageObjectMetadataRepository
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageContainerMetadataRepository
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageMetadataOutboxRepository
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageObjectMetadataRepository
import io.micronaut.objectstorage.local.LocalStorageConfiguration
import io.micronaut.objectstorage.local.LocalStorageOperations
import oracle.jdbc.pool.OracleDataSource
import org.testcontainers.oracle.OracleContainer
import org.testcontainers.utility.DockerImageName
import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Files
import java.sql.Connection
import java.time.Instant

abstract class AbstractOracleMetadataJdbcSpec extends Specification {

    private static final DockerImageName ORACLE_FREE = DockerImageName.parse('gvenzl/oracle-free:23-slim-faststart')

    @Shared OracleContainer oracle = new OracleContainer(ORACLE_FREE)
    @Shared OracleDataSource dataSource
    @Shared StorageDescriptor descriptor = new StorageDescriptor('default', 'local', 'default', null, 'default')
    @Shared MutableTenantResolver tenantResolver = new MutableTenantResolver()
    @Shared StorageContainerMetadataRepository containerRepository
    @Shared StorageObjectMetadataRepository objectRepository
    @Shared StorageMetadataOutboxRepository outboxRepository
    @Shared ObjectStorageMetadataOperations metadataOperations
    @Shared MetadataAwareObjectStorageOperations operations
    @Shared LocalStorageOperations delegate
    @Shared java.nio.file.Path storagePath

    void setupSpec() {
        oracle.start()
        dataSource = new OracleDataSource()
        dataSource.URL = oracle.jdbcUrl
        dataSource.user = oracle.username
        dataSource.password = oracle.password
        applySchema()
        containerRepository = new JdbcStorageContainerMetadataRepository(dataSource)
        objectRepository = new JdbcStorageObjectMetadataRepository(dataSource)
        outboxRepository = new JdbcStorageMetadataOutboxRepository(dataSource)
        metadataOperations = new io.micronaut.objectstorage.metadata.jdbc.DefaultObjectStorageMetadataOperations(descriptor, tenantResolver, containerRepository, objectRepository)
        storagePath = Files.createTempDirectory('oracle-metadata-local-storage')
        def configuration = new LocalStorageConfiguration('default')
        configuration.path = storagePath
        delegate = new LocalStorageOperations(configuration)
        operations = new io.micronaut.objectstorage.metadata.jdbc.DefaultMetadataAwareObjectStorageOperations<>(
            delegate,
            metadataOperations,
            descriptor,
            tenantResolver,
            containerRepository,
            outboxRepository,
            []
        )
    }

    void setup() {
        clearTables()
        clearLocalStorage()
        tenantResolver.tenantId = 'tenant-a'
        seedContainer('tenant-a')
        seedContainer('tenant-b')
    }

    void cleanupSpec() {
        oracle.stop()
    }

    void reconcileAll() {
        outboxRepository.findDueEntries(Instant.now().plusSeconds(5), 20).each { record ->
            switch (record.operationType) {
                case ObjectStorageOperationType.UPLOAD -> {
                    def key = record.objectKey.orElseThrow()
                    def hash = record.objectKeyHash.orElseGet { StorageObjectMetadata.deterministicObjectKeyHash(key) }
                    objectRepository.upsert(new StorageObjectMetadata(
                        record.tenantId,
                        descriptor,
                        key,
                        hash,
                        null,
                        null,
                        null,
                        null,
                        null,
                        Instant.now(),
                        Instant.now(),
                        new ObjectMetadataSyncStatus(ObjectMetadataReconciliationState.SUCCEEDED, null)
                    ), [:])
                }
                case ObjectStorageOperationType.COPY -> {
                    def sourceKey = record.objectKey.orElseThrow()
                    def sourceHash = record.objectKeyHash.orElseGet { StorageObjectMetadata.deterministicObjectKeyHash(sourceKey) }
                    def source = objectRepository.findByTenantAndObjectHash(record.tenantId, descriptor, sourceHash).orElseThrow()
                    def destinationKey = record.destinationObjectKey.orElseThrow()
                    def destinationHash = record.destinationObjectKeyHash.orElseGet { StorageObjectMetadata.deterministicObjectKeyHash(destinationKey) }
                    def sourceMetadata = source.metadata()
                    objectRepository.upsert(new StorageObjectMetadata(
                        record.tenantId,
                        descriptor,
                        destinationKey,
                        destinationHash,
                        sourceMetadata.contentType.orElse(null),
                        sourceMetadata.contentLength.present ? sourceMetadata.contentLength.asLong : null,
                        sourceMetadata.etag.orElse(null),
                        sourceMetadata.providerVersionId.orElse(null),
                        sourceMetadata.checksum.orElse(null),
                        sourceMetadata.createdAt,
                        Instant.now(),
                        new ObjectMetadataSyncStatus(ObjectMetadataReconciliationState.SUCCEEDED, null)
                    ), source.attributes())
                }
                case ObjectStorageOperationType.DELETE -> {
                    def hash = record.objectKeyHash.orElseGet { StorageObjectMetadata.deterministicObjectKeyHash(record.objectKey.orElseThrow()) }
                    objectRepository.deleteByTenantAndObjectHash(record.tenantId, descriptor, hash)
                }
            }
            outboxRepository.markSucceeded(record.id, Instant.now())
        }
    }

    void seedContainer(String tenantId) {
        containerRepository.upsert(new StorageContainerMetadata(tenantId, descriptor, Instant.now(), Instant.now()), [tenant: tenantId])
    }

    private void clearTables() {
        withConnection {
            it.createStatement().withCloseable { statement ->
                statement.executeUpdate('DELETE FROM storage_object_metadata_attr')
                statement.executeUpdate('DELETE FROM storage_object_metadata')
                statement.executeUpdate('DELETE FROM storage_container_metadata_attr')
                statement.executeUpdate('DELETE FROM storage_container_metadata')
                statement.executeUpdate('DELETE FROM storage_metadata_outbox')
            }
            it.commit()
        }
    }

    private void clearLocalStorage() {
        if (Files.exists(storagePath)) {
            Files.walk(storagePath)
                .sorted(Comparator.reverseOrder())
                .filter { it != storagePath }
                .forEach { Files.deleteIfExists(it) }
        }
    }

    private void applySchema() {
        String sql = AbstractOracleMetadataJdbcSpec.classLoader.getResource('db/metadata/oracle/schema-v1.sql').text
        withConnection {
            sql.split(';')
                .collect { it.trim() }
                .findAll { !it.isBlank() }
                .each { stmt -> it.createStatement().execute(stmt) }
            it.commit()
        }
    }

    private void withConnection(Closure<?> action) {
        Connection connection = dataSource.connection
        try {
            connection.autoCommit = false
            action.call(connection)
        } finally {
            connection.close()
        }
    }

    static final class MutableTenantResolver implements TenantResolver {
        String tenantId = 'tenant-a'
        @Override
        String resolveTenantId() {
            tenantId
        }
    }
}
