package io.micronaut.objectstorage.oraclecloud

import com.oracle.bmc.Region
import com.oracle.bmc.objectstorage.ObjectStorage
import com.oracle.bmc.objectstorage.ObjectStoragePaginators
import com.oracle.bmc.objectstorage.ObjectStorageWaiters
import com.oracle.bmc.objectstorage.requests.*
import com.oracle.bmc.objectstorage.responses.*
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.request.UploadRequest
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Singleton
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path

@Property(name = "micronaut.object-storage.oracle-cloud.default.bucket", value = "profile-pictures-bucket")
@Property(name = "spec.name", value = "OracleCloudStorageOperationsUploadWithConsumerSpec")
@MicronautTest
class OracleCloudStorageOperationsUploadWithConsumerSpec extends Specification {

    @Inject
    ObjectStorageOperations<PutObjectRequest.Builder, PutObjectResponse> objectStorage

    @Inject
    ObjectStorageReplacement objectStorageReplacement

    void "consumer accept is invoked"() {
        given:
        Path tempFilePath = Files.createTempFile('test-file', 'txt')
        tempFilePath.toFile().text = 'micronaut'
        UploadRequest uploadRequest = UploadRequest.fromPath(tempFilePath)

        when:
//tag::consumer[]
        objectStorage.upload(uploadRequest, builder -> {
                builder.opcMeta([project: "micronaut-object-storage"])
        });
//end::consumer[]
        then:
        objectStorageReplacement.request
        [project: "micronaut-object-storage"] == objectStorageReplacement.request.opcMeta
    }

    @Requires(property = "spec.name", value = "OracleCloudStorageOperationsUploadWithConsumerSpec")
    @Replaces(ObjectStorage)
    @Singleton
    static class ObjectStorageReplacement implements ObjectStorage {
        PutObjectRequest request

        @Override
        void setEndpoint(String endpoint) {

        }

        @Override
        String getEndpoint() {
            return null
        }

        @Override
        void setRegion(Region region) {

        }

        @Override
        void setRegion(String regionId) {

        }

        @Override
        AbortMultipartUploadResponse abortMultipartUpload(AbortMultipartUploadRequest request) {
            return null
        }

        @Override
        CancelWorkRequestResponse cancelWorkRequest(CancelWorkRequestRequest request) {
            return null
        }

        @Override
        CommitMultipartUploadResponse commitMultipartUpload(CommitMultipartUploadRequest request) {
            return null
        }

        @Override
        CopyObjectResponse copyObject(CopyObjectRequest request) {
            return null
        }

        @Override
        CreateBucketResponse createBucket(CreateBucketRequest request) {
            return null
        }

        @Override
        CreateMultipartUploadResponse createMultipartUpload(CreateMultipartUploadRequest request) {
            return null
        }

        @Override
        CreatePreauthenticatedRequestResponse createPreauthenticatedRequest(CreatePreauthenticatedRequestRequest request) {
            return null
        }

        @Override
        CreateReplicationPolicyResponse createReplicationPolicy(CreateReplicationPolicyRequest request) {
            return null
        }

        @Override
        CreateRetentionRuleResponse createRetentionRule(CreateRetentionRuleRequest request) {
            return null
        }

        @Override
        DeleteBucketResponse deleteBucket(DeleteBucketRequest request) {
            return null
        }

        @Override
        DeleteObjectResponse deleteObject(DeleteObjectRequest request) {
            return null
        }

        @Override
        DeleteObjectLifecyclePolicyResponse deleteObjectLifecyclePolicy(DeleteObjectLifecyclePolicyRequest request) {
            return null
        }

        @Override
        DeletePreauthenticatedRequestResponse deletePreauthenticatedRequest(DeletePreauthenticatedRequestRequest request) {
            return null
        }

        @Override
        DeleteReplicationPolicyResponse deleteReplicationPolicy(DeleteReplicationPolicyRequest request) {
            return null
        }

        @Override
        DeleteRetentionRuleResponse deleteRetentionRule(DeleteRetentionRuleRequest request) {
            return null
        }

        @Override
        GetBucketResponse getBucket(GetBucketRequest request) {
            return null
        }

        @Override
        GetNamespaceResponse getNamespace(GetNamespaceRequest request) {
            return null
        }

        @Override
        GetNamespaceMetadataResponse getNamespaceMetadata(GetNamespaceMetadataRequest request) {
            return null
        }

