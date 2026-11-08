package io.micronaut.objectstorage.db

import io.micronaut.context.ApplicationContext
import io.micronaut.objectstorage.ObjectStorageConfigurationSpecification
import io.micronaut.transaction.TransactionDefinition

class DbStorageConfigurationSpec extends ObjectStorageConfigurationSpecification<DbStorageOperations> {

    void 'it binds the configuration'() {
        given:
        def ctx = ApplicationContext.run([
            'micronaut.object-storage.db.files.datasource'            : 'other',
            'micronaut.object-storage.db.files.bucket'                : 'profile-pictures',
            'micronaut.object-storage.db.files.chunk-size'            : '256KB',
            'micronaut.object-storage.db.files.max-buffered-bytes'    : '64MB',
            'micronaut.object-storage.db.files.max-object-size'       : '10MB',
            'micronaut.object-storage.db.files.content-layout'        : 'SINGLE_COLUMN',
            'micronaut.object-storage.db.files.schema-generate'       : 'FLYWAY',
            'micronaut.object-storage.db.files.transaction-propagation': 'REQUIRES_NEW',
            'micronaut.object-storage.db.files.validate-mapping'      : 'true',
            'micronaut.object-storage.db.files.table.name'            : 'LEGAL.CONTRACT_FILES',
            'micronaut.object-storage.db.files.table.columns.key'     : 'FILE_PATH',
            'micronaut.object-storage.db.files.table.columns.content' : 'FILE_DATA',
            'micronaut.object-storage.db.files.table.columns.etag'    : 'CHECKSUM',
            'micronaut.object-storage.db.files.table.columns.updated-at': 'MODIFIED_AT',
        ])

        when:
        DbStorageConfiguration configuration = ctx.getBean(DbStorageConfiguration, io.micronaut.inject.qualifiers.Qualifiers.byName('files'))

        then:
        configuration.datasource == 'other'
        configuration.bucket == 'profile-pictures'
        configuration.chunkSize == 256 * 1024
        configuration.maxBufferedBytes == 64L * 1024 * 1024
        configuration.maxObjectSize == 10L * 1024 * 1024
        configuration.contentLayout == DbContentLayout.SINGLE_COLUMN
        configuration.schemaGenerate == DbSchemaGenerate.FLYWAY
        configuration.transactionPropagation == TransactionDefinition.Propagation.REQUIRES_NEW
        configuration.validateMapping
        configuration.table.name == 'LEGAL.CONTRACT_FILES'
        configuration.table.columns.key == 'FILE_PATH'
        configuration.table.columns.content == 'FILE_DATA'
        configuration.table.columns.etag == 'CHECKSUM'
        configuration.table.columns.updatedAt == 'MODIFIED_AT'

        cleanup:
        ctx.close()
    }

    void 'the bucket defaults to the storage name and nothing touches the database at startup'() {
        given: 'a storage on a datasource that does not exist'
        def ctx = ApplicationContext.run(['micronaut.object-storage.db.invoices.enabled': 'true'])

        when:
        DbStorageOperations operations = ctx.getBean(DbStorageOperations, io.micronaut.inject.qualifiers.Qualifiers.byName('invoices'))

        then:
        operations.configuration.bucket == 'invoices'
        operations.configuration.datasource == 'default'
        operations.configuration.chunkSize == DbStorageConfiguration.DEFAULT_CHUNK_SIZE
        operations.configuration.transactionPropagation == TransactionDefinition.Propagation.REQUIRED

        when: 'it is used'
        operations.exists('x')

        then: 'the missing datasource is reported on first use'
        def e = thrown(io.micronaut.objectstorage.ObjectStorageException)
        e.message.contains("datasource 'default'")

        cleanup:
        ctx.close()
    }

    @Override
    String getConfigPrefix() {
        DbStorageConfiguration.PREFIX
    }

    @Override
    Class<DbStorageOperations> getObjectStorage() {
        DbStorageOperations
    }
}
