package io.micronaut.objectstorage.request

import io.micronaut.http.server.netty.multipart.NettyCompletedFileUpload
import io.micronaut.objectstorage.ObjectStorageOperationsSpecification
import io.netty.handler.codec.http.multipart.DiskFileUpload
import io.netty.handler.codec.http.multipart.FileUpload
import spock.lang.Specification
import spock.lang.Subject

import java.nio.charset.Charset
import java.nio.file.Path

@Subject(CompletedFileUploadRequest)
class CompletedFileUploadSpec extends Specification {

    void "it can be created from a completed file upload"() {
        given:
        NettyCompletedFileUpload completedFileUpload = createCompletedFileUpload()

        when:
        CompletedFileUploadRequest request =
                UploadRequest.fromCompletedFileUpload(completedFileUpload) as CompletedFileUploadRequest

        then:
        assertThatRequestIsValid(request, completedFileUpload.name)
    }

    void "it can be created from a completed file upload and key"() {
        given:
        NettyCompletedFileUpload completedFileUpload = createCompletedFileUpload()
        String key = "path/tp=o/file.txt"

        when:
        CompletedFileUploadRequest request =
                UploadRequest.fromCompletedFileUpload(completedFileUpload, key) as CompletedFileUploadRequest

        then:
        assertThatRequestIsValid(request, key)

    }

    private NettyCompletedFileUpload createCompletedFileUpload() {
        Path path = ObjectStorageOperationsSpecification.createTempFile()
        File file = path.toFile()
        FileUpload fileUpload = new DiskFileUpload(file.name, file.name, "text/plain", "chunked", Charset.defaultCharset(), ObjectStorageOperationsSpecification.TEXT.length())
        fileUpload.setContent(file)
        return new NettyCompletedFileUpload(fileUpload, false)
    }

    private void assertThatRequestIsValid(CompletedFileUploadRequest request, String expectedKey) {
        request.with {
            assert key == expectedKey
            assert contentSize.present
            assert contentSize.get() == ObjectStorageOperationsSpecification.TEXT.length() as long
            assert contentType.present
            assert contentType.get() == "text/plain"
            assert inputStream.text == ObjectStorageOperationsSpecification.TEXT
        }
    }
}
