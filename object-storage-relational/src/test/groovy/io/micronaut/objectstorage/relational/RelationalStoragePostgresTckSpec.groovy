package io.micronaut.objectstorage.relational

import io.micronaut.objectstorage.request.UploadRequest
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import org.testcontainers.DockerClientFactory
import spock.lang.Requires

import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

@MicronautTest
@Requires({ DockerClientFactory.instance().isDockerAvailable() })
class RelationalStoragePostgresTckSpec extends AbstractRelationalStorageOperationsSpec implements TestPropertyProvider {

    private static final int LARGE_PAYLOAD_SIZE = 5 * 1024 * 1024

    @Override
    Map<String, String> getProperties() {
        [
            (RelationalStorageConfiguration.PREFIX + '.default.enabled'): 'true',
            (RelationalStorageConfiguration.PREFIX + '.default.datasource'): 'postgres',
            (RelationalStorageConfiguration.PREFIX + '.default.table-name'): 'relational_storage_postgres_tck_objects',
        ]
    }

    void 'it streams large PostgreSQL LOB payloads without losing metadata or ordering'() {
        given:
        Path payload = Files.createTempFile('relational-postgres-large-', '.bin')
        byte[] buffer = new byte[8192]
        for (int index = 0; index < buffer.length; index++) {
            buffer[index] = (byte) (index % 251)
        }
        Files.newOutputStream(payload).withCloseable { outputStream ->
            int remaining = LARGE_PAYLOAD_SIZE
            while (remaining > 0) {
                int chunkSize = Math.min(remaining, buffer.length)
                outputStream.write(buffer, 0, chunkSize)
                remaining -= chunkSize
            }
        }
        UploadRequest uploadRequest = UploadRequest.fromPath(payload, 'large')
        uploadRequest.contentType = 'application/octet-stream'
        uploadRequest.metadata = [profile: 'streaming']

        when:
        def response = operations.upload(uploadRequest)
        def entry = operations.retrieve(uploadRequest.key).orElseThrow()

        then:
        response.ETag
        entry.metadata == [profile: 'streaming']
        entry.contentType == Optional.of('application/octet-stream')
        digest(entry.inputStream) == digest(Files.newInputStream(payload))
        entry.inputStream.bytes.length == LARGE_PAYLOAD_SIZE
        operations.listObjects().contains(uploadRequest.key)

        cleanup:
        operations?.delete(uploadRequest.key)
        Files.deleteIfExists(payload)
    }

    private static String digest(InputStream inputStream) {
        MessageDigest digest = MessageDigest.getInstance('SHA-256')
        inputStream.withCloseable { stream ->
            byte[] buffer = new byte[8192]
            int read
            while ((read = stream.read(buffer)) != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().encodeHex().toString()
    }
}
