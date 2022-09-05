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
        Path path = ObjectStorageOperationsSpecification.createTempFile()
        File file = path.toFile()
        FileUpload fileUpload = new DiskFileUpload(file.name, file.name, "text/plain", "chunked", Charset.defaultCharset(), ObjectStorageOperationsSpecification.TEXT.length())
        fileUpload.setContent(file)
        NettyCompletedFileUpload completedFileUpload = new NettyCompletedFileUpload(fileUpload, false)

        when:
        CompletedFileUploadRequest request =
                UploadRequest.fromCompletedFileUpload(completedFileUpload) as CompletedFileUploadRequest

        then:
        request.with {
            assert key == file.name
            assert contentSize.present
            assert contentSize.get() == ObjectStorageOperationsSpecification.TEXT.length() as long
            assert contentType.present
            assert contentType.get() == "text/plain"
            assert inputStream.text == ObjectStorageOperationsSpecification.TEXT
        }

    }

}
