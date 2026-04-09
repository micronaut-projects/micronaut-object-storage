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
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.HashSet;
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

    static OutputStream newOutputStreamNoFollow(Path file, boolean supportsPosixPermissions) throws IOException {
        if (supportsPosixPermissions) {
            try {
                Set<OpenOption> createOptions = new HashSet<>();
                createOptions.add(StandardOpenOption.WRITE);
                createOptions.add(StandardOpenOption.CREATE_NEW);
                createOptions.add(LinkOption.NOFOLLOW_LINKS);
                return Channels.newOutputStream(FileChannel.open(file, createOptions, FILE_PERMISSIONS_ATTRIBUTE));
            } catch (FileAlreadyExistsException ignored) {
                Files.setPosixFilePermissions(file, FILE_PERMISSIONS);
                return Channels.newOutputStream(FileChannel.open(
                    file,
                    Set.of(StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)
                ));
            }
        }
        return Channels.newOutputStream(Files.newByteChannel(file, Set.of(
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
            LinkOption.NOFOLLOW_LINKS
        )));
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
        if (normalizedRelativePath.getNameCount() > 0
            && LocalStorageOperations.METADATA_DIRECTORY.equalsIgnoreCase(normalizedRelativePath.getName(0).toString())) {
            throw new IllegalArgumentException("Key uses the reserved " + LocalStorageOperations.METADATA_DIRECTORY + " namespace: " + key);
        }
    }

    private static void rejectSymbolicLink(Path path) {
        if (Files.isSymbolicLink(path)) {
            throw new IllegalArgumentException("Path contains symbolic links");
        }
    }

    private static void rejectSymbolicLinks(Path parent, Path file) {
        Path current = parent;
        for (Path segment : parent.relativize(file)) {
            current = current.resolve(segment);
            rejectSymbolicLink(current);
        }
    }
}
