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
package io.micronaut.objectstorage.s3compat;

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.objectstorage.aws.AwsS3Configuration;
import io.micronaut.objectstorage.aws.AwsS3Operations;
import io.micronaut.objectstorage.local.LocalStorageOperations;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.Optional;

/**
 * Creates backend adapters for each configured S3-compatible bucket.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.0.0
 */
@Factory
@Internal
public final class S3CompatibleOperationsFactory {

    private final BeanContext beanContext;

    public S3CompatibleOperationsFactory(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    @EachBean(S3CompatibilityConfiguration.class)
    S3CompatibleOperations s3CompatibleOperations(S3CompatibilityConfiguration configuration) {
        String bucket = configuration.getName();
        String storage = configuration.getStorage();
        Optional<LocalStorageOperations> localOperations = findBean(LocalStorageOperations.class, storage);
        Optional<AwsS3Operations> awsOperations = findBean(AwsS3Operations.class, storage);

        return configuration.getStorageProvider()
            .<S3CompatibleOperations>map(provider -> switch (provider) {
                case LOCAL -> localOperations
                    .map(operations -> new LocalStorageS3CompatibleOperations(configuration, operations))
                    .orElseThrow(() -> missingProvider(bucket, storage, provider));
                case AWS -> awsOperations
                    .map(operations -> new AwsS3CompatibleOperations(
                        configuration,
                        beanContext.getBean(AwsS3Configuration.class, Qualifiers.byName(storage)),
                        operations,
                        beanContext.getBean(S3Client.class)
                    ))
                    .orElseThrow(() -> missingProvider(bucket, storage, provider));
            })
            .orElseGet(() -> autoDetect(bucket, storage, localOperations, awsOperations, configuration));
    }

    private S3CompatibleOperations autoDetect(String bucket,
                                              String storage,
                                              Optional<LocalStorageOperations> localOperations,
                                              Optional<AwsS3Operations> awsOperations,
                                              S3CompatibilityConfiguration configuration) {
        if (localOperations.isPresent() && awsOperations.isPresent()) {
            throw new ConfigurationException("S3-compatible bucket [" + bucket + "] is ambiguous because both local and aws storage beans named ["
                + storage + "] exist. Set [" + providerProperty(bucket) + "] to [local] or [aws].");
        }
        if (localOperations.isPresent()) {
            return new LocalStorageS3CompatibleOperations(configuration, localOperations.orElseThrow());
        }
        if (awsOperations.isPresent()) {
            return new AwsS3CompatibleOperations(
                configuration,
                beanContext.getBean(AwsS3Configuration.class, Qualifiers.byName(storage)),
                awsOperations.orElseThrow(),
                beanContext.getBean(S3Client.class)
            );
        }
        throw new ConfigurationException("S3-compatible bucket [" + bucket + "] references storage bean [" + storage
            + "] but no supported local or aws object storage bean with that name exists.");
    }

    private ConfigurationException missingProvider(String bucket,
                                                   String storage,
                                                   S3CompatibilityStorageProvider provider) {
        return new ConfigurationException("S3-compatible bucket [" + bucket + "] requires backing provider [" + provider.name().toLowerCase()
            + "] for storage bean [" + storage + "], but no matching bean was found.");
    }

    private static String providerProperty(String bucket) {
        return S3CompatibilityConfiguration.PREFIX + '.' + bucket + ".storage-provider";
    }

    private <T> Optional<T> findBean(Class<T> beanType, String name) {
        return beanContext.findBean(beanType, Qualifiers.byName(name));
    }
}
