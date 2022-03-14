package io.micronaut.objectstorage.googlecloud;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.objectstorage.AbstractObjectStorageConfiguration;

/**
 * @author Pavol Gressa
 * @since 2.5
 */
@EachProperty("micronaut.object-storage.google-cloud")
public class GoogleCloudObjectStorageConfiguration extends AbstractObjectStorageConfiguration {

    public GoogleCloudObjectStorageConfiguration(@Parameter String bucketName) {
        super(bucketName);
    }
}
