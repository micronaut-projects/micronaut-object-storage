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

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.objectstorage.configuration.AbstractObjectStorageModuleConfiguration;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * S3-compatible module configuration.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.0.0
 */
@ConfigurationProperties(S3CompatibilityConfiguration.PREFIX)
public class S3CompatibilityModuleConfiguration extends AbstractObjectStorageModuleConfiguration {

    private S3CompatibilityAuthMode authMode = S3CompatibilityAuthMode.NONE;

    @Nullable
    private String accessKeyId;

    @Nullable
    private String secretAccessKey;

    @NonNull
    private String region = "us-east-1";

    /**
     * Whether to enable or disable the whole S3-compatible module.
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * @return The request authentication mode for the S3-compatible HTTP surface.
     */
    @NonNull
    public S3CompatibilityAuthMode getAuthMode() {
        return authMode;
    }

    /**
     * @param authMode The request authentication mode.
     */
    public void setAuthMode(@NonNull S3CompatibilityAuthMode authMode) {
        this.authMode = authMode;
    }

    /**
     * @return The access key id used for SigV4 verification.
     */
    @NonNull
    public Optional<String> getAccessKeyId() {
        return Optional.ofNullable(accessKeyId);
    }

    /**
     * @param accessKeyId The access key id used for SigV4 verification.
     */
    public void setAccessKeyId(@Nullable String accessKeyId) {
        this.accessKeyId = emptyToNull(accessKeyId);
    }

    /**
     * @return The secret access key used for SigV4 verification.
     */
    @NonNull
    public Optional<String> getSecretAccessKey() {
        return Optional.ofNullable(secretAccessKey);
    }

    /**
     * @param secretAccessKey The secret access key used for SigV4 verification.
     */
    public void setSecretAccessKey(@Nullable String secretAccessKey) {
        this.secretAccessKey = emptyToNull(secretAccessKey);
    }

    /**
     * @return The SigV4 region expected from incoming requests.
     */
    @NonNull
    public String getRegion() {
        return region;
    }

    /**
     * @param region The SigV4 region expected from incoming requests.
     */
    public void setRegion(@NonNull String region) {
        this.region = region;
    }

    @Nullable
    private static String emptyToNull(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
