package io.micronaut.objectstorage.oraclecloud

import com.oracle.bmc.Region
import com.oracle.bmc.objectstorage.ObjectStorage
import com.oracle.bmc.objectstorage.ObjectStoragePaginators
import com.oracle.bmc.objectstorage.ObjectStorageWaiters
import com.oracle.bmc.objectstorage.requests.AbortMultipartUploadRequest
import com.oracle.bmc.objectstorage.requests.CancelWorkRequestRequest
import com.oracle.bmc.objectstorage.requests.CommitMultipartUploadRequest
import com.oracle.bmc.objectstorage.requests.CopyObjectRequest
import com.oracle.bmc.objectstorage.requests.CreateBucketRequest
import com.oracle.bmc.objectstorage.requests.CreateMultipartUploadRequest
import com.oracle.bmc.objectstorage.requests.CreatePreauthenticatedRequestRequest
import com.oracle.bmc.objectstorage.requests.CreateReplicationPolicyRequest
import com.oracle.bmc.objectstorage.requests.CreateRetentionRuleRequest
import com.oracle.bmc.objectstorage.requests.DeleteBucketRequest
import com.oracle.bmc.objectstorage.requests.DeleteObjectLifecyclePolicyRequest
import com.oracle.bmc.objectstorage.requests.DeleteObjectRequest
import com.oracle.bmc.objectstorage.requests.DeletePreauthenticatedRequestRequest
import com.oracle.bmc.objectstorage.requests.DeleteReplicationPolicyRequest
import com.oracle.bmc.objectstorage.requests.DeleteRetentionRuleRequest
import com.oracle.bmc.objectstorage.requests.GetBucketRequest
import com.oracle.bmc.objectstorage.requests.GetNamespaceMetadataRequest
import com.oracle.bmc.objectstorage.requests.GetNamespaceRequest
import com.oracle.bmc.objectstorage.requests.GetObjectLifecyclePolicyRequest
import com.oracle.bmc.objectstorage.requests.GetObjectRequest
import com.oracle.bmc.objectstorage.requests.GetPreauthenticatedRequestRequest
import com.oracle.bmc.objectstorage.requests.GetReplicationPolicyRequest
import com.oracle.bmc.objectstorage.requests.GetRetentionRuleRequest
import com.oracle.bmc.objectstorage.requests.GetWorkRequestRequest
import com.oracle.bmc.objectstorage.requests.HeadBucketRequest
import com.oracle.bmc.objectstorage.requests.HeadObjectRequest
import com.oracle.bmc.objectstorage.requests.ListBucketsRequest
import com.oracle.bmc.objectstorage.requests.ListMultipartUploadPartsRequest
import com.oracle.bmc.objectstorage.requests.ListMultipartUploadsRequest
import com.oracle.bmc.objectstorage.requests.ListObjectVersionsRequest
import com.oracle.bmc.objectstorage.requests.ListObjectsRequest
import com.oracle.bmc.objectstorage.requests.ListPreauthenticatedRequestsRequest
import com.oracle.bmc.objectstorage.requests.ListReplicationPoliciesRequest
import com.oracle.bmc.objectstorage.requests.ListReplicationSourcesRequest
import com.oracle.bmc.objectstorage.requests.ListRetentionRulesRequest
import com.oracle.bmc.objectstorage.requests.ListWorkRequestErrorsRequest
import com.oracle.bmc.objectstorage.requests.ListWorkRequestLogsRequest
import com.oracle.bmc.objectstorage.requests.ListWorkRequestsRequest
import com.oracle.bmc.objectstorage.requests.MakeBucketWritableRequest
import com.oracle.bmc.objectstorage.requests.PutObjectLifecyclePolicyRequest
import com.oracle.bmc.objectstorage.requests.PutObjectRequest
import com.oracle.bmc.objectstorage.requests.ReencryptBucketRequest
import com.oracle.bmc.objectstorage.requests.ReencryptObjectRequest
import com.oracle.bmc.objectstorage.requests.RenameObjectRequest
import com.oracle.bmc.objectstorage.requests.RestoreObjectsRequest
import com.oracle.bmc.objectstorage.requests.UpdateBucketRequest
import com.oracle.bmc.objectstorage.requests.UpdateNamespaceMetadataRequest
import com.oracle.bmc.objectstorage.requests.UpdateObjectStorageTierRequest
import com.oracle.bmc.objectstorage.requests.UpdateRetentionRuleRequest
import com.oracle.bmc.objectstorage.requests.UploadPartRequest
import com.oracle.bmc.objectstorage.responses.AbortMultipartUploadResponse
import com.oracle.bmc.objectstorage.responses.CancelWorkRequestResponse
import com.oracle.bmc.objectstorage.responses.CommitMultipartUploadResponse
import com.oracle.bmc.objectstorage.responses.CopyObjectResponse
import com.oracle.bmc.objectstorage.responses.CreateBucketResponse
import com.oracle.bmc.objectstorage.responses.CreateMultipartUploadResponse
import com.oracle.bmc.objectstorage.responses.CreatePreauthenticatedRequestResponse
import com.oracle.bmc.objectstorage.responses.CreateReplicationPolicyResponse
import com.oracle.bmc.objectstorage.responses.CreateRetentionRuleResponse
import com.oracle.bmc.objectstorage.responses.DeleteBucketResponse
import com.oracle.bmc.objectstorage.responses.DeleteObjectLifecyclePolicyResponse
import com.oracle.bmc.objectstorage.responses.DeleteObjectResponse
import com.oracle.bmc.objectstorage.responses.DeletePreauthenticatedRequestResponse
import com.oracle.bmc.objectstorage.responses.DeleteReplicationPolicyResponse
import com.oracle.bmc.objectstorage.responses.DeleteRetentionRuleResponse
import com.oracle.bmc.objectstorage.responses.GetBucketResponse
import com.oracle.bmc.objectstorage.responses.GetNamespaceMetadataResponse
import com.oracle.bmc.objectstorage.responses.GetNamespaceResponse
import com.oracle.bmc.objectstorage.responses.GetObjectLifecyclePolicyResponse
import com.oracle.bmc.objectstorage.responses.GetObjectResponse
import com.oracle.bmc.objectstorage.responses.GetPreauthenticatedRequestResponse
import com.oracle.bmc.objectstorage.responses.GetReplicationPolicyResponse
import com.oracle.bmc.objectstorage.responses.GetRetentionRuleResponse
import com.oracle.bmc.objectstorage.responses.GetWorkRequestResponse
import com.oracle.bmc.objectstorage.responses.HeadBucketResponse
import com.oracle.bmc.objectstorage.responses.HeadObjectResponse
import com.oracle.bmc.objectstorage.responses.ListBucketsResponse
import com.oracle.bmc.objectstorage.responses.ListMultipartUploadPartsResponse
import com.oracle.bmc.objectstorage.responses.ListMultipartUploadsResponse
import com.oracle.bmc.objectstorage.responses.ListObjectVersionsResponse
import com.oracle.bmc.objectstorage.responses.ListObjectsResponse
import com.oracle.bmc.objectstorage.responses.ListPreauthenticatedRequestsResponse
import com.oracle.bmc.objectstorage.responses.ListReplicationPoliciesResponse
import com.oracle.bmc.objectstorage.responses.ListReplicationSourcesResponse
import com.oracle.bmc.objectstorage.responses.ListRetentionRulesResponse
import com.oracle.bmc.objectstorage.responses.ListWorkRequestErrorsResponse
import com.oracle.bmc.objectstorage.responses.ListWorkRequestLogsResponse
import com.oracle.bmc.objectstorage.responses.ListWorkRequestsResponse
import com.oracle.bmc.objectstorage.responses.MakeBucketWritableResponse
import com.oracle.bmc.objectstorage.responses.PutObjectLifecyclePolicyResponse
import com.oracle.bmc.objectstorage.responses.PutObjectResponse
import com.oracle.bmc.objectstorage.responses.ReencryptBucketResponse
import com.oracle.bmc.objectstorage.responses.ReencryptObjectResponse
import com.oracle.bmc.objectstorage.responses.RenameObjectResponse
import com.oracle.bmc.objectstorage.responses.RestoreObjectsResponse
import com.oracle.bmc.objectstorage.responses.UpdateBucketResponse
import com.oracle.bmc.objectstorage.responses.UpdateNamespaceMetadataResponse
import com.oracle.bmc.objectstorage.responses.UpdateObjectStorageTierResponse
import com.oracle.bmc.objectstorage.responses.UpdateRetentionRuleResponse
import com.oracle.bmc.objectstorage.responses.UploadPartResponse
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
