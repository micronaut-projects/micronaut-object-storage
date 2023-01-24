package io.micronaut.objectstorage.request

import io.micronaut.http.server.HttpServerConfiguration
import io.micronaut.http.server.netty.MicronautHttpData
import io.micronaut.http.server.netty.multipart.NettyCompletedFileUpload
import io.micronaut.objectstorage.ObjectStorageOperationsSpecification
import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.multipart.FileUpload
import spock.lang.Specification
import spock.lang.Subject

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Path

import static io.micronaut.objectstorage.ObjectStorageOperationsSpecification.TEXT

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
        HttpServerConfiguration.MultipartConfiguration cfg = new HttpServerConfiguration.MultipartConfiguration()
        cfg.mixed = true
        FileUpload fileUpload = new MicronautHttpData.Factory(cfg, StandardCharsets.UTF_8).createFileUpload(null, "test-file.txt", "test-file.txt", "text/plain", "chunked", Charset.defaultCharset(), TEXT.length())
        fileUpload.addContent(Unpooled.wrappedBuffer(TEXT.bytes), true)
        return new NettyCompletedFileUpload(fileUpload, false)
    }

    private void assertThatRequestIsValid(CompletedFileUploadRequest request, String expectedKey) {
        request.with {
            assert key == expectedKey
            assert contentSize.present
            assert contentSize.get() == TEXT.length() as long
            assert contentType.present
            assert contentType.get() == "text/plain"
            assert inputStream.text == TEXT
        }
    }
}
