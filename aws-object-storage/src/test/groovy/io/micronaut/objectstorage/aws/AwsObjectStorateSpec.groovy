package io.micronaut.objectstorage.aws

import io.micronaut.objectstorage.UploadRequest
import software.amazon.awssdk.services.s3.S3Client
import spock.lang.Specification


class AwsObjectStorateSpec extends Specification{

    def "it can upload from InputStream"(){
        given:
        def s3client = Mock(S3Client)
        def objectStorage = new AwsS3Bucket("dummy-bucket", s3client)

        when:
        objectStorage.put(UploadRequest.fromFile())




    }

    def "it can upload from File"(){
        given:
        def s3client = Mock(S3Client)
        def objectStorage = new AwsS3Bucket("dummy-bucket", s3client)

        when:
        objectStorage.put(UploadRequest.fromFile())




    }



}
