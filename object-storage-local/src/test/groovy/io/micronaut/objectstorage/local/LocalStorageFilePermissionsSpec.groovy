package io.micronaut.objectstorage.local

import io.micronaut.context.ApplicationContext
import io.micronaut.objectstorage.metadata.BucketMetadataWrite
import io.micronaut.objectstorage.multipart.CreateMultipartUploadRequest
import io.micronaut.objectstorage.multipart.UploadPartRequest
import io.micronaut.objectstorage.request.UploadRequest
import spock.lang.Requires
import spock.lang.Specification

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.FileSystems
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFilePermission

@Requires({ FileSystems.default.supportedFileAttributeViews().contains('posix') })
class LocalStorageFilePermissionsSpec extends Specification {

    void 'configured bucket path uses owner only POSIX permissions for objects and metadata'() {
        given:
        Path root = Files.createTempDirectory("micronaut-object-storage-permissions")
        Path bucket = root.resolve("bucket")
        ApplicationContext ctx = ApplicationContext.run(["micronaut.object-storage.local.a.path": bucket.toString()])
        LocalStorageOperations operations = ctx.getBean(LocalStorageOperations)
        LocalStorageMultipartOperations multipartOperations = ctx.getBean(LocalStorageMultipartOperations)
        LocalStorageBucketOperations bucketOperations = ctx.getBean(LocalStorageBucketOperations)
        LocalStorageBucketMetadataOperations bucketMetadataOperations = ctx.getBean(LocalStorageBucketMetadataOperations)
        UploadRequest request = UploadRequest.fromBytes('secret'.getBytes(StandardCharsets.UTF_8), 'nested/deeper/object.txt', 'text/plain')
        request.metadata = [classification: 'internal']

        when:
        bucketOperations.create('reports')
        bucketMetadataOperations.save(new BucketMetadataWrite('reports', [region: 'internal'], [:]))
        operations.upload(request)
        def multipartUpload = multipartOperations.createMultipartUpload(new CreateMultipartUploadRequest('multipart/secret.txt', 'text/plain')).upload
        def multipartPart = multipartOperations.uploadPart(new UploadPartRequest(multipartUpload, 1, UploadRequest.fromBytes('part'.bytes, 'multipart/secret.txt', 'text/plain')))
        Path multipartUploadDirectory = root.resolve(LocalStorageOperations.INTERNAL_DIRECTORY)
            .resolve(LocalStorageOperations.MULTIPART_DIRECTORY)
            .resolve('bucket')
            .resolve(multipartUpload.uploadId)

        then:
        Files.getPosixFilePermissions(bucket) == ownerOnlyDirectoryPermissions()
        Files.getPosixFilePermissions(root.resolve(LocalStorageOperations.INTERNAL_DIRECTORY)) == ownerOnlyDirectoryPermissions()
        Files.getPosixFilePermissions(root.resolve(LocalStorageOperations.INTERNAL_DIRECTORY).resolve(LocalStorageLayout.METADATA_DIRECTORY)) == ownerOnlyDirectoryPermissions()
        Files.getPosixFilePermissions(root.resolve(LocalStorageOperations.INTERNAL_DIRECTORY).resolve(LocalStorageLayout.METADATA_DIRECTORY).resolve(LocalStorageOperations.OBJECTS_DIRECTORY)) == ownerOnlyDirectoryPermissions()
        Files.getPosixFilePermissions(root.resolve(LocalStorageOperations.INTERNAL_DIRECTORY).resolve(LocalStorageLayout.METADATA_DIRECTORY).resolve(LocalStorageOperations.OBJECTS_DIRECTORY).resolve('bucket')) == ownerOnlyDirectoryPermissions()
        Files.getPosixFilePermissions(root.resolve(LocalStorageOperations.INTERNAL_DIRECTORY).resolve(LocalStorageLayout.METADATA_DIRECTORY).resolve(LocalStorageOperations.OBJECTS_DIRECTORY).resolve('bucket/nested')) == ownerOnlyDirectoryPermissions()
        Files.getPosixFilePermissions(root.resolve(LocalStorageOperations.INTERNAL_DIRECTORY).resolve(LocalStorageLayout.METADATA_DIRECTORY).resolve(LocalStorageOperations.OBJECTS_DIRECTORY).resolve('bucket/nested/deeper')) == ownerOnlyDirectoryPermissions()
        Files.getPosixFilePermissions(root.resolve(LocalStorageOperations.INTERNAL_DIRECTORY).resolve(LocalStorageLayout.METADATA_DIRECTORY).resolve(LocalStorageOperations.OBJECTS_DIRECTORY).resolve('bucket/nested/deeper/object.txt')) == ownerOnlyFilePermissions()
        Files.getPosixFilePermissions(root.resolve(LocalStorageOperations.INTERNAL_DIRECTORY).resolve(LocalStorageLayout.METADATA_DIRECTORY).resolve('buckets')) == ownerOnlyDirectoryPermissions()
        Files.getPosixFilePermissions(root.resolve(LocalStorageOperations.INTERNAL_DIRECTORY).resolve(LocalStorageLayout.METADATA_DIRECTORY).resolve('buckets/reports')) == ownerOnlyFilePermissions()
        Files.getPosixFilePermissions(root.resolve(LocalStorageOperations.INTERNAL_DIRECTORY).resolve(LocalStorageOperations.MULTIPART_DIRECTORY)) == ownerOnlyDirectoryPermissions()
        Files.getPosixFilePermissions(root.resolve(LocalStorageOperations.INTERNAL_DIRECTORY).resolve(LocalStorageOperations.MULTIPART_DIRECTORY).resolve('bucket')) == ownerOnlyDirectoryPermissions()
        Files.getPosixFilePermissions(multipartUploadDirectory) == ownerOnlyDirectoryPermissions()
        Files.getPosixFilePermissions(multipartUploadDirectory.resolve('upload.properties')) == ownerOnlyFilePermissions()
        Files.getPosixFilePermissions(multipartUploadDirectory.resolve('parts')) == ownerOnlyDirectoryPermissions()
        Files.getPosixFilePermissions(multipartPart.nativeResponse.path) == ownerOnlyFilePermissions()
        Files.getPosixFilePermissions(multipartUploadDirectory.resolve('parts/1.properties')) == ownerOnlyFilePermissions()
        Files.getPosixFilePermissions(root.resolve(LocalStorageOperations.INTERNAL_DIRECTORY).resolve(LocalStorageLayout.TEMPORARY_DIRECTORY)) == ownerOnlyDirectoryPermissions()
        Files.getPosixFilePermissions(root.resolve(LocalStorageOperations.INTERNAL_DIRECTORY).resolve(LocalStorageLayout.TEMPORARY_DIRECTORY).resolve('bucket')) == ownerOnlyDirectoryPermissions()
        Files.getPosixFilePermissions(bucket.resolve('nested')) == ownerOnlyDirectoryPermissions()
        Files.getPosixFilePermissions(bucket.resolve('nested/deeper')) == ownerOnlyDirectoryPermissions()
        Files.getPosixFilePermissions(bucket.resolve('nested/deeper/object.txt')) == ownerOnlyFilePermissions()
        !Files.exists(bucket.resolve(LocalStorageOperations.INTERNAL_DIRECTORY))

        cleanup:
        ctx?.close()
        if (Files.exists(root)) {
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
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

    private static Set<PosixFilePermission> ownerOnlyDirectoryPermissions() {
        [
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE,
        ] as Set<PosixFilePermission>
    }

    private static Set<PosixFilePermission> ownerOnlyFilePermissions() {
        [
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
        ] as Set<PosixFilePermission>
    }
}