        @Override
        GetObjectResponse getObject(GetObjectRequest request) {
            return null
        }

        @Override
        GetObjectLifecyclePolicyResponse getObjectLifecyclePolicy(GetObjectLifecyclePolicyRequest request) {
            return null
        }

        @Override
        GetPreauthenticatedRequestResponse getPreauthenticatedRequest(GetPreauthenticatedRequestRequest request) {
            return null
        }

        @Override
        GetReplicationPolicyResponse getReplicationPolicy(GetReplicationPolicyRequest request) {
            return null
        }

        @Override
        GetRetentionRuleResponse getRetentionRule(GetRetentionRuleRequest request) {
            return null
        }

        @Override
        GetWorkRequestResponse getWorkRequest(GetWorkRequestRequest request) {
            return null
        }

        @Override
        HeadBucketResponse headBucket(HeadBucketRequest request) {
            return null
        }

        @Override
        HeadObjectResponse headObject(HeadObjectRequest request) {
            return null
        }

        @Override
        ListBucketsResponse listBuckets(ListBucketsRequest request) {
            return null
        }

        @Override
        ListMultipartUploadPartsResponse listMultipartUploadParts(ListMultipartUploadPartsRequest request) {
            return null
        }

        @Override
        ListMultipartUploadsResponse listMultipartUploads(ListMultipartUploadsRequest request) {
            return null
        }

        @Override
        ListObjectVersionsResponse listObjectVersions(ListObjectVersionsRequest request) {
            return null
        }

        @Override
        ListObjectsResponse listObjects(ListObjectsRequest request) {
            return null
        }

        @Override
        ListPreauthenticatedRequestsResponse listPreauthenticatedRequests(ListPreauthenticatedRequestsRequest request) {
            return null
        }

        @Override
        ListReplicationPoliciesResponse listReplicationPolicies(ListReplicationPoliciesRequest request) {
            return null
        }

        @Override
        ListReplicationSourcesResponse listReplicationSources(ListReplicationSourcesRequest request) {
            return null
        }

        @Override
        ListRetentionRulesResponse listRetentionRules(ListRetentionRulesRequest request) {
            return null
        }

        @Override
        ListWorkRequestErrorsResponse listWorkRequestErrors(ListWorkRequestErrorsRequest request) {
            return null
        }

        @Override
        ListWorkRequestLogsResponse listWorkRequestLogs(ListWorkRequestLogsRequest request) {
            return null
        }

        @Override
        ListWorkRequestsResponse listWorkRequests(ListWorkRequestsRequest request) {
            return null
        }

        @Override
        MakeBucketWritableResponse makeBucketWritable(MakeBucketWritableRequest request) {
            return null
        }

        @Override
        PutObjectResponse putObject(PutObjectRequest request) {
            this.request = request
            return null
        }

        @Override
        PutObjectLifecyclePolicyResponse putObjectLifecyclePolicy(PutObjectLifecyclePolicyRequest request) {
            return null
        }

        @Override
        ReencryptBucketResponse reencryptBucket(ReencryptBucketRequest request) {
            return null
        }

        @Override
        ReencryptObjectResponse reencryptObject(ReencryptObjectRequest request) {
            return null
        }

        @Override
        RenameObjectResponse renameObject(RenameObjectRequest request) {
            return null
        }

        @Override
        RestoreObjectsResponse restoreObjects(RestoreObjectsRequest request) {
            return null
        }

        @Override
        UpdateBucketResponse updateBucket(UpdateBucketRequest request) {
            return null
        }

        @Override
        UpdateNamespaceMetadataResponse updateNamespaceMetadata(UpdateNamespaceMetadataRequest request) {
            return null
        }

        @Override
        UpdateObjectStorageTierResponse updateObjectStorageTier(UpdateObjectStorageTierRequest request) {
            return null
        }

        @Override
        UpdateRetentionRuleResponse updateRetentionRule(UpdateRetentionRuleRequest request) {
            return null
        }

        @Override
        UploadPartResponse uploadPart(UploadPartRequest request) {
            return null
        }

        @Override
        ObjectStorageWaiters getWaiters() {
            return null
        }

        @Override
        ObjectStoragePaginators getPaginators() {
            return null
        }

        @Override
        void close() throws Exception {

        }
    }
}
