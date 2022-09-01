package io.micronaut.objectstorage.googlecloud

import com.google.api.gax.paging.Page
import com.google.cloud.Policy
import com.google.cloud.ReadChannel
import com.google.cloud.WriteChannel
import com.google.cloud.storage.Acl
import com.google.cloud.storage.Blob
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Bucket
import com.google.cloud.storage.BucketInfo
import com.google.cloud.storage.CopyWriter
import com.google.cloud.storage.HmacKey
import com.google.cloud.storage.Notification
import com.google.cloud.storage.NotificationInfo
import com.google.cloud.storage.PostPolicyV4
import com.google.cloud.storage.ServiceAccount
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageBatch
import com.google.cloud.storage.StorageOptions
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.UploadRequest
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Singleton
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

@Property(name = "micronaut.object-storage.gcp.default.bucket", value = "profile-pictures-bucket")
@Property(name = "spec.name", value = "AswsS3OperationsUploadWithConsumerSpec")
@MicronautTest
class GoogleCloudStorageOperationsUploadWithConsumerSpec extends Specification {

    @Inject
    ObjectStorageOperations<BlobInfo.Builder, Blob> objectStorage

    @Inject
    StorageReplacement storageReplacement

    void "consumer accept is invoked"() {
        given:
        Path tempFilePath = Files.createTempFile('test-file', 'txt')
        tempFilePath.toFile().text = 'micronaut'
        UploadRequest uploadRequest = UploadRequest.fromPath(tempFilePath)

        when:
//tag::consumer[]
        objectStorage.upload(uploadRequest, builder -> {
            builder.metadata = [project: "micronaut-object-storage"]
        });
//end::consumer[]
        then:
        storageReplacement.blobInfo
        [project: "micronaut-object-storage"] == storageReplacement.blobInfo.metadata
    }

    @Requires(property = "spec.name", value = "AswsS3OperationsUploadWithConsumerSpec")
    @Replaces(Storage)
    @Singleton
    static class StorageReplacement implements Storage {
        BlobInfo blobInfo

        @Override
        Bucket create(BucketInfo bucketInfo, BucketTargetOption... options) {
            return null
        }

        @Override
        Blob create(BlobInfo blobInfo, BlobTargetOption... options) {
            return null
        }

        @Override
        Blob create(BlobInfo blobInfo, byte[] content, BlobTargetOption... options) {
            this.blobInfo = blobInfo
            return null
        }

        @Override
        Blob create(BlobInfo blobInfo, byte[] content, int offset, int length, BlobTargetOption... options) {
            null
        }

        @Override
        Blob create(BlobInfo blobInfo, InputStream content, BlobWriteOption... options) {
            return null
        }

        @Override
        Blob createFrom(BlobInfo blobInfo, Path path, BlobWriteOption... options) throws IOException {
            return null
        }

        @Override
        Blob createFrom(BlobInfo blobInfo, Path path, int bufferSize, BlobWriteOption... options) throws IOException {
            return null
        }

        @Override
        Blob createFrom(BlobInfo blobInfo, InputStream content, BlobWriteOption... options) throws IOException {
            return null
        }

        @Override
        Blob createFrom(BlobInfo blobInfo, InputStream content, int bufferSize, BlobWriteOption... options) throws IOException {
            return null
        }

        @Override
        Bucket get(String bucket, BucketGetOption... options) {
            return null
        }

        @Override
        Bucket lockRetentionPolicy(BucketInfo bucket, BucketTargetOption... options) {
            return null
        }

        @Override
        Blob get(String bucket, String blob, BlobGetOption... options) {
            return null
        }

        @Override
        Blob get(BlobId blob, BlobGetOption... options) {
            return null
        }

        @Override
        Blob get(BlobId blob) {
            return null
        }

        @Override
        Page<Bucket> list(BucketListOption... options) {
            return null
        }

        @Override
        Page<Blob> list(String bucket, BlobListOption... options) {
            return null
        }

        @Override
        Bucket update(BucketInfo bucketInfo, BucketTargetOption... options) {
            return null
        }

        @Override
        Blob update(BlobInfo blobInfo, BlobTargetOption... options) {
            return null
        }

        @Override
        Blob update(BlobInfo blobInfo) {
            return null
        }

        @Override
        boolean delete(String bucket, BucketSourceOption... options) {
            return false
        }

        @Override
        boolean delete(String bucket, String blob, BlobSourceOption... options) {
            return false
        }

        @Override
        boolean delete(BlobId blob, BlobSourceOption... options) {
            return false
        }

        @Override
        boolean delete(BlobId blob) {
            return false
        }

        @Override
        Blob compose(ComposeRequest composeRequest) {
            return null
        }

        @Override
        CopyWriter copy(CopyRequest copyRequest) {
            return null
        }

        @Override
        byte[] readAllBytes(String bucket, String blob, BlobSourceOption... options) {
            return new byte[0]
        }

        @Override
        byte[] readAllBytes(BlobId blob, BlobSourceOption... options) {
            return new byte[0]
        }

        @Override
        StorageBatch batch() {
            return null
        }

        @Override
        ReadChannel reader(String bucket, String blob, BlobSourceOption... options) {
            return null
        }

        @Override
        ReadChannel reader(BlobId blob, BlobSourceOption... options) {
            return null
        }

        @Override
        void downloadTo(BlobId blob, Path path, BlobSourceOption... options) {

        }

        @Override
        void downloadTo(BlobId blob, OutputStream outputStream, BlobSourceOption... options) {

        }

