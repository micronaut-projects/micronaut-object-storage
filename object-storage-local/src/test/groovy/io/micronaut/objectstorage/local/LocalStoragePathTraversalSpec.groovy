package io.micronaut.objectstorage.local

import io.micronaut.context.ApplicationContext
import io.micronaut.objectstorage.request.ListObjectsRequest
import io.micronaut.objectstorage.request.UploadRequest
import spock.lang.Specification

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
        Path secret = tmp.resolve("secret")
        Files.writeString(secret, "bar")
        Path bucket = tmp.resolve("foo")
        Files.createDirectory(bucket)
        Path pub = bucket.resolve("public")
        Files.writeString(pub, "baz")

        ApplicationContext ctx = ApplicationContext.run(["micronaut.object-storage.local.a.path": bucket.toString()])

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
        ctx.close()
        Files.walkFileTree(tmp, new SimpleFileVisitor<Path>() {
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

    def 'symlink escapes are rejected for retrieve copy upload and delete'() {
        given:
        Path tmp = Files.createTempDirectory("micronaut-object-storage")
        Path outside = tmp.resolve("outside")
        Files.createDirectory(outside)
        Path secret = outside.resolve("secret")
        Files.writeString(secret, "bar")
        Path bucket = tmp.resolve("bucket")
        Files.createDirectory(bucket)
        Path publicEntry = bucket.resolve("public")
        Files.writeString(publicEntry, "baz")
        Files.createSymbolicLink(bucket.resolve("secret-link"), secret)
        Files.createSymbolicLink(bucket.resolve("outside-link"), outside)

        ApplicationContext ctx = ApplicationContext.run(["micronaut.object-storage.local.a.path": bucket.toString()])

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
        ctx.close()
        Files.walkFileTree(tmp, new SimpleFileVisitor<Path>() {
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

    def 'symlinked metadata directory is rejected during upload'() {
        given:
        Path tmp = Files.createTempDirectory("micronaut-object-storage")
        Path outside = tmp.resolve("outside")
        Files.createDirectory(outside)
        Path bucket = tmp.resolve("bucket")
        Files.createDirectory(bucket)
        Path metadataTarget = outside.resolve("metadata-target")
        Files.createDirectory(metadataTarget)
        Files.createSymbolicLink(bucket.resolve(LocalStorageOperations.METADATA_DIRECTORY), metadataTarget)

        ApplicationContext ctx = ApplicationContext.run(["micronaut.object-storage.local.a.path": bucket.toString()])
        UploadRequest request = UploadRequest.fromBytes("evil".bytes, "public", "text/plain")
        request.setMetadata(["owner": "mallory"])

        when:
        ctx.getBean(LocalStorageOperations).upload(request)

        then:
        thrown IllegalArgumentException
        !Files.exists(bucket.resolve("public"))
        !Files.exists(metadataTarget.resolve("public"))

        cleanup:
        ctx.close()
        Files.walkFileTree(tmp, new SimpleFileVisitor<Path>() {
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
