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

import com.azure.identity.EnvironmentCredential;
import com.azure.identity.EnvironmentCredentialBuilder;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

/**
 * TODO: migrate upstream to micronaut-azure.
 * https://github.com/micronaut-projects/micronaut-azure/issues/366
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 1.0.0
 */
@Factory
@BootstrapContextCompatible
public class EnvironmentCredentialFactory {

    /**
     * @return the environment credential builder.
     */
    //TODO create custom condition
    @Requires(property = "azure.client.id")
    @Requires(property = "azure.client.secret")
    @Requires(property = "azure.tenant.id")
    @Singleton
    @BootstrapContextCompatible
    public EnvironmentCredentialBuilder environmentCredentialBuilder() {
        return new EnvironmentCredentialBuilder();
    }

    /**
     * @param builder the environment credential builder.
     * @return the environment builder.
     */
    @Requires(bean = EnvironmentCredentialBuilder.class)
    @Singleton
    @BootstrapContextCompatible
    public EnvironmentCredential environmentCredential(final EnvironmentCredentialBuilder builder) {
        return builder.build();
    }
}
