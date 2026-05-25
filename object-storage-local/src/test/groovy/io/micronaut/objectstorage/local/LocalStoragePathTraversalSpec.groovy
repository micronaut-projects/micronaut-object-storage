package io.micronaut.objectstorage.local

import io.micronaut.context.ApplicationContext
import io.micronaut.objectstorage.request.ListObjectsRequest
import io.micronaut.objectstorage.request.UploadRequest
import org.opentest4j.TestAbortedException
import spock.lang.Specification

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

class LocalStoragePathTraversalSpec extends Specification {
    def 'path traversal'() {
        given:
        Path tmp = Files.createTempDirectory("micronaut-object-storage")
        ApplicationContext ctx = null
        Path secret = tmp.resolve("secret")
        Files.writeString(secret, "bar")
        Path bucket = tmp.resolve("foo")
        Files.createDirectory(bucket)
        Path pub = bucket.resolve("public")
        Files.writeString(pub, "baz")

        ctx = ApplicationContext.run(["micronaut.object-storage.local.a.path": bucket.toString()])

        when:
        def publicEntry = ctx.getBean(LocalStorageOperations).retrieve("public")
        then:
        publicEntry.isPresent()
        new String(publicEntry.get().inputStream.readAllBytes(), StandardCharsets.UTF_8) == "baz"

        when:
        ctx.getBean(LocalStorageOperations).retrieve("../secret")
        then:
        thrown IllegalArgumentException

        when:
        def listedKeys = ctx.getBean(LocalStorageOperations).listObjects(new ListObjectsRequest(10, '../'))

        then:
        listedKeys.keys.empty
        listedKeys.continuationToken.empty

        cleanup:
        ctx?.close()
        deleteRecursively(tmp)
    }

    def 'symlink escapes are rejected for retrieve copy upload and delete'() {
        given:
        Path tmp = Files.createTempDirectory("micronaut-object-storage")
        ApplicationContext ctx = null
        Path outside = tmp.resolve("outside")
        Files.createDirectory(outside)
        Path secret = outside.resolve("secret")
        Files.writeString(secret, "bar")
        Path bucket = tmp.resolve("bucket")
        Files.createDirectory(bucket)
        Path publicEntry = bucket.resolve("public")
        Files.writeString(publicEntry, "baz")
        assumeSymbolicLinksSupported(tmp)
        Files.createSymbolicLink(bucket.resolve("secret-link"), secret)
        Files.createSymbolicLink(bucket.resolve("outside-link"), outside)

        ctx = ApplicationContext.run(["micronaut.object-storage.local.a.path": bucket.toString()])

        when:
        ctx.getBean(LocalStorageOperations).retrieve("public")

        then:
        noExceptionThrown()

        when:
        ctx.getBean(LocalStorageOperations).retrieve("secret-link")

        then:
        thrown IllegalArgumentException

        when:
        ctx.getBean(LocalStorageOperations).copy("outside-link/secret", "copied")

        then:
        thrown IllegalArgumentException

        when:
        ctx.getBean(LocalStorageOperations).upload(UploadRequest.fromBytes("evil".bytes, "outside-link/created", "text/plain"))

        then:
        thrown IllegalArgumentException
        !Files.exists(outside.resolve("created"))

        when:
        ctx.getBean(LocalStorageOperations).delete("secret-link")

        then:
        thrown IllegalArgumentException
        Files.exists(secret)

        cleanup:
        ctx?.close()
        deleteRecursively(tmp)
    }

    def 'symlinked metadata directory is rejected during upload'() {
        given:
        Path tmp = Files.createTempDirectory("micronaut-object-storage")
        ApplicationContext ctx = null
        Path outside = tmp.resolve("outside")
        Files.createDirectory(outside)
        Path bucket = tmp.resolve("bucket")
        Files.createDirectory(bucket)
        Path metadataTarget = outside.resolve("metadata-target")
        Files.createDirectory(metadataTarget)
        assumeSymbolicLinksSupported(tmp)
        Files.createSymbolicLink(bucket.resolve(LocalStorageOperations.INTERNAL_DIRECTORY), metadataTarget)

        ctx = ApplicationContext.run(["micronaut.object-storage.local.a.path": bucket.toString()])
        UploadRequest request = UploadRequest.fromBytes("evil".bytes, "public", "text/plain")
        request.setMetadata(["owner": "mallory"])

        when:
        ctx.getBean(LocalStorageOperations).upload(request)

        then:
        thrown IllegalArgumentException
        !Files.exists(bucket.resolve("public"))
        !Files.exists(metadataTarget.resolve(LocalStorageOperations.METADATA_DIRECTORY).resolve("public"))

        cleanup:
        ctx?.close()
        deleteRecursively(tmp)
    }

    private static void assumeSymbolicLinksSupported(Path directory) {
        Path target = directory.resolve("symlink-target-probe")
        Path link = directory.resolve("symlink-link-probe")
        try {
            Files.writeString(target, "probe")
            Files.createSymbolicLink(link, target.fileName)
        } catch (UnsupportedOperationException | IOException | SecurityException ignored) {
            throw new TestAbortedException("symbolic links are not supported in this environment")
        } finally {
            try {
                Files.deleteIfExists(link)
                Files.deleteIfExists(target)
            } catch (IOException ignored) {
                // no-op
            }
        }
    }

    private static void deleteRecursively(Path path) {
        if (path == null || !Files.exists(path)) {
            return
        }
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override
            FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file)
                return FileVisitResult.CONTINUE
            }

            @Override
            FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir)
                return FileVisitResult.CONTINUE
            }
        })
    }
}
