package io.micronaut.objectstorage.relational

import io.micronaut.objectstorage.request.UploadRequest
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import jakarta.inject.Named

import javax.sql.DataSource
import java.sql.ResultSet

@MicronautTest
class RelationalStorageSpec extends spock.lang.Specification implements TestPropertyProvider {

    @Inject
    RelationalStorageOperations operations

    @Inject
    @Named("default")
    DataSource dataSource

    @Override
    Map<String, String> getProperties() {
        [
            (RelationalStorageConfiguration.PREFIX + '.default.enabled'): 'true',
            (RelationalStorageConfiguration.PREFIX + '.default.table-name'): 'relational_storage_spec_objects',
            (RelationalStorageConfiguration.PREFIX + '.default.key-prefix'): 'tenant-a/',
        ]
    }

    void cleanup() {
        operations.listObjects().each { operations.delete(it) }
    }

    void 'it uploads retrieves copies updates and deletes objects while storing rows under the configured namespace'() {
        given:
        def uploadRequest = UploadRequest.fromBytes('v1-body'.bytes, 'docs/report.txt', 'text/plain')
        uploadRequest.metadata = [owner: 'ops', project: 'micronaut']

        when:
        def uploadResponse = operations.upload(uploadRequest)
        def retrieved = operations.retrieve('docs/report.txt').orElseThrow()

        then:
        uploadResponse.ETag
        operations.exists('docs/report.txt')
        operations.listObjects() == ['docs/report.txt'] as Set
        retrieved.key == 'docs/report.txt'
        retrieved.inputStream.text == 'v1-body'
        retrieved.metadata == [owner: 'ops', project: 'micronaut']
        retrieved.contentType == Optional.of('text/plain')
        storedKeys() == ['tenant-a/docs/report.txt']

        when:
        def updateRequest = UploadRequest.fromBytes('v2-body'.bytes, 'docs/report.txt', 'text/plain')
        updateRequest.metadata = [owner: 'ops', revision: '2']
        def updateResponse = operations.upload(updateRequest)
        operations.copy('docs/report.txt', 'archive/report.txt')
        def copied = operations.retrieve('archive/report.txt').orElseThrow()

        then:
        updateResponse.ETag
        operations.retrieve('docs/report.txt').orElseThrow().inputStream.text == 'v2-body'
        operations.retrieve('docs/report.txt').orElseThrow().metadata == [owner: 'ops', revision: '2']
        copied.inputStream.text == 'v2-body'
        copied.metadata == [owner: 'ops', revision: '2']
        copied.contentType == Optional.of('text/plain')
        storedKeys() == ['tenant-a/archive/report.txt', 'tenant-a/docs/report.txt']

        when:
        operations.delete('docs/report.txt')
        operations.delete('archive/report.txt')

        then:
        !operations.exists('docs/report.txt')
        !operations.exists('archive/report.txt')
        operations.retrieve('docs/report.txt').empty
        operations.listObjects().empty
        storedKeys().empty
    }

    private List<String> storedKeys() {
        dataSource.connection.withCloseable { connection ->
            connection.prepareStatement('SELECT object_key FROM relational_storage_spec_objects ORDER BY object_key ASC').withCloseable { statement ->
                ResultSet resultSet = statement.executeQuery()
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
