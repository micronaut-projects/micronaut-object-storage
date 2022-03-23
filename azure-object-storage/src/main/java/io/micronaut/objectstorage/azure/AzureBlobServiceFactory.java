/*
 * Copyright 2021 original authors
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
import com.azure.identity.ClientCertificateCredential;
import com.azure.identity.ClientCertificateCredentialBuilder;
import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.identity.DefaultAzureCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.identity.ManagedIdentityCredential;
import com.azure.identity.ManagedIdentityCredentialBuilder;
import com.azure.identity.UsernamePasswordCredential;
import com.azure.identity.UsernamePasswordCredentialBuilder;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.inject.Singleton;

/**
 * @author Pavol Gressa
 */
@Factory
public class AzureBlobServiceFactory {
    private final BeanContext beanContext;

    public AzureBlobServiceFactory(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    @EachBean(AzureBlobContainerConfiguration.class)
    public BlobServiceClientBuilder blobServiceClientBuilder(AzureBlobContainerConfiguration configuration, @NonNull TokenCredential tokenCredential) {
        return new BlobServiceClientBuilder()
            .endpoint(configuration.getEndpoint())
            .credential(tokenCredential);
    }

    @EachBean(BlobServiceClientBuilder.class)
    public BlobServiceClient blobServiceClient(@NonNull BlobServiceClientBuilder builder) {
        return builder.buildClient();
    }

    @EachBean(BlobServiceClient.class)
    public BlobContainerClient blobContainerClient(@Parameter String name, @NonNull BlobServiceClient serviceClient) {
        final AzureBlobContainerConfiguration configuration = beanContext.getBean(AzureBlobContainerConfiguration.class, Qualifiers.byName(name));
        return serviceClient.getBlobContainerClient(configuration.getName());
    }
}
