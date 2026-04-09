/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.objectstorage.aws;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.objectstorage.InputStreamMapper;
import io.micronaut.objectstorage.MultipartObjectStorageOperations;
import io.micronaut.objectstorage.MultipartPart;
import io.micronaut.objectstorage.MultipartUploadHandle;
import io.micronaut.objectstorage.ObjectStorageException;
import io.micronaut.objectstorage.ObjectStorageOperations;
import io.micronaut.objectstorage.configuration.ToggeableCondition;
import io.micronaut.objectstorage.request.AbortMultipartUploadRequest;
import io.micronaut.objectstorage.request.BytesUploadRequest;
import io.micronaut.objectstorage.request.CompleteMultipartUploadRequest;
import io.micronaut.objectstorage.request.CreateMultipartUploadRequest;
import io.micronaut.objectstorage.request.CreatePresignedUploadRequest;
import io.micronaut.objectstorage.request.FileUploadRequest;
import io.micronaut.objectstorage.request.ListMultipartPartsRequest;
import io.micronaut.objectstorage.request.ListObjectsRequest;
import io.micronaut.objectstorage.request.UploadPartRequest;
import io.micronaut.objectstorage.request.UploadRequest;
import io.micronaut.objectstorage.response.CompleteMultipartUploadResponse;
import io.micronaut.objectstorage.response.CreateMultipartUploadResponse;
import io.micronaut.objectstorage.response.ListMultipartPartsResponse;
import io.micronaut.objectstorage.response.ListObjectsResponse;
import io.micronaut.objectstorage.response.PresignedUpload;
import io.micronaut.objectstorage.response.UploadPartResponse;
import io.micronaut.objectstorage.response.UploadResponse;
import org.jspecify.annotations.NonNull;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.NoSuchUploadException;
import software.amazon.awssdk.services.s3.model.Part;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Consumer;

/**
 * AWS implementation of {@link ObjectStorageOperations}.
 *
 * @author Pavol Gressa
 * @since 1.0
 */
