package io.micronaut.objectstorage.request

import io.micronaut.http.server.HttpServerConfiguration
import io.micronaut.http.server.netty.MicronautHttpData
import io.micronaut.http.server.netty.multipart.NettyStreamingFileUpload
import io.micronaut.objectstorage.ObjectStorageOperationsSpecification
import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.multipart.FileUpload
import spock.lang.Specification
import spock.lang.Subject

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Path

import static io.micronaut.objectstorage.ObjectStorageOperationsSpecification.TEXT

@Subject(StreamingFileUploadRequest)
class StreamingFileUploadSpec extends Specification {

    void "it can be created from a streaming file upload"() {
        given:
        NettyStreamingFileUpload streamingFileUpload = createStreamingFileUpload()

        when:
        StreamingFileUploadRequest request =
                UploadRequest.fromStreamingFileUpload(streamingFileUpload) as StreamingFileUploadRequest

        then:
        assertThatRequestIsValid(request, streamingFileUpload.name)
    }

    void "it can be created from a streaming file upload and key"() {
        given:
        NettyStreamingFileUpload streamingFileUpload = createStreamingFileUpload()
        String key = "path/tp=o/file.txt"

        when:
        StreamingFileUploadRequest request =
                UploadRequest.fromStreamingFileUpload(streamingFileUpload, key) as StreamingFileUploadRequest

        then:
        assertThatRequestIsValid(request, key)

    }

    private NettyStreamingFileUpload createStreamingFileUpload() {
        HttpServerConfiguration.MultipartConfiguration cfg = new HttpServerConfiguration.MultipartConfiguration()
        cfg.mixed = true
        FileUpload fileUpload = new MicronautHttpData.Factory(cfg, StandardCharsets.UTF_8).createFileUpload(null, "test-file.txt", "test-file.txt", "text/plain", "chunked", Charset.defaultCharset(), TEXT.length())
        
        return new NettyStreamingFileUpload.Factory(cfg, null).create(fileUpload, null)
        
    }

    private void assertThatRequestIsValid(StreamingFileUploadRequest request, String expectedKey) {
        request.with {
            assert key == expectedKey
            assert contentType.present
            assert contentType.get() == "text/plain"
            
        }
    }
}
