package io.micronaut.objectstorage.relational

import io.micronaut.objectstorage.ObjectStorageException
import io.micronaut.objectstorage.request.UploadRequest
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import jakarta.inject.Named

import javax.sql.DataSource

@MicronautTest
class RelationalStorageFailureSpec extends spock.lang.Specification implements TestPropertyProvider {

    private static final String TABLE_NAME = 'relational_storage_failure_spec_objects'

    @Inject
    RelationalStorageOperations operations

    @Inject
    @Named('default')
    DataSource dataSource

    @Override
    Map<String, String> getProperties() {
        [
            (RelationalStorageConfiguration.PREFIX + '.default.enabled'): 'true',
            (RelationalStorageConfiguration.PREFIX + '.default.table-name'): TABLE_NAME,
        ]
    }

    void cleanup() {
        operations.listObjects().each { operations.delete(it) }
    }

    void 'failed overwrite rolls back the prior row instead of orphaning the object'() {
        given:
        operations.upload(UploadRequest.fromBytes('v1-body'.bytes, 'docs/report.txt', 'text/plain'))
        UploadRequest failingUpdate = UploadRequest.fromBytes('v2-body'.bytes, 'docs/report.txt', 'x' * 300)

        when:
        operations.upload(failingUpdate)

        then:
        def e = thrown(ObjectStorageException)
        e.message == 'Error storing relational object for key [docs/report.txt]'
        operations.retrieve('docs/report.txt').orElseThrow().with {
            inputStream.text == 'v1-body'
            contentType == Optional.of('text/plain')
            metadata == [:]
        }
        storedKeys() == ['docs/report.txt']
    }

    void 'entry stream fails cleanly when the payload row disappears after metadata lookup'() {
        given:
        operations.upload(UploadRequest.fromBytes('payload'.bytes, 'docs/missing.bin', 'application/octet-stream'))
        def entry = operations.retrieve('docs/missing.bin').orElseThrow()
        deleteRow('docs/missing.bin')

        when:
        entry.inputStream.text

        then:
        def e = thrown(ObjectStorageException)
        e.message == 'No relational object found for key [docs/missing.bin]'
    }

    private void deleteRow(String key) {
        dataSource.connection.withCloseable { connection ->
            connection.prepareStatement("DELETE FROM ${TABLE_NAME} WHERE object_key = ?").withCloseable { statement ->
                statement.setString(1, key)
                statement.executeUpdate()
            }
        }
    }

    private List<String> storedKeys() {
        dataSource.connection.withCloseable { connection ->
            connection.prepareStatement("SELECT object_key FROM ${TABLE_NAME} ORDER BY object_key ASC").withCloseable { statement ->
                def resultSet = statement.executeQuery()
                List<String> keys = []
                while (resultSet.next()) {
                    keys << resultSet.getString(1)
                }
                resultSet.close()
                return keys
            }
        }
    }
}
