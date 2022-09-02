package io.micronaut.objectstorage.request

import io.micronaut.objectstorage.ObjectStorageOperationsSpecification
import spock.lang.Specification
import spock.lang.Subject

import java.nio.file.Path

@Subject(FileUploadRequest)
class FileUploadRequestSpec extends Specification {

    void "it can be created from a path"() {
        given:
        Path path = ObjectStorageOperationsSpecification.createTempFile()
        String key = path.toFile().name

        when:
        FileUploadRequest request = UploadRequest.fromPath(path) as FileUploadRequest

        then:
        assertThatRequestIsValid(request, path, key)
    }

    void "it can be created from a path with a prefix"() {
        given:
        Path path = ObjectStorageOperationsSpecification.createTempFile()
        String prefix = "some-dir"
        String key = "${prefix}/${path.toFile().name}"

        when:
        FileUploadRequest request = UploadRequest.fromPath(path, prefix) as FileUploadRequest

        then:
        assertThatRequestIsValid(request, path, key)
    }

    private void assertThatRequestIsValid(FileUploadRequest request, Path expectedPath, String expectedKey) {
        request.with {
            assert key == expectedKey
            assert absolutePath == expectedPath.toFile().absolutePath
            assert contentSize.present
            assert contentSize.get() == ObjectStorageOperationsSpecification.TEXT.length() as long
            assert contentType.present
            assert contentType.get() == "text/plain"
            assert file == expectedPath.toFile()
            assert inputStream.text == ObjectStorageOperationsSpecification.TEXT
            assert path == expectedPath
        }
    }

}
