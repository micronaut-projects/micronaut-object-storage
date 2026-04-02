package io.micronaut.objectstorage.request

import io.micronaut.http.MediaType
import io.micronaut.http.multipart.FormFieldMetadata
import io.micronaut.http.multipart.RawFormField
import io.micronaut.http.multipart.StreamingFileUpload
import io.micronaut.http.body.ByteBody
import io.micronaut.http.body.CloseableAvailableByteBody
import spock.lang.Specification

import java.io.ByteArrayInputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

class StreamingFileUploadRequestSpec extends Specification {

    private static final byte[] BYTES = "streaming upload body".bytes

    void "it can be created from a streaming file upload and key"() {
        given:
        StreamingFileUpload streamingFileUpload = new StreamingFileUpload(
            new RawFormField(
                new FormFieldMetadata("upload", "resume.txt", MediaType.TEXT_PLAIN_TYPE),
                new TestCloseableByteBody(BYTES)
            ),
            ({ Runnable runnable -> runnable.run() } as Executor)
        )

        when:
        StreamingFileUploadRequest request = UploadRequest.fromStreamingFileUpload(streamingFileUpload, "profiles/resume.txt") as StreamingFileUploadRequest
        request.metadata = [owner: "micronaut"]

        then:
        request.key == "profiles/resume.txt"
        request.contentType.present
        request.contentType.get() == MediaType.TEXT_PLAIN
        request.contentSize.present
        request.contentSize.get() == BYTES.length
        request.inputStream.bytes == BYTES
        request.metadata == [owner: "micronaut"]
    }

    private static final class TestCloseableByteBody implements CloseableAvailableByteBody {
        private final byte[] bytes

        TestCloseableByteBody(byte[] bytes) {
            this.bytes = bytes
        }

        @Override
        CloseableAvailableByteBody split() {
            return new TestCloseableByteBody(bytes)
        }

        @Override
        long length() {
            return bytes.length
        }

        @Override
        byte[] toByteArray() {
            return bytes
        }

        @Override
        OptionalLong expectedLength() {
            return OptionalLong.of(bytes.length)
        }

        @Override
        ByteArrayInputStream toInputStream() {
            return new ByteArrayInputStream(bytes)
        }

        @Override
        CompletableFuture<? extends CloseableAvailableByteBody> buffer() {
            return CompletableFuture.completedFuture(this)
        }

        @Override
        CloseableAvailableByteBody move() {
            return new TestCloseableByteBody(bytes)
        }

        @Override
        CloseableAvailableByteBody split(ByteBody.SplitBackpressureMode backpressureMode) {
            return split()
        }

        @Override
        org.reactivestreams.Publisher<byte[]> toByteArrayPublisher() {
            return { subscriber ->
                subscriber.onNext(bytes)
                subscriber.onComplete()
            }
        }

        @Override
        void close() {
        }
    }
}
