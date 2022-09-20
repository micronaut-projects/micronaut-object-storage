package io.micronaut.objectstorage

import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.server.types.files.StreamedFile
import io.micronaut.http.server.types.files.SystemFile
import spock.lang.Specification

import static io.micronaut.objectstorage.ObjectStorageOperationsSpecification.createTempFile

class ObjectStorageEntrySpec extends Specification {

    void "it can convert to StreamedFile"() {
        given:
        ObjectStorageEntry<?> entry = new DummyObjectStorageEntry()

        when:
        StreamedFile streamedFile = entry.toStreamedFile()

        then:
        streamedFile.inputStream.text == ObjectStorageOperationsSpecification.TEXT
        streamedFile.mediaType == MediaType.TEXT_PLAIN_TYPE

        when:
        HttpResponse response = HttpResponse.ok()
        streamedFile.process(response)

        then:
        response.header(HttpHeaders.CONTENT_DISPOSITION).startsWith("attachment; filename=\"${entry.key}\"")
    }

    void "it can convert to SystemFile"() {
        given:
        ObjectStorageEntry<?> entry = new DummyObjectStorageEntry()

        when:
        SystemFile systemFile = entry.toSystemFile()

        then:
        systemFile.file.text == ObjectStorageOperationsSpecification.TEXT
        systemFile.mediaType == MediaType.TEXT_PLAIN_TYPE

        when:
        HttpResponse response = HttpResponse.ok()
        systemFile.process(response)

        then:
        response.header(HttpHeaders.CONTENT_DISPOSITION).startsWith("attachment; filename=\"${entry.key}\"")
    }

    static class DummyObjectStorageEntry implements ObjectStorageEntry {

        private File file = createTempFile().toFile()

        @Override
        String getKey() {
            return file.name
        }

        @Override
        InputStream getInputStream() {
            return file.newInputStream()
        }

        @Override
        Object getNativeEntry() {
            return null
        }

        @Override
        Optional<String> getContentType() {
            return Optional.of(MediaType.TEXT_PLAIN)
        }
    }

}
