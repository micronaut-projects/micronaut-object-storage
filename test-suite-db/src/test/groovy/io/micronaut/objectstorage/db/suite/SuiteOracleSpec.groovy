package io.micronaut.objectstorage.db.suite

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.objectstorage.db.DbContentLayout
import io.micronaut.objectstorage.db.DbMultipartOperations
import io.micronaut.objectstorage.db.DbStorageOperations
import io.micronaut.objectstorage.db.internal.DbStorageRuntime
import io.micronaut.objectstorage.request.UploadRequest
import io.micronaut.objectstorage.multipart.CompleteMultipartUploadRequest
import io.micronaut.objectstorage.multipart.CreateMultipartUploadRequest
import io.micronaut.objectstorage.multipart.UploadPartRequest
import io.micronaut.transaction.TransactionOperations
import spock.lang.AutoCleanup
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Specification

import java.sql.Connection

@IgnoreIf({ System.getProperty('micronaut.environments') != 'oracle' })
class SuiteOracleSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext ctx = ApplicationContext.run(SuiteProperties.storage('oracle'))

    @Shared
    DbStorageOperations storage = ctx.getBean(DbStorageOperations, Qualifiers.byName('oracle'))

    @Shared
    DbMultipartOperations multipart = ctx.getBean(DbMultipartOperations, Qualifiers.byName('oracle'))

    void 'the Oracle module stores content in SecureFile BLOBs by default'() {
        when:
        storage.upload(UploadRequest.fromBytes('x'.bytes, 'probe.txt'))
        def store = ctx.getBean(DbStorageRuntime, Qualifiers.byName('oracle')).store()

        then:
        store.class.simpleName == 'OracleBlobContentStore'
        store.layout == DbContentLayout.SINGLE_COLUMN
        count("SELECT COUNT(*) FROM MN_OS_OBJECT WHERE BUCKET = 'oracle' AND CONTENT IS NOT NULL AND DBMS_LOB.GETLENGTH(CONTENT) = 1") == 1
    }

    void 'objects larger than a window stream through the BLOB locator'() {
        given:
        byte[] content = new byte[3 * 1024 * 1024 + 17]
        new Random(42).nextBytes(content)

        when:
        storage.upload(UploadRequest.fromBytes(content, 'large.bin'))

        then:
        storage.retrieve('large.bin').get().inputStream.readAllBytes() == content

        when:
        storage.copy('large.bin', 'large-copy.bin')

        then:
        storage.retrieve('large-copy.bin').get().inputStream.readAllBytes() == content
    }

    void 'multipart parts are BLOBs appended inside the database'() {
        given:
        String key = 'multipart/oracle.bin'
        byte[] part1 = new byte[5 * 1024 * 1024]
        byte[] part2 = 'tail'.bytes
        new Random(7).nextBytes(part1)
        def upload = multipart.createMultipartUpload(new CreateMultipartUploadRequest(key, null, [:])).upload

        when:
        def p1 = multipart.uploadPart(new UploadPartRequest(upload, 1, UploadRequest.fromBytes(part1, key)))
        def p2 = multipart.uploadPart(new UploadPartRequest(upload, 2, UploadRequest.fromBytes(part2, key)))

        then:
        count("SELECT COUNT(*) FROM MN_OS_UPLOAD_PART WHERE UPLOAD_ID = '${upload.uploadId}' AND CONTENT IS NOT NULL") == 2

        when:
        multipart.completeMultipartUpload(new CompleteMultipartUploadRequest(upload, [p1.part, p2.part]))

        then:
        storage.retrieve(key).get().inputStream.readAllBytes() == concat(part1, part2)
        count('SELECT COUNT(*) FROM MN_OS_CHUNK') == 0
        count("SELECT COUNT(*) FROM MN_OS_UPLOAD_PART WHERE UPLOAD_ID = '${upload.uploadId}'") == 0
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] result = Arrays.copyOf(a, a.length + b.length)
        System.arraycopy(b, 0, result, a.length, b.length)
        result
    }

    private long count(CharSequence sql) {
        TransactionOperations<Connection> tx = ctx.getBean(Argument.of(TransactionOperations, Connection), Qualifiers.byName('default'))
        tx.executeRead { status ->
            status.connection.createStatement().withCloseable { st ->
                st.executeQuery(sql.toString()).withCloseable { rs ->
                    rs.next()
                    rs.getLong(1)
                }
            }
        }
    }
}
