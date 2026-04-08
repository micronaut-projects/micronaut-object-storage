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
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.objectstorage.ObjectStorageEntry;
import io.micronaut.objectstorage.ObjectStorageOperations;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Shared {@link ResourceLoader} support for object storage-backed resources.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.0.0
 */
@Internal
public abstract class AbstractObjectStorageResourceLoader implements ResourceLoader {

    @Nullable
    private final RelativeBase relativeBase;

    protected AbstractObjectStorageResourceLoader() {
        this(null);
    }

    protected AbstractObjectStorageResourceLoader(@Nullable RelativeBase relativeBase) {
        this.relativeBase = relativeBase;
    }

    @Override
    public Optional<InputStream> getResourceAsStream(String path) {
        return resolveResource(path).flatMap(this::openStream);
    }

    @Override
    public Optional<URL> getResource(String path) {
        return resolveResource(path)
            .filter(this::exists)
            .map(this::toUrl);
    }

    @Override
    public Stream<URL> getResources(String path) {
        return getResource(path).stream();
    }

    @Override
    public boolean supportsPrefix(String path) {
        try {
            if (resolveRelative(path).isPresent()) {
                return true;
            }
        } catch (IllegalArgumentException e) {
            return true;
        }
        if (!hasRecognizedPrefix(path)) {
            return false;
        }
        try {
            return resolveAbsolute(path).isPresent();
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    @Override
    public ResourceLoader forBase(String basePath) {
        ResolvedObjectStorageResource resolved = resolveAbsolute(basePath)
            .orElseThrow(() -> new IllegalArgumentException("Unsupported object storage base path: " + basePath));
        return withRelativeBase(new RelativeBase(
            ObjectStorageResourceParser.ensureTrailingSlash(resolved.externalForm()),
            ObjectStorageResourceParser.ensureTrailingSlash(resolved.key()),
            resolved.operations()
        ));
    }

    protected abstract boolean hasRecognizedPrefix(@NonNull String path);

    protected abstract Optional<ResolvedObjectStorageResource> resolveAbsolute(@NonNull String path);

    protected abstract AbstractObjectStorageResourceLoader withRelativeBase(@NonNull RelativeBase relativeBase);

    protected final Optional<ResolvedObjectStorageResource> resolveResource(@NonNull String path) {
        Optional<ResolvedObjectStorageResource> relative = resolveRelative(path);
        return relative.isPresent() ? relative : resolveAbsolute(path);
    }

    protected final Optional<ResolvedObjectStorageResource> resolveRelative(@NonNull String path) {
        if (relativeBase == null || hasRecognizedPrefix(path) || !ObjectStorageResourceParser.isRelativePath(path)) {
            return Optional.empty();
        }
        String normalizedRelativePath = normalizeRelativeLookupPath(path);
        return Optional.of(new ResolvedObjectStorageResource(
            relativeBase.keyPrefix() + normalizedRelativePath,
            relativeBase.externalPrefix() + normalizedRelativePath,
            relativeBase.operations()
        ));
    }

    protected final Optional<InputStream> openStream(@NonNull ResolvedObjectStorageResource resolved) {
        return retrieveEntry(resolved).map(ObjectStorageEntry::getInputStream);
    }

    protected final boolean exists(@NonNull ResolvedObjectStorageResource resolved) {
        return resolved.operations().exists(resolved.key());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    protected final Optional<ObjectStorageEntry<?>> retrieveEntry(@NonNull ResolvedObjectStorageResource resolved) {
        return (Optional) resolved.operations().retrieve(resolved.key());
    }

    private URL toUrl(ResolvedObjectStorageResource resolved) {
        String externalForm = resolved.externalForm();
        int separator = externalForm.indexOf(':');
        String protocol = externalForm.substring(0, separator);
        String remainder = externalForm.substring(separator + 1);
        try {
            return new URL(protocol, null, -1, remainder, new ObjectStorageStreamHandler(resolved));
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid object storage URL: " + externalForm, e);
        }
    }

    private static String normalizeRelativeLookupPath(String path) {
        boolean leadingSlash = path.startsWith("/");
        boolean trailingSlash = path.endsWith("/") && !path.isEmpty();
        String candidate = trimRelativeLookupCandidate(path, leadingSlash, trailingSlash);
        String normalized = normalizeRelativeLookupCandidate(candidate, path);
        return restoreRelativeLookupDecorators(normalized, leadingSlash, trailingSlash);
    }

    private static String trimRelativeLookupCandidate(String path, boolean leadingSlash, boolean trailingSlash) {
        String candidate = leadingSlash ? path.substring(1) : path;
        if (trailingSlash && !candidate.isEmpty()) {
            return candidate.substring(0, candidate.length() - 1);
        }
        return candidate;
    }

    private static String normalizeRelativeLookupCandidate(String candidate, String path) {
        Deque<String> segments = new ArrayDeque<>();
        for (String segment : candidate.split("/", -1)) {
            applyRelativeLookupSegment(segments, segment, path);
        }
        return String.join("/", segments);
    }

    private static void applyRelativeLookupSegment(Deque<String> segments, String segment, String path) {
        if (segment.isEmpty() || ".".equals(segment)) {
            return;
        }
        if ("..".equals(segment)) {
            if (segments.isEmpty()) {
                throw new IllegalArgumentException("Relative resource path escapes the configured base path: " + path);
            }
            segments.removeLast();
            return;
        }
        segments.addLast(segment);
    }

    private static String restoreRelativeLookupDecorators(String normalized, boolean leadingSlash, boolean trailingSlash) {
        if (leadingSlash) {
            normalized = "/" + normalized;
        }
        if (trailingSlash && (normalized.isEmpty() || normalized.charAt(normalized.length() - 1) != '/')) {
            normalized = normalized + '/';
        }
        return normalized;
    }

    /**
     * Relative loader state for {@link #forBase(String)}.
     *
     * @param externalPrefix The rebased URI prefix, always ending in {@code /}
     * @param keyPrefix The rebased object key prefix, always ending in {@code /}
     * @param operations The storage operations to use for relative lookups
     */
    @Internal
    public record RelativeBase(
        @NonNull String externalPrefix,
        @NonNull String keyPrefix,
        @NonNull ObjectStorageOperations<?, ?, ?> operations
    ) {
        public RelativeBase {
            Objects.requireNonNull(externalPrefix, "externalPrefix");
            Objects.requireNonNull(keyPrefix, "keyPrefix");
            Objects.requireNonNull(operations, "operations");
        }
    }

    /**
     * Resolved object storage resource.
     *
     * @param key The object key used against the backing storage
     * @param externalForm The URI exposed via {@link URL#toExternalForm()}
     * @param operations The storage operations for the resolved resource
     */
    @Internal
    public record ResolvedObjectStorageResource(
        @NonNull String key,
        @NonNull String externalForm,
        @NonNull ObjectStorageOperations<?, ?, ?> operations
    ) {
        public ResolvedObjectStorageResource {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(externalForm, "externalForm");
            Objects.requireNonNull(operations, "operations");
        }
    }

    private final class ObjectStorageStreamHandler extends URLStreamHandler {
        private final ResolvedObjectStorageResource resolved;

        private ObjectStorageStreamHandler(ResolvedObjectStorageResource resolved) {
            this.resolved = resolved;
        }

        @Override
        protected URLConnection openConnection(URL url) {
            return new URLConnection(url) {
                @Override
                public void connect() {
                    connected = true;
                }

                @Override
                public InputStream getInputStream() throws IOException {
                    return openStream(resolved)
                        .orElseThrow(() -> new FileNotFoundException(resolved.externalForm()));
                }
            };
        }
    }
}
