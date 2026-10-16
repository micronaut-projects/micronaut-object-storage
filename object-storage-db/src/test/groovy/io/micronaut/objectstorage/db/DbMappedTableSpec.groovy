package io.micronaut.objectstorage.db

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.objectstorage.ObjectStorageException
import io.micronaut.objectstorage.request.BytesUploadRequest
import io.micronaut.objectstorage.request.ListObjectsRequest
import io.micronaut.objectstorage.request.UploadRequest
import io.micronaut.transaction.TransactionOperations
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.sql.Connection

class DbMappedTableSpec extends Specification {

    static final String DDL = '''
        CREATE TABLE CONTRACT_FILES (
            TENANT VARCHAR(64) NOT NULL,
            FILE_PATH VARCHAR(512) NOT NULL,
            FILE_DATA BLOB,
            FILE_SIZE BIGINT,
            MIME VARCHAR(255),
            CHECKSUM VARCHAR(64),
            META CLOB,
            MODIFIED_AT TIMESTAMP,
            OWNER_ID VARCHAR(64),
            PRIMARY KEY (TENANT, FILE_PATH)
        )'''

    static Map<String, String> mapped(String name, String bucket, Map<String, String> extra = [:]) {
        String prefix = "${DbStorageConfiguration.PREFIX}.${name}."
        Map<String, String> props = [
            'bucket'                     : bucket,
            'table.name'                 : 'CONTRACT_FILES',
            'table.columns.key'          : 'FILE_PATH',
            'table.columns.content'      : 'FILE_DATA',
            'table.columns.content-length': 'FILE_SIZE',
            'table.columns.content-type' : 'MIME',
            'table.columns.etag'         : 'CHECKSUM',
            'table.columns.metadata'     : 'META',
            'table.columns.bucket'       : 'TENANT',
            'table.columns.updated-at'   : 'MODIFIED_AT',
        ] + extra
        props.collectEntries { k, v -> [(prefix + k): v] } as Map<String, String>
    }

    @Shared
    @AutoCleanup
    ApplicationContext ctx = ApplicationContext.run(
        DbTestProperties.h2([:], 'builtin') +
            mapped('acme', 'acme') +
            mapped('globex', 'globex') +
            [("${DbStorageConfiguration.PREFIX}.minimal.table.name".toString())           : 'PLAIN_FILES',
             ("${DbStorageConfiguration.PREFIX}.minimal.table.columns.key".toString())    : 'PATH',
             ("${DbStorageConfiguration.PREFIX}.minimal.table.columns.content".toString()): 'DATA'] +
            mapped('broken', 'acme', ['table.columns.etag': 'NO_SUCH_COLUMN'])
    )

    def setupSpec() {
        TransactionOperations<Connection> tx = ctx.getBean(Argument.of(TransactionOperations, Connection), Qualifiers.byName('default'))
        tx.executeWrite { status ->
            status.connection.createStatement().withCloseable { it.execute(DDL) }
            status.connection.createStatement().withCloseable { it.execute('CREATE TABLE PLAIN_FILES (PATH VARCHAR(255) PRIMARY KEY, DATA BLOB)') }
        }
    }

    DbStorageOperations storage(String name) {
        ctx.getBean(DbStorageOperations, Qualifiers.byName(name))
    }

    void 'it stores objects in the mapped table'() {
        given:
        def acme = storage('acme')

        when:
        def response = acme.upload(new BytesUploadRequest('hello'.bytes, 'application/pdf', 'contracts/1.pdf', [owner: 'jane']))

        then:
        response.ETag == '5d41402abc4b2a76b9719d911017c592'

        and: 'the row is a plain row of the application table'
        def row = query('SELECT TENANT, FILE_SIZE, MIME, CHECKSUM, FILE_DATA FROM CONTRACT_FILES WHERE FILE_PATH = ?', 'contracts/1.pdf')
        row.TENANT == 'acme'
        row.FILE_SIZE == 5
        row.MIME == 'application/pdf'
        row.CHECKSUM == '5d41402abc4b2a76b9719d911017c592'

        and:
        def entry = acme.retrieve('contracts/1.pdf').get()
        entry.inputStream.text == 'hello'
        entry.contentType.get() == 'application/pdf'
        entry.metadata.get('owner') == 'jane'
    }

    void 'overwriting updates the row in place'() {
        given:
        def acme = storage('acme')
        acme.upload(UploadRequest.fromBytes('v1'.bytes, 'overwrite.txt'))

        when:
        acme.upload(UploadRequest.fromBytes('version 2'.bytes, 'overwrite.txt'))

        then:
        acme.retrieve('overwrite.txt').get().inputStream.text == 'version 2'
        query('SELECT COUNT(*) AS C FROM CONTRACT_FILES WHERE FILE_PATH = ?', 'overwrite.txt').C == 1
    }