        @Override
        WriteChannel writer(BlobInfo blobInfo, BlobWriteOption... options) {
            return null
        }

        @Override
        WriteChannel writer(URL signedURL) {
            return null
        }

        @Override
        URL signUrl(BlobInfo blobInfo, long duration, TimeUnit unit, SignUrlOption... options) {
            return null
        }

        @Override
        PostPolicyV4 generateSignedPostPolicyV4(BlobInfo blobInfo, long duration, TimeUnit unit, PostPolicyV4.PostFieldsV4 fields, PostPolicyV4.PostConditionsV4 conditions, PostPolicyV4Option... options) {
            return null
        }

        @Override
        PostPolicyV4 generateSignedPostPolicyV4(BlobInfo blobInfo, long duration, TimeUnit unit, PostPolicyV4.PostFieldsV4 fields, PostPolicyV4Option... options) {
            return null
        }

        @Override
        PostPolicyV4 generateSignedPostPolicyV4(BlobInfo blobInfo, long duration, TimeUnit unit, PostPolicyV4.PostConditionsV4 conditions, PostPolicyV4Option... options) {
            return null
        }

        @Override
        PostPolicyV4 generateSignedPostPolicyV4(BlobInfo blobInfo, long duration, TimeUnit unit, PostPolicyV4Option... options) {
            return null
        }

        @Override
        List<Blob> get(BlobId... blobIds) {
            return null
        }

        @Override
        List<Blob> get(Iterable<BlobId> blobIds) {
            return null
        }

        @Override
        List<Blob> update(BlobInfo... blobInfos) {
            return null
        }

        @Override
        List<Blob> update(Iterable<BlobInfo> blobInfos) {
            return null
        }

        @Override
        List<Boolean> delete(BlobId... blobIds) {
            return null
        }

        @Override
        List<Boolean> delete(Iterable<BlobId> blobIds) {
            return null
        }

        @Override
        Acl getAcl(String bucket, Acl.Entity entity, BucketSourceOption... options) {
            return null
        }

        @Override
        Acl getAcl(String bucket, Acl.Entity entity) {
            return null
        }

        @Override
        boolean deleteAcl(String bucket, Acl.Entity entity, BucketSourceOption... options) {
            return false
        }

        @Override
        boolean deleteAcl(String bucket, Acl.Entity entity) {
            return false
        }

        @Override
        Acl createAcl(String bucket, Acl acl, BucketSourceOption... options) {
            return null
        }

        @Override
        Acl createAcl(String bucket, Acl acl) {
            return null
        }

        @Override
        Acl updateAcl(String bucket, Acl acl, BucketSourceOption... options) {
            return null
        }

        @Override
        Acl updateAcl(String bucket, Acl acl) {
            return null
        }

        @Override
        List<Acl> listAcls(String bucket, BucketSourceOption... options) {
            return null
        }

        @Override
        List<Acl> listAcls(String bucket) {
            return null
        }

        @Override
        Acl getDefaultAcl(String bucket, Acl.Entity entity) {
            return null
        }

        @Override
        boolean deleteDefaultAcl(String bucket, Acl.Entity entity) {
            return false
        }

        @Override
        Acl createDefaultAcl(String bucket, Acl acl) {
            return null
        }

        @Override
        Acl updateDefaultAcl(String bucket, Acl acl) {
            return null
        }

        @Override
        List<Acl> listDefaultAcls(String bucket) {
            return null
        }

        @Override
        Acl getAcl(BlobId blob, Acl.Entity entity) {
            return null
        }

        @Override
        boolean deleteAcl(BlobId blob, Acl.Entity entity) {
            return false
        }

        @Override
        Acl createAcl(BlobId blob, Acl acl) {
            return null
        }

        @Override
        Acl updateAcl(BlobId blob, Acl acl) {
            return null
        }

        @Override
        List<Acl> listAcls(BlobId blob) {
            return null
        }

        @Override
        HmacKey createHmacKey(ServiceAccount serviceAccount, CreateHmacKeyOption... options) {
            return null
        }

        @Override
        Page<HmacKey.HmacKeyMetadata> listHmacKeys(ListHmacKeysOption... options) {
            return null
        }

        @Override
        HmacKey.HmacKeyMetadata getHmacKey(String accessId, GetHmacKeyOption... options) {
            return null
        }

        @Override
        void deleteHmacKey(HmacKey.HmacKeyMetadata hmacKeyMetadata, DeleteHmacKeyOption... options) {

        }

        @Override
        HmacKey.HmacKeyMetadata updateHmacKeyState(HmacKey.HmacKeyMetadata hmacKeyMetadata, HmacKey.HmacKeyState state, UpdateHmacKeyOption... options) {
            return null
        }

        @Override
        Policy getIamPolicy(String bucket, BucketSourceOption... options) {
            return null
        }

        @Override
        Policy setIamPolicy(String bucket, Policy policy, BucketSourceOption... options) {
            return null
        }

        @Override
        List<Boolean> testIamPermissions(String bucket, List<String> permissions, BucketSourceOption... options) {
            return null
        }

        @Override
        ServiceAccount getServiceAccount(String projectId) {
            return null
        }

        @Override
        Notification createNotification(String bucket, NotificationInfo notificationInfo) {
            return null
        }

        @Override
        Notification getNotification(String bucket, String notificationId) {
            return null
        }

        @Override
        List<Notification> listNotifications(String bucket) {
            return null
        }

        @Override
        boolean deleteNotification(String bucket, String notificationId) {
            return false
        }

        @Override
        StorageOptions getOptions() {
            return null
        }
    }
}
