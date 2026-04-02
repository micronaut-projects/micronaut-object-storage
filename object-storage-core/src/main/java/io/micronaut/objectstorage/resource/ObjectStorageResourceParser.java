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
package io.micronaut.objectstorage.resource;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * Parser utilities for object storage resource URIs.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.1.0
 */
@Internal
public final class ObjectStorageResourceParser {

    public static final String S3_SCHEME = "s3";
    public static final String GS_SCHEME = "gs";
    public static final String AZB_SCHEME = "azb";
    public static final String OS_SCHEME = "os";
    private static final String EXPECTED_PREFIX = "Expected ";
    private static final String OBJECT_KEY_MUST_NOT_BE_EMPTY = "Object key must not be empty";
    private static final String ORACLE_CLOUD_URI_FORMAT = "os:<region>:<namespace>://<bucket>/<key>";

    private ObjectStorageResourceParser() {
    }

    public static boolean isRelativePath(@NonNull String path) {
        return path.indexOf(':') < 0;
    }

    public static String ensureTrailingSlash(@NonNull String value) {
        return value.endsWith("/") ? value : value + '/';
    }

    public static Optional<NamedStorageUri> parseNamedStorageUri(@NonNull String path, @NonNull String storageName) {
        String prefix = storageName + ':';
        if (!path.startsWith(prefix)) {
            return Optional.empty();
        }
        if (!path.startsWith(storageName + "://")) {
            throw invalid(path, EXPECTED_PREFIX + storageName + "://<key>");
        }
        String key = path.substring(storageName.length() + 3);
        if (key.isEmpty()) {
            throw invalid(path, OBJECT_KEY_MUST_NOT_BE_EMPTY);
        }
        return Optional.of(new NamedStorageUri(storageName, key));
    }

    public static Optional<BucketStorageUri> parseBucketStorageUri(@NonNull String path, @NonNull String scheme) {
        String prefix = scheme + ':';
        if (!path.startsWith(prefix)) {
            return Optional.empty();
        }
        if (!path.startsWith(scheme + "://")) {
            throw invalid(path, EXPECTED_PREFIX + scheme + "://<bucket>/<key>");
        }
        String remainder = path.substring(scheme.length() + 3);
        int separator = remainder.indexOf('/');
        if (separator < 0) {
            throw invalid(path, EXPECTED_PREFIX + scheme + "://<bucket>/<key>");
        }
        String bucket = remainder.substring(0, separator);
        String key = remainder.substring(separator + 1);
        if (bucket.isEmpty()) {
            throw invalid(path, "Bucket name must not be empty");
        }
        if (key.isEmpty()) {
            throw invalid(path, OBJECT_KEY_MUST_NOT_BE_EMPTY);
        }
        return Optional.of(new BucketStorageUri(scheme, bucket, key));
    }

    public static Optional<AzureBlobStorageUri> parseAzureBlobStorageUri(@NonNull String path) {
        if (!path.startsWith(AZB_SCHEME + ':')) {
            return Optional.empty();
        }
        String remainder = path.substring(AZB_SCHEME.length() + 1);
        int authoritySeparator = remainder.indexOf("://");
        if (authoritySeparator < 0) {
            throw invalid(path, EXPECTED_PREFIX + "azb:<account>://<container>/<key>");
        }
        String account = remainder.substring(0, authoritySeparator);
        if (account.isEmpty()) {
            throw invalid(path, "Azure storage account must not be empty");
        }
        String containerAndKey = remainder.substring(authoritySeparator + 3);
        int keySeparator = containerAndKey.indexOf('/');
        if (keySeparator < 0) {
            throw invalid(path, EXPECTED_PREFIX + "azb:<account>://<container>/<key>");
        }
        String container = containerAndKey.substring(0, keySeparator);
        String key = containerAndKey.substring(keySeparator + 1);
        if (container.isEmpty()) {
            throw invalid(path, "Azure container must not be empty");
        }
        if (key.isEmpty()) {
            throw invalid(path, OBJECT_KEY_MUST_NOT_BE_EMPTY);
        }
        return Optional.of(new AzureBlobStorageUri(account, container, key));
    }

    public static Optional<OracleCloudStorageUri> parseOracleCloudStorageUri(@NonNull String path) {
        if (!path.startsWith(OS_SCHEME + ':')) {
            return Optional.empty();
        }
        String remainder = path.substring(OS_SCHEME.length() + 1);
        int authoritySeparator = remainder.indexOf("://");
        if (authoritySeparator < 0) {
            throw invalid(path, EXPECTED_PREFIX + ORACLE_CLOUD_URI_FORMAT);
        }
        String[] authorityParts = remainder.substring(0, authoritySeparator).split(":", 2);
        if (authorityParts.length != 2 || authorityParts[0].isEmpty() || authorityParts[1].isEmpty()) {
            throw invalid(path, EXPECTED_PREFIX + ORACLE_CLOUD_URI_FORMAT);
        }
        String bucketAndKey = remainder.substring(authoritySeparator + 3);
        int keySeparator = bucketAndKey.indexOf('/');
        if (keySeparator < 0) {
            throw invalid(path, EXPECTED_PREFIX + ORACLE_CLOUD_URI_FORMAT);
        }
        String bucket = bucketAndKey.substring(0, keySeparator);
        String key = bucketAndKey.substring(keySeparator + 1);
        if (bucket.isEmpty()) {
            throw invalid(path, "Oracle Cloud bucket must not be empty");
        }
        if (key.isEmpty()) {
            throw invalid(path, OBJECT_KEY_MUST_NOT_BE_EMPTY);
        }
        return Optional.of(new OracleCloudStorageUri(authorityParts[0], authorityParts[1], bucket, key));
    }

    private static IllegalArgumentException invalid(String path, String message) {
        return new IllegalArgumentException("Invalid object storage URI [" + path + "]: " + message);
    }

    @Internal
    public record NamedStorageUri(@NonNull String storageName, @NonNull String key) {
    }

    @Internal
    public record BucketStorageUri(@NonNull String scheme, @NonNull String bucket, @NonNull String key) {
    }

    @Internal
    public record AzureBlobStorageUri(@NonNull String account, @NonNull String container, @NonNull String key) {
    }

    @Internal
    public record OracleCloudStorageUri(@NonNull String region, @NonNull String namespace, @NonNull String bucket, @NonNull String key) {
    }
}