    void 'the bucket column keeps tenants apart'() {
        when:
        storage('acme').upload(UploadRequest.fromBytes('acme'.bytes, 'same-key'))
        storage('globex').upload(UploadRequest.fromBytes('globex'.bytes, 'same-key'))

        then:
        storage('acme').retrieve('same-key').get().inputStream.text == 'acme'
        storage('globex').retrieve('same-key').get().inputStream.text == 'globex'
        !storage('globex').listObjects().contains('contracts/1.pdf')

        when:
        storage('globex').delete('same-key')

        then:
        storage('acme').exists('same-key')
        !storage('globex').exists('same-key')
    }

    void 'extra columns are written on upload'() {
        when:
        storage('acme').upload(UploadRequest.fromBytes('x'.bytes, 'owned.txt')) { it.setColumn('OWNER_ID', 'user-42') }

        then:
        query('SELECT OWNER_ID FROM CONTRACT_FILES WHERE FILE_PATH = ?', 'owned.txt').OWNER_ID == 'user-42'
    }

    void 'extra column names must be identifiers'() {
        when:
        new DbUploadOptions().setColumn('OWNER_ID = 1; DROP TABLE X; --', 'x')

        then:
        thrown(IllegalArgumentException)
    }

    void 'extra columns are rejected on the built-in tables'() {
        when:
        storage('builtin').upload(UploadRequest.fromBytes('x'.bytes, 'x.txt')) { it.setColumn('OWNER_ID', 'user-42') }

        then:
        def e = thrown(ObjectStorageException)
        e.message.contains('built-in tables')
    }

    void 'list, copy and delete work on the mapped table'() {
        given:
        def acme = storage('acme')
        ['docs/a', 'docs/b', 'docs/c', 'other/d'].each { acme.upload(UploadRequest.fromBytes(it.bytes, it)) }

        when:
        def page = acme.listObjects(new ListObjectsRequest(2, 'docs/', null))

        then:
        page.keys == ['docs/a', 'docs/b']
        acme.listObjects(new ListObjectsRequest(2, 'docs/', page.continuationToken.get())).keys == ['docs/c']

        when:
        acme.copy('docs/a', 'docs/a-copy')

        then:
        acme.retrieve('docs/a-copy').get().inputStream.text == 'docs/a'

        when:
        acme.delete('docs/a')

        then:
        !acme.exists('docs/a')
        acme.exists('docs/a-copy')
    }

    void 'a table with only key and content columns works'() {
        given:
        def minimal = storage('minimal')

        when:
        minimal.upload(new BytesUploadRequest('first'.bytes, 'text/plain', 'a.txt', [ignored: 'yes']))
        minimal.upload(UploadRequest.fromBytes('second version'.bytes, 'a.txt'))

        then:
        minimal.retrieve('a.txt').get().inputStream.text == 'second version'
        minimal.listObjects() == ['a.txt'] as Set
        ctx.getBean(DbStorageMappingValidator, Qualifiers.byName('minimal')).validate().isEmpty()
    }

    void 'bucket features need the built-in tables'() {
        when:
        ctx.getBean(DbBucketOperations, Qualifiers.byName('acme')).create('new-bucket')

        then:
        def e = thrown(ObjectStorageException)
        e.message.contains('CONTRACT_FILES')
    }

    void 'the validator reports unmapped columns'() {
        expect:
        ctx.getBean(DbStorageMappingValidator, Qualifiers.byName('acme')).validate().isEmpty()
        ctx.getBean(DbStorageMappingValidator, Qualifiers.byName('builtin')).validate().isEmpty()

        when:
        ctx.getBean(DbStorageMappingValidator, Qualifiers.byName('broken')).assertValid()

        then:
        def e = thrown(ObjectStorageException)
        e.message.contains('NO_SUCH_COLUMN')
    }

    private Map<String, Object> query(String sql, String key) {
        TransactionOperations<Connection> tx = ctx.getBean(Argument.of(TransactionOperations, Connection), Qualifiers.byName('default'))
        tx.executeRead { status ->
            status.connection.prepareStatement(sql).withCloseable { ps ->
                ps.setString(1, key)
                ps.executeQuery().withCloseable { rs ->
                    rs.next()
                    (1..rs.metaData.columnCount).collectEntries { [(rs.metaData.getColumnLabel(it)): rs.getObject(it)] }
                }
            }
        }
    }
}
