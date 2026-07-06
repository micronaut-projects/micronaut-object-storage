/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.objectstorage.request

import spock.lang.Specification

import java.io.ByteArrayInputStream

class InputStreamUploadRequestSpec extends Specification {

    private static final byte[] BYTES = "streaming upload body".bytes

    void "it can be created with a known content length"() {
        given:
        ByteArrayInputStream inputStream = new ByteArrayInputStream(BYTES)

        when:
        InputStreamUploadRequest request = UploadRequest.fromInputStream(
            inputStream,
            "uploads/body.txt",
            BYTES.length
        ) as InputStreamUploadRequest
        request.metadata = [source: "validated"]

        then:
        request.key == "uploads/body.txt"
        request.contentType == Optional.of("text/plain")
        request.contentSize == Optional.of(BYTES.length as long)
        request.inputStream.is(inputStream)
        request.metadata == [source: "validated"]
    }

    void "it can be created with unknown content type and content length"() {
        given:
        ByteArrayInputStream inputStream = new ByteArrayInputStream(BYTES)

        when:
        InputStreamUploadRequest request = UploadRequest.fromInputStream(
            inputStream,
            "uploads/body",
            null,
            null
        ) as InputStreamUploadRequest

        then:
        request.key == "uploads/body"
        request.contentType.empty
        request.contentSize.empty
        request.inputStream.is(inputStream)
        request.metadata.isEmpty()
    }

    void "it accepts metadata in the full constructor"() {
        given:
        ByteArrayInputStream inputStream = new ByteArrayInputStream(BYTES)

        when:
        InputStreamUploadRequest request = new InputStreamUploadRequest(
            inputStream,
            "uploads/body.json",
            "application/json",
            BYTES.length,
            [owner: "micronaut"]
        )

        then:
        request.contentType == Optional.of("application/json")
        request.contentSize == Optional.of(BYTES.length as long)
        request.metadata == [owner: "micronaut"]
    }

    void "it rejects a negative content length"() {
        when:
        UploadRequest.fromInputStream(new ByteArrayInputStream(BYTES), "uploads/body.txt", -1)

        then:
        IllegalArgumentException e = thrown()
        e.message == "contentLength must not be negative"
    }
}
