package io.micronaut.objectstorage

import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.nio.file.Files


abstract class ObjectStorageSpecification extends Specification {

    def "it can upload, get and delete object from file"() {
        given:
        def tempFilePath = Files.createTempFile("test-file", "txt")
        def tempFileName = tempFilePath.getFileName().toString()
        Files.writeString(tempFilePath, "micronaut", StandardCharsets.UTF_8);

        when: 'put file to object storage'
        UploadRequest uploadRequest = UploadRequest.fromFile(tempFilePath)
        def uploadResponse = getObjectStorage().put(uploadRequest)

        then:
        uploadResponse
        uploadResponse.ETag

        when: 'get file based on path'
        Optional<ObjectStorageEntry> objectStorageEntry = getObjectStorage().get(tempFileName)

        then:
        objectStorageEntry.isPresent()
        objectStorageEntry.get().key == tempFileName

        when: 'the file has same content'
        String text = new BufferedReader(
                new InputStreamReader(objectStorageEntry.get().inputStream, StandardCharsets.UTF_8))
                .text

        then:
        text
        text == "micronaut"

        when: 'delete the file on object storage'
        getObjectStorage().delete(tempFileName)

        then:
        noExceptionThrown()
    }

    abstract ObjectStorage getObjectStorage()
}
