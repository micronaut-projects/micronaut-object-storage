/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.objectstorage.aws;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.objectstorage.configuration.AbstractObjectStorageConfiguration;
import io.micronaut.objectstorage.configuration.ObjectStorageConfiguration;

import javax.validation.constraints.Pattern;

import static io.micronaut.objectstorage.aws.AwsS3Configuration.PREFIX;

/**
 * AWS object storage configuration properties.
 *
 * @author Pavol Gressa
 * @since 1.0
 */
@EachProperty(PREFIX)
@Introspected
public class AwsS3Configuration extends AbstractObjectStorageConfiguration {

    /**
     * Configuration Prefix ending.
     */
    public static final String NAME = "aws";

    /**
     * Configuration Prefix.
     */
    public static final String PREFIX = ObjectStorageConfiguration.PREFIX + '.' + NAME;

    /**
     * Bucket name.
     * @see <a href="https://docs.aws.amazon.com/AmazonS3/latest/userguide/bucketnamingrules.html">Bucket Name Requirements</a>.
     */
    @NonNull
    @Pattern(regexp = "(?!(^((2(5[0-5]|[0-4][0-9])|[01]?[0-9]{1,2})\\.){3}(2(5[0-5]|[0-4][0-9])|[01]?[0-9]{1,2})$|^xn--|.+-s3alias$|.*\\.\\.))^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$")
    private String bucket;

    /**
     *
     * @param name Bean Qualifier name
     */
    public AwsS3Configuration(@Parameter String name) {
        super(name);
    }

    /**
     * @return The name of the AWS S3 bucket.
     */
    @NonNull
    public String getBucket() {
        return bucket;
    }

    /**
     * @param bucket The name of the AWS S3 bucket.
     */
    public void setBucket(@NonNull String bucket) {
        this.bucket = bucket;
    }
}
