package io.micronaut.objectstorage.aws

import io.micronaut.objectstorage.ObjectStorage
import io.micronaut.objectstorage.ObjectStorageSpecification
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import jakarta.inject.Named
import spock.lang.Requires


@Requires({ System.getenv("AWS_TEST_BUCKET_NAME") })
@MicronautTest
class AwsS3BucketSpec extends ObjectStorageSpecification implements TestPropertyProvider{

    @Named("test-bucket")
    @Inject
    AwsS3Bucket awsS3Bucket

    @Override
    Map<String, String> getProperties() {
        return ["micronaut.object-storage.s3.test-bucket.name": System.getenv('AWS_TEST_BUCKET_NAME')]
    }

    @Override
    ObjectStorage getObjectStorage() {
        return awsS3Bucket
    }
}
