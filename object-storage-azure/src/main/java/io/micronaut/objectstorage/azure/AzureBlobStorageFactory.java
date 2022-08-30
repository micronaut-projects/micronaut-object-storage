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
package io.micronaut.objectstorage.azure;

import com.azure.core.credential.TokenCredential;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.common.StorageSharedKeyCredential;
import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.qualifiers.Qualifiers;

/**
 * <p>Creates beans of the following types:</p>
 * <ul>
 *     <li>For each {@link AzureBlobStorageConfiguration}, creates a {@link BlobServiceClientBuilder}.</li>
 *     <li>For each {@link BlobServiceClientBuilder}, creates a {@link BlobServiceClient}</li>
 *     <li>For each {@link BlobServiceClient}, creates a {@link BlobContainerClient}</li>
 * </ul>
 *
 * @author Pavol Gressa
 * @since 1.0
 */
@Factory
public class AzureBlobStorageFactory {

    private final BeanContext beanContext;

    public AzureBlobStorageFactory(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    /**
     * @param configuration   the configuration
     * @param tokenCredential the token credential
     * @return the {@link BlobServiceClientBuilder}
     */
    @EachBean(AzureBlobStorageConfiguration.class)
    @Requires(bean = TokenCredential.class)
    @Requires(missingBeans = StorageSharedKeyCredential.class)
    public BlobServiceClientBuilder blobServiceClientBuilderWithTokenCredential(AzureBlobStorageConfiguration configuration,
                                                                                @NonNull TokenCredential tokenCredential) {
        return new BlobServiceClientBuilder()
            .endpoint(configuration.getEndpoint())
            .credential(tokenCredential);
    }

    /**
     * @param configuration       the configuration
     * @param sharedKeyCredential the shared key credential
     * @return the {@link BlobServiceClientBuilder}
     */
    @EachBean(AzureBlobStorageConfiguration.class)
    @Requires(bean = StorageSharedKeyCredential.class)
    public BlobServiceClientBuilder blobServiceClientBuilderWithSharedKeyCredential(AzureBlobStorageConfiguration configuration,
                                                                                    @NonNull StorageSharedKeyCredential sharedKeyCredential) {
        return new BlobServiceClientBuilder()
            .endpoint(configuration.getEndpoint())
            .credential(sharedKeyCredential);
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
     * @param name          The configuration
     * @param serviceClient The service client
     * @return The {@link BlobContainerClient}
     */
    @EachBean(BlobServiceClient.class)
    public BlobContainerClient blobContainerClient(@Parameter String name,
                                                   @NonNull BlobServiceClient serviceClient) {
        final AzureBlobStorageConfiguration configuration = beanContext.getBean(AzureBlobStorageConfiguration.class, Qualifiers.byName(name));
        return serviceClient.getBlobContainerClient(configuration.getName());
    }
}
