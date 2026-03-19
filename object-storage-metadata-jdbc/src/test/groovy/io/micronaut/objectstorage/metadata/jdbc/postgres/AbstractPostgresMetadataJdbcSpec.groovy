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
package io.micronaut.objectstorage.metadata.jdbc.postgres

import io.micronaut.objectstorage.metadata.MetadataAwareObjectStorageOperations
import io.micronaut.objectstorage.metadata.ObjectMetadataReconciliationState
import io.micronaut.objectstorage.metadata.ObjectMetadataSyncStatus
import io.micronaut.objectstorage.metadata.ObjectStorageMetadataOperations
import io.micronaut.objectstorage.metadata.ObjectStorageOperationType
import io.micronaut.objectstorage.metadata.StorageContainerMetadata
import io.micronaut.objectstorage.metadata.StorageDescriptor
import io.micronaut.objectstorage.metadata.StorageObjectMetadata
import io.micronaut.objectstorage.metadata.TenantResolver
import io.micronaut.objectstorage.metadata.jdbc.DefaultMetadataAwareObjectStorageOperations
import io.micronaut.objectstorage.metadata.jdbc.DefaultObjectStorageMetadataOperations
import io.micronaut.objectstorage.metadata.jdbc.repository.JdbcStorageContainerMetadataRepository
import io.micronaut.objectstorage.metadata.jdbc.repository.JdbcStorageMetadataOutboxRepository
import io.micronaut.objectstorage.metadata.jdbc.repository.JdbcStorageObjectMetadataRepository
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageContainerMetadataRepository
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageMetadataOutboxRepository
import io.micronaut.objectstorage.metadata.jdbc.repository.StorageObjectMetadataRepository
import io.micronaut.objectstorage.local.LocalStorageConfiguration
import io.micronaut.objectstorage.local.LocalStorageOperations
import org.postgresql.ds.PGSimpleDataSource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Files
import java.sql.Connection
import java.time.Instant

abstract class AbstractPostgresMetadataJdbcSpec extends Specification {

    private static final DockerImageName POSTGRES = DockerImageName.parse('postgres:16-alpine')

    @Shared PostgreSQLContainer postgres = new PostgreSQLContainer(POSTGRES)
    @Shared PGSimpleDataSource dataSource
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
        postgres.start()
        dataSource = new PGSimpleDataSource()
        dataSource.URL = postgres.jdbcUrl
        dataSource.user = postgres.username
        dataSource.password = postgres.password
        applySchema()
        containerRepository = new JdbcStorageContainerMetadataRepository(dataSource)
        objectRepository = new JdbcStorageObjectMetadataRepository(dataSource)
        outboxRepository = new JdbcStorageMetadataOutboxRepository(dataSource)
        metadataOperations = new DefaultObjectStorageMetadataOperations(tenantResolver, containerRepository, objectRepository)
        storagePath = Files.createTempDirectory('postgres-metadata-local-storage')
        def configuration = new LocalStorageConfiguration('default')
        configuration.path = storagePath
        delegate = new LocalStorageOperations(configuration)
        operations = new DefaultMetadataAwareObjectStorageOperations<>(
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
        postgres.stop()
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
                    objectRepository.upsert(new StorageObjectMetadata(
                        record.tenantId,
                        descriptor,
                        destinationKey,
                        destinationHash,
                        source.metadata().contentType.orElse(null),
                        source.metadata().contentLength.present ? source.metadata().contentLength.asLong : null,
                        source.metadata().etag.orElse(null),
                        source.metadata().providerVersionId.orElse(null),
                        source.metadata().checksum.orElse(null),
                        source.metadata().createdAt,
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

    int tableCount(String tableName) {
        withConnection { connection ->
            connection.prepareStatement('SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = current_schema() AND table_name = ?').withCloseable { statement ->
                statement.setString(1, tableName)
                def resultSet = statement.executeQuery()
                resultSet.next()
                return resultSet.getInt(1)
            }
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
        String sql = AbstractPostgresMetadataJdbcSpec.classLoader.getResource('db/metadata/postgresql/schema-v1.sql').text
        withConnection {
            sql.split(';')
                .collect { it.trim() }
                .findAll { !it.isBlank() }
                .each { stmt -> it.createStatement().execute(stmt) }
            it.commit()
        }
    }

    private <T> T withConnection(Closure<T> action) {
        Connection connection = dataSource.connection
        try {
            connection.autoCommit = false
            return action.call(connection)
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
