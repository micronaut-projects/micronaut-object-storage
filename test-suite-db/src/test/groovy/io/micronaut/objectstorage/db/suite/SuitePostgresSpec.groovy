package io.micronaut.objectstorage.db.suite

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.objectstorage.db.DbContentLayout
import io.micronaut.objectstorage.db.DbMultipartOperations
import io.micronaut.objectstorage.db.DbStorageOperations
import io.micronaut.objectstorage.db.internal.DbStorageRuntime
import io.micronaut.objectstorage.multipart.CompleteMultipartUploadRequest
import io.micronaut.objectstorage.multipart.CreateMultipartUploadRequest
import io.micronaut.objectstorage.multipart.UploadPartRequest
import io.micronaut.objectstorage.request.UploadRequest
import io.micronaut.transaction.TransactionOperations
import org.postgresql.PGConnection
import org.postgresql.PGNotification
import spock.lang.AutoCleanup
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Specification

import java.sql.Connection
import java.sql.DriverManager

@IgnoreIf({ System.getProperty('micronaut.environments') != 'postgres' })
class SuitePostgresSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext ctx = ApplicationContext.run(
        SuiteProperties.storage('pg') +
            SuiteProperties.storage('pgnotify', ['postgres.notify-channel': 'object_events'])
    )

    @Shared
    DbStorageOperations storage = ctx.getBean(DbStorageOperations, Qualifiers.byName('pg'))

    @Shared
    TransactionOperations<Connection> tx = ctx.getBean(Argument.of(TransactionOperations, Connection), Qualifiers.byName('default'))

    void 'the PostgreSQL module stores content in Large Objects by default'() {
        when:
        storage.upload(UploadRequest.fromBytes('x'.bytes, 'probe.txt'))
        def store = ctx.getBean(DbStorageRuntime, Qualifiers.byName('pg')).store()

        then:
        store.class.simpleName == 'PostgresLargeObjectContentStore'
        store.layout == DbContentLayout.LARGE_OBJECT
        count("SELECT COUNT(*) FROM mn_os_object WHERE bucket = 'pg' AND object_key = 'probe.txt' AND content_oid IS NOT NULL AND content IS NULL") == 1
    }

    void 'large content streams through Large Objects and copies are independent'() {
        given:
        byte[] content = new byte[3 * 1024 * 1024 + 17]
        new Random(42).nextBytes(content)

        when:
        storage.upload(UploadRequest.fromBytes(content, 'large.bin'))
        storage.copy('large.bin', 'large-copy.bin')
        storage.delete('large.bin')

        then:
        storage.retrieve('large-copy.bin').get().inputStream.readAllBytes() == content

        cleanup:
        storage.delete('large-copy.bin')
    }

    void 'no Large Object is orphaned by overwrites, copies, deletes and multipart uploads'() {
        given:
        long before = largeObjects()

        when:
        storage.upload(UploadRequest.fromBytes('v1'.bytes, 'lo.txt'))
        storage.upload(UploadRequest.fromBytes('v2'.bytes, 'lo.txt'))
        storage.copy('lo.txt', 'lo-copy.txt')
        storage.copy('lo.txt', 'lo-copy.txt')
        DbMultipartOperations multipart = ctx.getBean(DbMultipartOperations, Qualifiers.byName('pg'))
        def upload = multipart.createMultipartUpload(new CreateMultipartUploadRequest('lo-multipart.txt')).upload
        def p1 = multipart.uploadPart(new UploadPartRequest(upload, 1, UploadRequest.fromBytes(('a' * 10).bytes, 'lo-multipart.txt')))
        multipart.uploadPart(new UploadPartRequest(upload, 2, UploadRequest.fromBytes('discarded'.bytes, 'lo-multipart.txt')))
        multipart.uploadPart(new UploadPartRequest(upload, 2, UploadRequest.fromBytes(('b' * 10).bytes, 'lo-multipart.txt')))
        def p2 = multipart.uploadPart(new UploadPartRequest(upload, 2, UploadRequest.fromBytes(('b' * 10).bytes, 'lo-multipart.txt')))
        multipart.completeMultipartUpload(new CompleteMultipartUploadRequest(upload, [p1.part, p2.part]))

        then:
        storage.retrieve('lo-multipart.txt').get().inputStream.text == 'a' * 10 + 'b' * 10
        largeObjects() == before + 3
        count('SELECT COUNT(*) FROM mn_os_chunk') == 0

        when:
        ['lo.txt', 'lo-copy.txt', 'lo-multipart.txt'].each { storage.delete(it) }

        then:
        largeObjects() == before
    }

    void 'rows deleted outside the library unlink their Large Objects'() {
        given:
        long before = largeObjects()
        storage.upload(UploadRequest.fromBytes('x'.bytes, 'external.txt'))

        expect:
        largeObjects() == before + 1

        when:
        tx.executeWrite { status ->
            status.connection.createStatement().withCloseable { it.executeUpdate("DELETE FROM mn_os_object WHERE object_key = 'external.txt'") }
        }

        then:
        largeObjects() == before
    }

    void 'changes are notified on commit only'() {
        given:
        DbStorageOperations notified = ctx.getBean(DbStorageOperations, Qualifiers.byName('pgnotify'))
        Connection listener = DriverManager.getConnection(
            ctx.getProperty('datasources.default.url', String).get(),
            ctx.getProperty('datasources.default.username', String).get(),
            ctx.getProperty('datasources.default.password', String).get())
        listener.createStatement().withCloseable { it.execute('LISTEN object_events') }

        when:
        notified.upload(UploadRequest.fromBytes('hello'.bytes, 'events/a.txt'))
        notified.delete('events/a.txt')
        try {
            tx.executeWrite { status ->
                notified.upload(UploadRequest.fromBytes('never'.bytes, 'events/rolled-back.txt'))
                throw new IllegalStateException('rollback')
            }
        } catch (IllegalStateException ignored) {
        }
        storage.upload(UploadRequest.fromBytes('quiet'.bytes, 'events/not-notified.txt'))
        List<String> payloads = notifications(listener, 2)*.parameter

        then:
        payloads == [
            '{"bucket":"pgnotify","etag":"5d41402abc4b2a76b9719d911017c592","event":"UPLOADED","key":"events/a.txt","storage":"pgnotify"}',
            '{"bucket":"pgnotify","event":"DELETED","key":"events/a.txt","storage":"pgnotify"}',
        ]
        notifications(listener, 1, 500).isEmpty()

        cleanup:
        listener?.close()
        storage.delete('events/not-notified.txt')
    }

    private static List<PGNotification> notifications(Connection connection, int expected, int timeoutMillis = 5000) {
        PGConnection pg = connection.unwrap(PGConnection)
        List<PGNotification> received = []
        long deadline = System.currentTimeMillis() + timeoutMillis
        while (received.size() < expected && System.currentTimeMillis() < deadline) {
            PGNotification[] batch = pg.getNotifications(100)
            if (batch != null) {
                received.addAll(batch)
            }
        }
        received
    }

    private long largeObjects() {
        count('SELECT COUNT(*) FROM pg_largeobject_metadata')
    }

    private long count(CharSequence sql) {
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
