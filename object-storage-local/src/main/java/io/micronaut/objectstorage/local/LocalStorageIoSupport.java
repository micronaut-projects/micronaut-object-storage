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
package io.micronaut.objectstorage.local;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.Set;

final class LocalStorageIoSupport {

    private static final Set<PosixFilePermission> DIRECTORY_PERMISSIONS = EnumSet.of(
        PosixFilePermission.OWNER_READ,
        PosixFilePermission.OWNER_WRITE,
        PosixFilePermission.OWNER_EXECUTE
    );
    private static final Set<PosixFilePermission> FILE_PERMISSIONS = EnumSet.of(
        PosixFilePermission.OWNER_READ,
        PosixFilePermission.OWNER_WRITE
    );
    private static final FileAttribute<Set<PosixFilePermission>> DIRECTORY_PERMISSIONS_ATTRIBUTE =
        PosixFilePermissions.asFileAttribute(DIRECTORY_PERMISSIONS);
    private static final FileAttribute<Set<PosixFilePermission>> FILE_PERMISSIONS_ATTRIBUTE =
        PosixFilePermissions.asFileAttribute(FILE_PERMISSIONS);

    private LocalStorageIoSupport() {
    }

    static boolean mkdirs(Path rootPath, Path path, boolean supportsPosixPermissions) {
        try {
            if (!supportsPosixPermissions) {
                Files.createDirectories(path);
                return true;
            }
            Path normalizedRoot = rootPath.normalize();
            Path normalizedPath = path.normalize();
            Files.createDirectories(normalizedRoot, DIRECTORY_PERMISSIONS_ATTRIBUTE);
            Files.setPosixFilePermissions(normalizedRoot, DIRECTORY_PERMISSIONS);
            Path current = normalizedRoot;
            for (Path part : normalizedRoot.relativize(normalizedPath)) {
                current = current.resolve(part);
                createOwnerOnlyDirectory(current);
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    static Path createTempFile(Path directory, String prefix, String suffix, boolean supportsPosixPermissions) throws IOException {
        if (supportsPosixPermissions) {
            return Files.createTempFile(directory, prefix, suffix, FILE_PERMISSIONS_ATTRIBUTE);
        }
        return Files.createTempFile(directory, prefix, suffix);
    }

    static Path writeAndReplace(Path file,
                                Path temporaryDirectory,
                                String temporaryPrefix,
                                String temporarySuffix,
                                boolean supportsPosixPermissions,
                                OutputStreamWriter writer) throws IOException {
        Path temporaryFile = createTempFile(temporaryDirectory, temporaryPrefix, temporarySuffix, supportsPosixPermissions);
        try {
            try (FileChannel temporaryChannel = FileChannel.open(
                temporaryFile,
                Set.of(StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)
            );
                 OutputStream temporaryOut = Channels.newOutputStream(temporaryChannel)) {
                writer.write(temporaryOut);
                temporaryOut.flush();
                temporaryChannel.force(true);
            }
            moveReplacing(temporaryFile, file);
            forceDirectories(file.getParent(), temporaryDirectory);
            return file;
        } catch (IOException e) {
            deleteTemporaryFile(temporaryFile, e);
            throw e;
        } catch (RuntimeException e) {
            deleteTemporaryFile(temporaryFile, e);
            throw e;
        } catch (Error e) {
            deleteTemporaryFile(temporaryFile, e);
            throw e;
        }
    }

    static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            try {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException fallbackFailure) {
                fallbackFailure.addSuppressed(e);
                throw fallbackFailure;
            }
        }
    }

    static InputStream newInputStreamNoFollow(Path path) throws IOException {
        return Channels.newInputStream(Files.newByteChannel(path, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)));
    }

    static Path resolveSafe(Path parent, String key) {
        Path normalizedParent = parent.normalize();
        rejectSymbolicLink(normalizedParent);
        Path file = normalizedParent.resolve(key).normalize();
        if (!file.startsWith(normalizedParent)) {
            throw new IllegalArgumentException("Path lies outside the configured bucket");
        }
        validateKey(normalizedParent.relativize(file), key);
        rejectSymbolicLinks(normalizedParent, file);
        return file;
    }

    static Path resolveBucketPath(Path rootDirectory, String name) {
        rejectSymbolicLink(rootDirectory);
        Path path = rootDirectory.resolve(name).normalize();
        if (!path.getParent().equals(rootDirectory) ||
            !path.getFileName().toString().equals(name)) {
            throw new IllegalArgumentException("Bucket name must not contain filesystem special characters");
        }
        String reservedNamespace = LocalStorageOperations.reservedLocalStorageNamespace(name).orElse(null);
        if (reservedNamespace != null) {
            throw new IllegalArgumentException("Bucket name uses the reserved " + reservedNamespace + " namespace: " + name);
        }
        rejectSymbolicLinks(rootDirectory, path);
        return path;
    }

    private static void createOwnerOnlyDirectory(Path directory) throws IOException {
        try {
            Files.createDirectory(directory, DIRECTORY_PERMISSIONS_ATTRIBUTE);
        } catch (FileAlreadyExistsException ignored) {
            if (!Files.isDirectory(directory)) {
                throw ignored;
            }
        }
        Files.setPosixFilePermissions(directory, DIRECTORY_PERMISSIONS);
    }

    private static void validateKey(Path normalizedRelativePath, String key) {
        if (normalizedRelativePath.getNameCount() > 0) {
            String reservedNamespace = LocalStorageOperations.reservedLocalStorageNamespace(normalizedRelativePath.getName(0).toString()).orElse(null);
            if (reservedNamespace != null) {
                throw new IllegalArgumentException("Key uses the reserved " + reservedNamespace + " namespace: " + key);
            }
        }
    }

    private static void rejectSymbolicLink(Path path) {
        if (Files.isSymbolicLink(path)) {
            throw new IllegalArgumentException("Path contains symbolic links");
        }
    }

    static void rejectSymbolicLinks(Path parent, Path file) {
        Path current = parent;
        for (Path segment : parent.relativize(file)) {
            current = current.resolve(segment);
            rejectSymbolicLink(current);
        }
    }

    private static void deleteTemporaryFile(Path temporaryFile, Throwable failure) {
        try {
            Files.deleteIfExists(temporaryFile);
        } catch (IOException | RuntimeException e) {
            failure.addSuppressed(e);
        }
    }

    private static void forceDirectories(Path first, Path second) {
        forceDirectory(first);
        if (second != null && !second.equals(first)) {
            forceDirectory(second);
        }
    }

    private static void forceDirectory(Path directory) {
        if (directory == null) {
            return;
        }
        try (FileChannel directoryChannel = FileChannel.open(directory, StandardOpenOption.READ)) {
            directoryChannel.force(true);
        } catch (IOException | SecurityException | UnsupportedOperationException ignored) {
            // Directory fsync is not portable through Java NIO, so keep it best-effort.
        }
    }

    @FunctionalInterface
    interface OutputStreamWriter {
        void write(OutputStream outputStream) throws IOException;
    }
}
