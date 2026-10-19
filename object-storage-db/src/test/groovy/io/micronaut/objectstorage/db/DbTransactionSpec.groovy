package io.micronaut.objectstorage.db

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.objectstorage.ObjectStorageException
import io.micronaut.objectstorage.request.UploadRequest
import io.micronaut.transaction.TransactionDefinition
import io.micronaut.transaction.TransactionOperations
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.sql.Connection

class DbTransactionSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext ctx = ApplicationContext.run(
        DbTestProperties.h2([:], 'joined') +
            DbTestProperties.h2(['transaction-propagation': 'REQUIRES_NEW'], 'isolated').findAll { it.key.contains('.isolated.') } +
            DbTestProperties.h2(['transaction-propagation': 'NEVER'], 'never').findAll { it.key.contains('.never.') } +
            DbTestProperties.h2(['transaction-propagation': 'MANDATORY'], 'mandatory').findAll { it.key.contains('.mandatory.') }
    )

    @Shared
    TransactionOperations<Connection> tx = ctx.getBean(Argument.of(TransactionOperations, Connection), Qualifiers.byName('default'))

    def setupSpec() {
        tx.executeWrite { status ->
            status.connection.createStatement().withCloseable { it.execute('CREATE TABLE INVOICE (ID VARCHAR(36) PRIMARY KEY, FILE_KEY VARCHAR(255))') }
        }
    }

    DbStorageOperations storage(String name) {
        ctx.getBean(DbStorageOperations, Qualifiers.byName(name))
    }

    void 'an upload commits together with the caller transaction'() {
        when:
        tx.executeWrite { status ->
            insertInvoice(status.connection, 'inv-1', 'invoices/1.pdf')
            storage('joined').upload(UploadRequest.fromBytes('pdf'.bytes, 'invoices/1.pdf'))
        }

        then:
        invoiceExists('inv-1')
        storage('joined').exists('invoices/1.pdf')
    }

    void 'an upload rolls back together with the caller transaction'() {
        when:
        tx.executeWrite { status ->
            insertInvoice(status.connection, 'inv-2', 'invoices/2.pdf')
            storage('joined').upload(UploadRequest.fromBytes('pdf'.bytes, 'invoices/2.pdf'))
            throw new IllegalStateException('business rule failed')
        }

        then:
        thrown(IllegalStateException)
        !invoiceExists('inv-2')
        !storage('joined').exists('invoices/2.pdf')
    }

    void 'a delete rolls back together with the caller transaction'() {
        given:
        storage('joined').upload(UploadRequest.fromBytes('keep'.bytes, 'keep.txt'))

        when:
        tx.executeWrite { status ->
            storage('joined').delete('keep.txt')
            throw new IllegalStateException('abort')
        }

        then:
        thrown(IllegalStateException)
        storage('joined').retrieve('keep.txt').get().inputStream.text == 'keep'
    }

    void 'content uploaded in the caller transaction can be read back before it commits'() {
        expect:
        tx.executeWrite { status ->
            storage('joined').upload(UploadRequest.fromBytes(('x' * 100).bytes, 'pending.txt'))
            storage('joined').retrieve('pending.txt').get().inputStream.text
        } == 'x' * 100
    }

    void 'REQUIRES_NEW commits the upload even when the caller transaction rolls back'() {
        when:
        tx.executeWrite { status ->
            insertInvoice(status.connection, 'inv-3', 'invoices/3.pdf')
            storage('isolated').upload(UploadRequest.fromBytes('pdf'.bytes, 'invoices/3.pdf'))
            throw new IllegalStateException('business rule failed')
        }

        then:
        thrown(IllegalStateException)
        !invoiceExists('inv-3')
        storage('isolated').exists('invoices/3.pdf')
    }

    void 'NEVER fails inside a transaction and works outside one'() {
        when:
        tx.executeWrite { status ->
            storage('never').upload(UploadRequest.fromBytes('a'.bytes, 'never.txt'))
        }

        then:
        def e = thrown(ObjectStorageException)
        e.message.contains('NEVER')

        when:
        storage('never').upload(UploadRequest.fromBytes('a'.bytes, 'never.txt'))

        then:
        storage('never').exists('never.txt')
    }

    void 'MANDATORY fails outside a transaction'() {
        when:
        storage('mandatory').upload(UploadRequest.fromBytes('a'.bytes, 'mandatory.txt'))

        then:
        thrown(ObjectStorageException)

        when:
        tx.executeWrite { status -> storage('mandatory').upload(UploadRequest.fromBytes('a'.bytes, 'mandatory.txt')) }

        then:
        storage('mandatory').exists('mandatory.txt')
    }

    void 'storages sharing the tables keep their objects apart by bucket'() {
        when:
        storage('joined').upload(UploadRequest.fromBytes('joined'.bytes, 'shared.txt'))
        storage('isolated').upload(UploadRequest.fromBytes('isolated'.bytes, 'shared.txt'))

        then:
        storage('joined').retrieve('shared.txt').get().inputStream.text == 'joined'
        storage('isolated').retrieve('shared.txt').get().inputStream.text == 'isolated'
    }

    private void insertInvoice(Connection connection, String id, String key) {
        connection.prepareStatement('INSERT INTO INVOICE (ID, FILE_KEY) VALUES (?, ?)').withCloseable {
            it.setString(1, id)
            it.setString(2, key)
            it.executeUpdate()
        }
    }

    private boolean invoiceExists(String id) {
        tx.executeRead { status ->
            status.connection.prepareStatement('SELECT 1 FROM INVOICE WHERE ID = ?').withCloseable {
                it.setString(1, id)
                it.executeQuery().withCloseable { it.next() }
            }
        }
    }
}