@EachBean(AwsS3Configuration.class)
@Requires(condition = ToggeableCondition.class)
@Requires(beans = AwsS3Configuration.class)
public class AwsS3Operations implements ObjectStorageOperations<
    PutObjectRequest.Builder, PutObjectResponse, DeleteObjectResponse>, MultipartObjectStorageOperations<
    software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse,
    software.amazon.awssdk.services.s3.model.UploadPartResponse,
    software.amazon.awssdk.services.s3.model.CompleteMultipartUploadResponse> {

    private static final int DEFAULT_LIST_PAGE_SIZE = 1_000;
    private static final String LEGACY_CONTINUATION_TOKEN_PREFIX = "aws-s3:v1:";
    private static final String RAW_CONTINUATION_TOKEN_PREFIX = "aws-s3:v2:";
    private static final String CONTINUATION_TOKEN_PREFIX = "aws-s3:v3:";
    private static final String MULTIPART_CONTINUATION_TOKEN_PREFIX = "aws-s3-multipart:v1:";

    private final S3Client s3Client;
    private final AwsS3Configuration configuration;
    private final InputStreamMapper inputStreamMapper;

    /**
     *
     * @param configuration AWS S3 Configuration
     * @param s3Client S3 Client
     * @param inputStreamMapper InputStream Mapper
     */
    public AwsS3Operations(@Parameter AwsS3Configuration configuration,
                           S3Client s3Client,
                           InputStreamMapper inputStreamMapper) {
        this.s3Client = s3Client;
        this.configuration = configuration;
        this.inputStreamMapper = inputStreamMapper;
    }

    @Override
    @NonNull
    public UploadResponse<PutObjectResponse> upload(@NonNull UploadRequest uploadRequest) {
        PutObjectRequest objectRequest = getRequestBuilder(uploadRequest).build();
        RequestBody requestBody = getRequestBody(uploadRequest);
        try {
            PutObjectResponse response = s3Client.putObject(objectRequest, requestBody);
            return UploadResponse.of(uploadRequest.getKey(), response.eTag(), response);
        } catch (AwsServiceException | SdkClientException e) {
            String msg = String.format("Error when trying to upload a file with key [%s] to Amazon S3", uploadRequest.getKey());
            throw new ObjectStorageException(msg, e);
        }
    }

    @Override
    @NonNull
    public UploadResponse<PutObjectResponse> upload(@NonNull UploadRequest request,
                                    @NonNull Consumer<PutObjectRequest.Builder> requestConsumer) {
        PutObjectRequest.Builder builder = getRequestBuilder(request);
        requestConsumer.accept(builder);
        RequestBody requestBody = getRequestBody(request);
        try {
            PutObjectResponse response = s3Client.putObject(builder.build(), requestBody);
            return UploadResponse.of(request.getKey(), response.eTag(), response);
        } catch (AwsServiceException | SdkClientException e) {
            String msg = String.format("Error when trying to upload a file with key [%s] to AWS S3", request.getKey());
            throw new ObjectStorageException(msg, e);
        }
    }

    @Override
    @NonNull
    @SuppressWarnings("unchecked")
    public Optional<AwsS3ObjectStorageEntry> retrieve(@NonNull String key) {
        try {
            ResponseInputStream<GetObjectResponse> responseInputStream = s3Client.getObject(GetObjectRequest.builder()
                .bucket(configuration.getBucket())
                .key(key)
                .build());
            AwsS3ObjectStorageEntry entry = new AwsS3ObjectStorageEntry(key, responseInputStream);
            return Optional.of(entry);
        } catch (NoSuchKeyException noSuchKeyException) {
            return Optional.empty();
        } catch (AwsServiceException | SdkClientException e) {
            String msg = String.format("Error when trying to retrieve a file with key [%s] from Amazon S3", key);
            throw new ObjectStorageException(msg, e);
        }
    }

    @Override
    @NonNull
    public DeleteObjectResponse delete(@NonNull String key) {
        try {
            return s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(configuration.getBucket())
                .key(key)
                .build());
        } catch (AwsServiceException | SdkClientException e) {
            String msg = String.format("Error when trying to delete a file with key [%s] from Amazon S3", key);
            throw new ObjectStorageException(msg, e);
        }
    }

    @Override
    public boolean exists(@NonNull String key) {
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                .bucket(configuration.getBucket())
                .key(key)
                .build());
            return true;
        } catch (NoSuchKeyException noSuchKeyException) {
            return false;
        } catch (AwsServiceException | SdkClientException e) {
            String msg = String.format("Error when trying to check the existence of a file with key [%s] in Amazon S3", key);
            throw new ObjectStorageException(msg, e);
        }
    }

    @NonNull
    @Override
    public Set<String> listObjects() {
        String bucket = configuration.getBucket();
        ListObjectsRequest request = new ListObjectsRequest(DEFAULT_LIST_PAGE_SIZE);
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        try {
            String continuationToken;
            do {
                ListObjectsResponse response = listObjects(request);
                keys.addAll(response.getKeys());
                continuationToken = response.getContinuationToken().orElse(null);
                request = new ListObjectsRequest(DEFAULT_LIST_PAGE_SIZE, null, continuationToken);
            } while (continuationToken != null);
            return keys;
        } catch (NoSuchBucketException e) {
            return Collections.emptySet();
        } catch (AwsServiceException | SdkClientException e) {
            String msg = String.format("Error when listing the objects of the bucket [%s] in Amazon S3", bucket);
            throw new ObjectStorageException(msg, e);
        }
    }

    @Override
    @NonNull
    public ListObjectsResponse listObjects(@NonNull ListObjectsRequest request) {
        String bucket = configuration.getBucket();
        try {
            DecodedContinuationToken decodedToken = decodeContinuationToken(request);
            ListObjectsV2Request.Builder builder = ListObjectsV2Request.builder()
                .bucket(bucket)
                .maxKeys(request.getPageSize());
            request.getPrefix().ifPresent(builder::prefix);
            decodedToken.rawContinuationToken().ifPresent(builder::continuationToken);
            decodedToken.startAfter().ifPresent(builder::startAfter);

            ListObjectsV2Response response = s3Client.listObjectsV2(builder.build());
            List<String> keys = response.contents().stream().map(S3Object::key).toList();
            return new ListObjectsResponse(
                keys,
                encodeContinuationToken(request, response, keys)
            );
        } catch (NoSuchBucketException e) {
            return new ListObjectsResponse(Collections.emptyList());
        } catch (AwsServiceException | SdkClientException e) {
            String msg = String.format("Error when listing the objects of the bucket [%s] in Amazon S3", bucket);
            throw new ObjectStorageException(msg, e);
        }
    }

    @Override
    public void copy(@NonNull String sourceKey, @NonNull String destinationKey) {
        try {
            s3Client.copyObject(CopyObjectRequest.builder()
                .sourceBucket(configuration.getBucket())
                .destinationBucket(configuration.getBucket())
                .sourceKey(sourceKey)
                .destinationKey(destinationKey)
                .build());
        } catch (AwsServiceException | SdkClientException e) {
            String msg = String.format("Error when trying to copy a file from key [%s] to key [%s] in Amazon S3", sourceKey, destinationKey);
            throw new ObjectStorageException(msg, e);
        }
    }

    @Override
    @NonNull
    public Optional<PresignedUpload> createPresignedUpload(@NonNull CreatePresignedUploadRequest request) {
        try {
            PresignedPutObjectRequest presignedRequest;
            try (S3Presigner s3Presigner = createS3Presigner()) {
                presignedRequest = s3Presigner.presignPutObject(builder -> builder
                    .signatureDuration(request.getExpiresIn())
                    .putObjectRequest(getPresignedUploadRequestBuilder(request).build()));
            }
            return Optional.of(new PresignedUpload(
                java.net.URI.create(presignedRequest.url().toString()),
                presignedRequest.httpRequest().method().name(),
                toPortableHeaders(presignedRequest.signedHeaders()),
                presignedRequest.expiration()
            ));
        } catch (RuntimeException e) {
            String msg = String.format(
                "Error when trying to create a pre-signed upload request with key [%s] in Amazon S3",
                request.getKey()
            );
            throw new ObjectStorageException(msg, e);
        }
    }

    @Override
    @NonNull
    public CreateMultipartUploadResponse<software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse> createMultipartUpload(
        @NonNull CreateMultipartUploadRequest request) {
        software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest.Builder builder =
            software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest.builder()
                .bucket(configuration.getBucket())
                .key(request.getKey());
        request.getContentType().ifPresent(builder::contentType);
        if (CollectionUtils.isNotEmpty(request.getMetadata())) {
            builder.metadata(request.getMetadata());
        }
        try {
            software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse response =
                s3Client.createMultipartUpload(builder.build());
            return CreateMultipartUploadResponse.of(
                new MultipartUploadHandle(request.getKey(), response.uploadId()),
                response
            );
        } catch (AwsServiceException | SdkClientException e) {
            String msg = String.format("Error when trying to create a multipart upload with key [%s] in Amazon S3", request.getKey());
            throw new ObjectStorageException(msg, e);
        }
    }

    @Override
    @NonNull
    public UploadPartResponse<software.amazon.awssdk.services.s3.model.UploadPartResponse> uploadPart(@NonNull UploadPartRequest request) {
        MultipartUploadHandle upload = request.getUpload();
        RequestBodyWithSize requestBody = getMultipartRequestBody(request.getUploadRequest());
        software.amazon.awssdk.services.s3.model.UploadPartRequest.Builder builder =
            software.amazon.awssdk.services.s3.model.UploadPartRequest.builder()
                .bucket(configuration.getBucket())
                .key(upload.getKey())
                .uploadId(upload.getUploadId())
                .partNumber(request.getPartNumber())
                .contentLength(requestBody.size());
        try {
            software.amazon.awssdk.services.s3.model.UploadPartResponse response =
                s3Client.uploadPart(builder.build(), requestBody.requestBody());
            MultipartPart part = new MultipartPart(
                request.getPartNumber(),
                response.eTag(),
                requestBody.size(),
                checksumFromUploadPartResponse(response)
            );
            return UploadPartResponse.of(part, response);
        } catch (AwsServiceException | SdkClientException e) {
            String msg = String.format(
                "Error when trying to upload part [%d] for multipart upload [%s] in Amazon S3",
                request.getPartNumber(),
                upload.getUploadId()
            );
            throw new ObjectStorageException(msg, e);
        }
    }

    @Override
    @NonNull
    public ListMultipartPartsResponse listParts(@NonNull ListMultipartPartsRequest request) {
        MultipartUploadHandle upload = request.getUpload();
        try {
            software.amazon.awssdk.services.s3.model.ListPartsRequest.Builder builder =
                software.amazon.awssdk.services.s3.model.ListPartsRequest.builder()
                    .bucket(configuration.getBucket())
                    .key(upload.getKey())
                    .uploadId(upload.getUploadId())
                    .maxParts(request.getPageSize());
            decodeMultipartContinuationToken(request).ifPresent(builder::partNumberMarker);

            software.amazon.awssdk.services.s3.model.ListPartsResponse response = s3Client.listParts(builder.build());
            List<MultipartPart> parts = response.parts().stream().map(this::toMultipartPart).toList();
            return new ListMultipartPartsResponse(parts, encodeMultipartContinuationToken(response.nextPartNumberMarker()));
        } catch (AwsServiceException | SdkClientException e) {
            String msg = String.format("Error when trying to list parts for multipart upload [%s] in Amazon S3", upload.getUploadId());
            throw new ObjectStorageException(msg, e);
        }
    }

    @Override
    @NonNull
    public CompleteMultipartUploadResponse<software.amazon.awssdk.services.s3.model.CompleteMultipartUploadResponse> completeMultipartUpload(
        @NonNull CompleteMultipartUploadRequest request) {
        MultipartUploadHandle upload = request.getUpload();
        CompletedMultipartUpload completedMultipartUpload = CompletedMultipartUpload.builder()
            .parts(request.getParts().stream().map(part -> CompletedPart.builder()
                .partNumber(part.getPartNumber())
                .eTag(part.getETag())
                .build()).toList())
            .build();
        try {
            software.amazon.awssdk.services.s3.model.CompleteMultipartUploadResponse response = s3Client.completeMultipartUpload(
                software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest.builder()
                    .bucket(configuration.getBucket())
                    .key(upload.getKey())
                    .uploadId(upload.getUploadId())
                    .multipartUpload(completedMultipartUpload)
                    .build()
            );
            return CompleteMultipartUploadResponse.of(upload, response.eTag(), response);
        } catch (AwsServiceException | SdkClientException e) {
            String msg = String.format("Error when trying to complete multipart upload [%s] in Amazon S3", upload.getUploadId());
            throw new ObjectStorageException(msg, e);
        }
    }

    @Override
    public void abortMultipartUpload(@NonNull AbortMultipartUploadRequest request) {
        MultipartUploadHandle upload = request.getUpload();
        try {
            s3Client.abortMultipartUpload(
                software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest.builder()
                    .bucket(configuration.getBucket())
                    .key(upload.getKey())
                    .uploadId(upload.getUploadId())
                    .build()
            );
        } catch (NoSuchUploadException ignored) {
            // Abort is required to be safe to retry.
        } catch (AwsServiceException | SdkClientException e) {
            String msg = String.format("Error when trying to abort multipart upload [%s] in Amazon S3", upload.getUploadId());
            throw new ObjectStorageException(msg, e);
        }
    }

    /**
     * @return the presigner used to create pre-signed upload requests.
     * @since 3.0.0
     */
    @NonNull
    protected S3Presigner createS3Presigner() {
        var clientConfiguration = s3Client.serviceClientConfiguration();
        S3Presigner.Builder builder = S3Presigner.builder().s3Client(s3Client);
        if (clientConfiguration.region() != null) {
            builder.region(clientConfiguration.region());
        }
        if (clientConfiguration.credentialsProvider() != null) {
            builder.credentialsProvider(clientConfiguration.credentialsProvider());
        }
        clientConfiguration.endpointOverride().ifPresent(builder::endpointOverride);
        return builder.build();
    }

    /**
     * @param request the upload request
     * @return An AWS' {@link PutObjectRequest.Builder} from a Micronaut's {@link UploadRequest}.
     */
    protected PutObjectRequest.@NonNull Builder getRequestBuilder(@NonNull UploadRequest request) {
        PutObjectRequest.Builder builder = PutObjectRequest.builder()
            .bucket(configuration.getBucket())
            .key(request.getKey());

        request.getContentType().ifPresent(builder::contentType);
        request.getContentSize().ifPresent(builder::contentLength);
        if (CollectionUtils.isNotEmpty(request.getMetadata())) {
            builder.metadata(request.getMetadata());
        }
        return builder;
    }

    /**
     * @param request the pre-signed upload request
     * @return An AWS' {@link PutObjectRequest.Builder} from a Micronaut pre-signed upload request.
     * @since 3.0.0
     */
    protected PutObjectRequest.@NonNull Builder getPresignedUploadRequestBuilder(@NonNull CreatePresignedUploadRequest request) {
        PutObjectRequest.Builder builder = PutObjectRequest.builder()
            .bucket(configuration.getBucket())
            .key(request.getKey());

        request.getContentType().ifPresent(builder::contentType);
        request.getContentLength().ifPresent(builder::contentLength);
        if (CollectionUtils.isNotEmpty(request.getMetadata())) {
            builder.metadata(request.getMetadata());
        }
        return builder;
    }

    /**
     * @param uploadRequest the upload request
     * @return An AWS' {@link RequestBody} from a Micronaut's {@link UploadRequest}.
     */
    @NonNull
    protected RequestBody getRequestBody(@NonNull UploadRequest uploadRequest) {
        if (uploadRequest instanceof FileUploadRequest) {
            FileUploadRequest request = (FileUploadRequest) uploadRequest;
            return RequestBody.fromFile(request.getFile());
        } else if (uploadRequest instanceof BytesUploadRequest) {
            BytesUploadRequest request = (BytesUploadRequest) uploadRequest;
            return RequestBody.fromBytes(request.getBytes());
        } else {
            return uploadRequest.getContentSize()
                .map(contentSize -> RequestBody.fromInputStream(uploadRequest.getInputStream(), contentSize))
                .orElseGet(() -> {
                    try (InputStream inputStream = uploadRequest.getInputStream()) {
                        return RequestBody.fromBytes(inputStreamMapper.toByteArray(inputStream));
                    } catch (IOException e) {
                        throw new ObjectStorageException("Error uploading file", e);
                    }
                });
        }
    }

    @NonNull
    private RequestBodyWithSize getMultipartRequestBody(@NonNull UploadRequest uploadRequest) {
        if (uploadRequest instanceof FileUploadRequest) {
            FileUploadRequest request = (FileUploadRequest) uploadRequest;
            long size = request.getContentSize()
                .orElseThrow(() -> new ObjectStorageException("Unable to determine multipart part size for file upload"));
            return new RequestBodyWithSize(RequestBody.fromFile(request.getFile()), size);
        } else if (uploadRequest instanceof BytesUploadRequest) {
            BytesUploadRequest request = (BytesUploadRequest) uploadRequest;
            return new RequestBodyWithSize(RequestBody.fromBytes(request.getBytes()), request.getBytes().length);
        } else {
            long size = uploadRequest.getContentSize()
                .orElseThrow(() -> new ObjectStorageException(
                    "Multipart uploads require UploadRequest#getContentSize() for streaming requests"
                ));
            return new RequestBodyWithSize(RequestBody.fromInputStream(uploadRequest.getInputStream(), size), size);
        }
    }

    private DecodedContinuationToken decodeContinuationToken(ListObjectsRequest request) {
        String continuationToken = request.getContinuationToken().orElse(null);
        if (continuationToken == null || continuationToken.isEmpty()) {
            return new DecodedContinuationToken(Optional.empty(), Optional.empty());
        }
        try {
            if (!continuationToken.startsWith(CONTINUATION_TOKEN_PREFIX)) {
                if (continuationToken.startsWith(LEGACY_CONTINUATION_TOKEN_PREFIX)) {
                    String encodedToken = continuationToken.substring(LEGACY_CONTINUATION_TOKEN_PREFIX.length());
                    return new DecodedContinuationToken(Optional.of(new String(Base64.getUrlDecoder().decode(encodedToken), StandardCharsets.UTF_8)), Optional.empty());
                }
                if (continuationToken.startsWith(RAW_CONTINUATION_TOKEN_PREFIX)) {
                    String encodedToken = continuationToken.substring(RAW_CONTINUATION_TOKEN_PREFIX.length());
                    return new DecodedContinuationToken(Optional.of(new String(Base64.getUrlDecoder().decode(encodedToken), StandardCharsets.UTF_8)), Optional.empty());
                }
                return new DecodedContinuationToken(Optional.of(continuationToken), Optional.empty());
            }
            String encodedToken = continuationToken.substring(CONTINUATION_TOKEN_PREFIX.length());
            String decodedToken = new String(Base64.getUrlDecoder().decode(encodedToken), StandardCharsets.UTF_8);
            String[] parts = decodedToken.split("\n", -1);
            if (parts.length != 3) {
                throw new ObjectStorageException("Invalid AWS S3 continuation token");
            }
            String expectedPrefix = request.getPrefix().orElse("");
            String expectedPageSize = Integer.toString(request.getPageSize());
            String prefix = decodeTokenPart(parts[0]);
            String pageSize = decodeTokenPart(parts[1]);
            String lastKey = decodeTokenPart(parts[2]);
            if (!expectedPrefix.equals(prefix) || !expectedPageSize.equals(pageSize)) {
                throw new ObjectStorageException("AWS S3 continuation token does not match the current request");
            }
            return new DecodedContinuationToken(Optional.empty(), Optional.of(lastKey));
        } catch (IllegalArgumentException e) {
            throw new ObjectStorageException("Invalid AWS S3 continuation token", e);
        }
    }

    private String encodeContinuationToken(ListObjectsRequest request,
                                           ListObjectsV2Response response,
                                           List<String> keys) {
        if (response.nextContinuationToken() == null || response.nextContinuationToken().isEmpty()) {
            return null;
        }
        if (keys.isEmpty()) {
            return null;
        }
        String payload = new StringJoiner("\n")
            .add(encodeTokenPart(request.getPrefix().orElse("")))
            .add(encodeTokenPart(Integer.toString(request.getPageSize())))
            .add(encodeTokenPart(keys.get(keys.size() - 1)))
            .toString();
        return CONTINUATION_TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    private String encodeTokenPart(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String decodeTokenPart(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    @NonNull
    private Map<String, List<String>> toPortableHeaders(Map<String, List<String>> signedHeaders) {
        Map<String, List<String>> headers = new LinkedHashMap<>(signedHeaders.size());
        signedHeaders.forEach((name, values) -> headers.put(name, List.copyOf(values)));
        return headers;
    }

    private Optional<Integer> decodeMultipartContinuationToken(ListMultipartPartsRequest request) {
        String continuationToken = request.getContinuationToken().orElse(null);
        if (continuationToken == null || continuationToken.isEmpty()) {
            return Optional.empty();
        }
        try {
            String rawToken = continuationToken;
            if (continuationToken.startsWith(MULTIPART_CONTINUATION_TOKEN_PREFIX)) {
                rawToken = new String(
                    Base64.getUrlDecoder().decode(continuationToken.substring(MULTIPART_CONTINUATION_TOKEN_PREFIX.length())),
                    StandardCharsets.UTF_8
                );
            }
            return Optional.of(Integer.parseInt(rawToken));
        } catch (IllegalArgumentException e) {
            throw new ObjectStorageException("Invalid AWS S3 multipart continuation token", e);
        }
    }

    private String encodeMultipartContinuationToken(Integer nextPartNumberMarker) {
        if (nextPartNumberMarker == null || nextPartNumberMarker <= 0) {
            return null;
        }
        return MULTIPART_CONTINUATION_TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding()
            .encodeToString(Integer.toString(nextPartNumberMarker).getBytes(StandardCharsets.UTF_8));
    }

    private MultipartPart toMultipartPart(Part part) {
        return new MultipartPart(
            part.partNumber(),
            part.eTag(),
            part.size(),
            firstNonEmpty(part.checksumCRC32(), part.checksumCRC32C(), part.checksumSHA1(), part.checksumSHA256())
        );
    }

    private String checksumFromUploadPartResponse(software.amazon.awssdk.services.s3.model.UploadPartResponse response) {
        return firstNonEmpty(response.checksumCRC32(), response.checksumCRC32C(), response.checksumSHA1(), response.checksumSHA256());
    }

    private String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return null;
    }

    private record DecodedContinuationToken(Optional<String> rawContinuationToken,
                                            Optional<String> startAfter) {
    }

    private record RequestBodyWithSize(RequestBody requestBody, long size) {
    }
}
