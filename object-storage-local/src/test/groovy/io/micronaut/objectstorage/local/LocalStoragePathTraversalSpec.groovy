package io.micronaut.objectstorage.local

import io.micronaut.context.ApplicationContext
import io.micronaut.objectstorage.multipart.AbortMultipartUploadRequest
import io.micronaut.objectstorage.multipart.CompleteMultipartUploadRequest
import io.micronaut.objectstorage.multipart.CreateMultipartUploadRequest
import io.micronaut.objectstorage.multipart.ListMultipartPartsRequest
import io.micronaut.objectstorage.multipart.UploadPartRequest
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

    def 'symlinked root metadata directory is rejected during upload'() {
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
        Files.createSymbolicLink(tmp.resolve(LocalStorageOperations.INTERNAL_DIRECTORY), metadataTarget)

        ctx = ApplicationContext.run(["micronaut.object-storage.local.a.path": bucket.toString()])
        UploadRequest request = UploadRequest.fromBytes("evil".bytes, "public", "text/plain")
        request.setMetadata(["owner": "mallory"])

        when:
        ctx.getBean(LocalStorageOperations).upload(request)

        then:
        thrown IllegalArgumentException
        !Files.exists(bucket.resolve("public"))
        !Files.exists(metadataTarget.resolve(LocalStorageLayout.METADATA_DIRECTORY)
            .resolve(LocalStorageOperations.OBJECTS_DIRECTORY)
            .resolve("bucket")
            .resolve("public"))

        cleanup:
        ctx?.close()
        deleteRecursively(tmp)
    }

    def 'symlinked root multipart directory is rejected during create multipart upload'() {
        given:
        Path tmp = Files.createTempDirectory("micronaut-object-storage")
        ApplicationContext ctx = null
        Path outside = tmp.resolve("outside")
        Files.createDirectory(outside)
        Path bucket = tmp.resolve("bucket")
        Files.createDirectory(bucket)
        Path multipartTarget = outside.resolve("multipart-target")
        Files.createDirectory(multipartTarget)
        assumeSymbolicLinksSupported(tmp)
        Files.createDirectories(tmp.resolve(LocalStorageOperations.INTERNAL_DIRECTORY))
        Files.createSymbolicLink(
            tmp.resolve(LocalStorageOperations.INTERNAL_DIRECTORY).resolve(LocalStorageOperations.MULTIPART_DIRECTORY),
            multipartTarget
        )

        ctx = ApplicationContext.run(["micronaut.object-storage.local.a.path": bucket.toString()])

        when:
        ctx.getBean(LocalStorageMultipartOperations).createMultipartUpload(new CreateMultipartUploadRequest("public", "text/plain"))

        then:
        thrown IllegalArgumentException
        !Files.list(multipartTarget).withCloseable { stream -> stream.findAny().present }

        cleanup:
        ctx?.close()
        deleteRecursively(tmp)
    }

    def 'symlinked multipart upload directory is rejected during upload part'() {
        given:
        Path tmp = Files.createTempDirectory("micronaut-object-storage")
        ApplicationContext ctx = null
        Path outside = tmp.resolve("outside")
        Files.createDirectory(outside)
        Path bucket = tmp.resolve("bucket")
        Files.createDirectory(bucket)
        Path multipartTarget = outside.resolve("multipart-target")
        Files.createDirectory(multipartTarget)
        assumeSymbolicLinksSupported(tmp)

        ctx = ApplicationContext.run(["micronaut.object-storage.local.a.path": bucket.toString()])
        LocalStorageMultipartOperations multipartOperations = ctx.getBean(LocalStorageMultipartOperations)
        def createResponse = multipartOperations.createMultipartUpload(new CreateMultipartUploadRequest("public", "text/plain"))
        LocalStorageBucketOperations.deleteRecursively(createResponse.nativeResponse.path)
        Files.createSymbolicLink(createResponse.nativeResponse.path, multipartTarget)

        when:
        multipartOperations.uploadPart(new UploadPartRequest(createResponse.upload, 1, UploadRequest.fromBytes("evil".bytes, "public", "text/plain")))

        then:
        thrown IllegalArgumentException
        !Files.list(multipartTarget).withCloseable { stream -> stream.findAny().present }

        cleanup:
        ctx?.close()
        deleteRecursively(tmp)
    }

    def 'symlinked multipart upload directory is rejected during abort'() {
        given:
        Path tmp = Files.createTempDirectory("micronaut-object-storage")
        ApplicationContext ctx = null
        Path outside = tmp.resolve("outside")
        Files.createDirectory(outside)
        Path bucket = tmp.resolve("bucket")
        Files.createDirectory(bucket)
        Path multipartTarget = outside.resolve("multipart-target")
        Files.createDirectory(multipartTarget)
        assumeSymbolicLinksSupported(tmp)

        ctx = ApplicationContext.run(["micronaut.object-storage.local.a.path": bucket.toString()])
        LocalStorageMultipartOperations multipartOperations = ctx.getBean(LocalStorageMultipartOperations)
        def createResponse = multipartOperations.createMultipartUpload(new CreateMultipartUploadRequest("public", "text/plain"))
        LocalStorageBucketOperations.deleteRecursively(createResponse.nativeResponse.path)
        Files.createSymbolicLink(createResponse.nativeResponse.path, multipartTarget)

        when:
        multipartOperations.abortMultipartUpload(new AbortMultipartUploadRequest(createResponse.upload))

        then:
        thrown IllegalArgumentException
        !Files.list(multipartTarget).withCloseable { stream -> stream.findAny().present }

        cleanup:
        ctx?.close()
        deleteRecursively(tmp)
    }

    def 'symlinked multipart bucket state is not followed during bucket delete'() {
        given:
        Path tmp = Files.createTempDirectory("micronaut-object-storage")
        ApplicationContext ctx = null
        Path outside = tmp.resolve("outside")
        Files.createDirectory(outside)
        Path bucket = tmp.resolve("bucket")
        Files.createDirectory(bucket)
        Path multipartTarget = outside.resolve("multipart-target")
        Files.createDirectory(multipartTarget)
        Path multipartRoot = tmp.resolve(LocalStorageOperations.INTERNAL_DIRECTORY)
            .resolve(LocalStorageOperations.MULTIPART_DIRECTORY)
        Files.createDirectories(multipartRoot)
        assumeSymbolicLinksSupported(tmp)
        Files.createSymbolicLink(multipartRoot.resolve("bucket"), multipartTarget)

        ctx = ApplicationContext.run(["micronaut.object-storage.local.a.path": bucket.toString()])

        when:
        ctx.getBean(LocalStorageBucketOperations).delete("bucket")

        then:
        thrown IllegalArgumentException
        !Files.list(multipartTarget).withCloseable { stream -> stream.findAny().present }

        cleanup:
        ctx?.close()
        deleteRecursively(tmp)
    }

    def 'symlinked provider-managed #component parent is not followed during bucket delete'() {
        given:
        Path tmp = Files.createTempDirectory("micronaut-object-storage")
        ApplicationContext ctx = null
        Path outside = tmp.resolve("outside")
        Files.createDirectory(outside)
        Path bucket = tmp.resolve("bucket")
        Files.createDirectory(bucket)
        Path providerTarget = outside.resolve("${component}-target")
        Path protectedBucketState = providerTarget.resolve("bucket")
        Files.createDirectories(protectedBucketState)
        Files.writeString(protectedBucketState.resolve("protected"), "keep")
        Path providerRoot = internalPath(tmp, providerRootSegments)
        Files.createDirectories(providerRoot.parent)
        assumeSymbolicLinksSupported(tmp)
        Files.createSymbolicLink(providerRoot, providerTarget)

        ctx = ApplicationContext.run(["micronaut.object-storage.local.a.path": bucket.toString()])

        when:
        ctx.getBean(LocalStorageBucketOperations).delete("bucket")

        then:
        thrown IllegalArgumentException
        Files.exists(protectedBucketState.resolve("protected"))

        cleanup:
        ctx?.close()
        deleteRecursively(tmp)

        where:
        component         | providerRootSegments
        'metadata object' | [LocalStorageLayout.METADATA_DIRECTORY, LocalStorageLayout.OBJECTS_DIRECTORY]
        'multipart'       | [LocalStorageLayout.MULTIPART_DIRECTORY]
        'snapshot'        | [LocalStorageLayout.SNAPSHOT_DIRECTORY]
    }

    def 'symlinked multipart parts directory is rejected during list parts'() {
        given:
        Path tmp = Files.createTempDirectory("micronaut-object-storage")
        ApplicationContext ctx = null
        Path outside = tmp.resolve("outside")
        Files.createDirectory(outside)
        Path bucket = tmp.resolve("bucket")
        Files.createDirectory(bucket)
        Path partsTarget = outside.resolve("parts-target")
        Files.createDirectory(partsTarget)
        assumeSymbolicLinksSupported(tmp)

        ctx = ApplicationContext.run(["micronaut.object-storage.local.a.path": bucket.toString()])
        LocalStorageMultipartOperations multipartOperations = ctx.getBean(LocalStorageMultipartOperations)
        def createResponse = multipartOperations.createMultipartUpload(new CreateMultipartUploadRequest("public", "text/plain"))
        Files.createSymbolicLink(createResponse.nativeResponse.path.resolve("parts"), partsTarget)

        when:
        multipartOperations.listParts(new ListMultipartPartsRequest(createResponse.upload, 10))

        then:
        thrown IllegalArgumentException
        !Files.list(partsTarget).withCloseable { stream -> stream.findAny().present }

        cleanup:
        ctx?.close()
        deleteRecursively(tmp)
    }

    def 'symlinked multipart part data is rejected during complete'() {
        given:
        Path tmp = Files.createTempDirectory("micronaut-object-storage")
        ApplicationContext ctx = null
        Path outside = tmp.resolve("outside")
        Files.createDirectory(outside)
        Path bucket = tmp.resolve("bucket")
        Files.createDirectory(bucket)
        Path secret = outside.resolve("secret")
        Files.writeString(secret, "keep")
        assumeSymbolicLinksSupported(tmp)

        ctx = ApplicationContext.run(["micronaut.object-storage.local.a.path": bucket.toString()])
        LocalStorageMultipartOperations multipartOperations = ctx.getBean(LocalStorageMultipartOperations)
        def createResponse = multipartOperations.createMultipartUpload(new CreateMultipartUploadRequest("public", "text/plain"))
        def uploadPart = multipartOperations.uploadPart(new UploadPartRequest(
            createResponse.upload,
            1,
            UploadRequest.fromBytes("safe".bytes, "public", "text/plain")
        ))
        Properties properties = loadProperties(createResponse.nativeResponse.path.resolve("parts").resolve("1.properties"))
        Path partPath = createResponse.nativeResponse.path.resolve("parts").resolve(properties.getProperty("file"))
        Files.delete(partPath)
        Files.createSymbolicLink(partPath, secret)

        when:
        multipartOperations.completeMultipartUpload(new CompleteMultipartUploadRequest(createResponse.upload, [uploadPart.part]))

        then:
        thrown IllegalArgumentException
        Files.readString(secret) == "keep"

        cleanup:
        ctx?.close()
        deleteRecursively(tmp)
    }

    private static Path internalPath(Path tmp, List<String> segments) {
        Path path = tmp.resolve(LocalStorageOperations.INTERNAL_DIRECTORY)
        for (String segment : segments) {
            path = path.resolve(segment)
        }
        path
    }

    private static Properties loadProperties(Path path) {
        Properties properties = new Properties()
        Files.newInputStream(path).withCloseable { properties.load(it) }
        properties
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
