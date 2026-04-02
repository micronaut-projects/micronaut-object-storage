package io.micronaut.objectstorage.request

import spock.lang.Specification

import java.time.Duration

class CreatePresignedUploadRequestSpec extends Specification {

    void "it rejects blank content types"() {
        given:
        CreatePresignedUploadRequest request = new CreatePresignedUploadRequest("avatars/alice.png", Duration.ofMinutes(5))

        when:
        request.contentType = contentType

        then:
        IllegalArgumentException e = thrown()
        e.message == "contentType must not be blank"

        where:
        contentType << ["", "   "]
    }
}
