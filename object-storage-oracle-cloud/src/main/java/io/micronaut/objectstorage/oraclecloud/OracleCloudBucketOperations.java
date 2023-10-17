package io.micronaut.objectstorage.oraclecloud;

import com.oracle.bmc.auth.RegionProvider;
import com.oracle.bmc.objectstorage.ObjectStorage;
import com.oracle.bmc.objectstorage.model.BucketSummary;
import com.oracle.bmc.objectstorage.requests.ListBucketsRequest;
import com.oracle.bmc.objectstorage.requests.PutObjectRequest;
import com.oracle.bmc.objectstorage.responses.DeleteObjectResponse;
import com.oracle.bmc.objectstorage.responses.ListBucketsResponse;
import com.oracle.bmc.objectstorage.responses.PutObjectResponse;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Requires;
import io.micronaut.objectstorage.ObjectStorageOperations;
import io.micronaut.objectstorage.bucket.BucketOperations;
import io.micronaut.objectstorage.configuration.ToggeableCondition;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@EachBean(OracleCloudNamespaceConfiguration.class)
@Requires(condition = ToggeableCondition.class)
@Requires(beans = OracleCloudBucketOperations.class)
final class OracleCloudBucketOperations
    implements BucketOperations<PutObjectRequest.Builder, PutObjectResponse, DeleteObjectResponse> {
    private final OracleCloudNamespaceConfiguration configuration;
    private final ObjectStorage client;
    private final RegionProvider regionProvider;

    OracleCloudBucketOperations(OracleCloudNamespaceConfiguration configuration, ObjectStorage client, RegionProvider regionProvider) {
        this.configuration = configuration;
        this.client = client;
        this.regionProvider = regionProvider;
    }

    @Override
    public Set<String> listBuckets() {
        ListBucketsResponse response = client.listBuckets(ListBucketsRequest.builder()
            .namespaceName(configuration.getNamespace())
            .build());
        return response.getItems().stream()
            .map(BucketSummary::getName)
            .collect(Collectors.toSet());
    }

    @Override
    public ObjectStorageOperations<PutObjectRequest.Builder, PutObjectResponse, DeleteObjectResponse> storageForBucket(String bucket) {
        Objects.requireNonNull(bucket, "bucket");
        OracleCloudStorageConfiguration cfg = new OracleCloudStorageConfiguration("");
        cfg.setNamespace(configuration.getNamespace());
        cfg.setBucket(bucket);
        return new OracleCloudStorageOperations(cfg, client, regionProvider);
    }
}
