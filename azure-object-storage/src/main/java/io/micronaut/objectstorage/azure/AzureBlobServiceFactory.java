/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.objectstorage.azure;

import com.azure.core.credential.TokenCredential;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.qualifiers.Qualifiers;

/**
 * @author Pavol Gressa
 */
@Factory
public class AzureBlobServiceFactory {
    private final BeanContext beanContext;

    public AzureBlobServiceFactory(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    /**
     * @param configuration the configuration
     * @param tokenCredential the token credential
     * @return the {@link BlobServiceClientBuilder}
     */
    @EachBean(AzureBlobContainerConfiguration.class)
    public BlobServiceClientBuilder blobServiceClientBuilder(AzureBlobContainerConfiguration configuration, @NonNull TokenCredential tokenCredential) {
        return new BlobServiceClientBuilder()
            .endpoint(configuration.getEndpoint())
            .credential(tokenCredential);
    }

    /**
     * @param builder the builder
     * @return the {@link BlobServiceClient}
     */
    @EachBean(BlobServiceClientBuilder.class)
    public BlobServiceClient blobServiceClient(@NonNull BlobServiceClientBuilder builder) {
        return builder.buildClient();
    }

    /**
     * @param name The configuration
     * @param serviceClient The service client
     * @return The {@link BlobContainerClient}
     */
    @EachBean(BlobServiceClient.class)
    public BlobContainerClient blobContainerClient(@Parameter String name, @NonNull BlobServiceClient serviceClient) {
        final AzureBlobContainerConfiguration configuration = beanContext.getBean(AzureBlobContainerConfiguration.class, Qualifiers.byName(name));
        return serviceClient.getBlobContainerClient(configuration.getName());
    }
}
