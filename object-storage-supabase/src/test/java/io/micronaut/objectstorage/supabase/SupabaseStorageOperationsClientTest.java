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
package io.micronaut.objectstorage.supabase;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.json.JsonMapper;
import io.micronaut.objectstorage.DefaultInputStreamMapper;
import io.micronaut.objectstorage.ObjectStorageException;
import io.micronaut.objectstorage.request.CreatePresignedUploadRequest;
import io.micronaut.objectstorage.request.UploadRequest;
import io.micronaut.objectstorage.response.PresignedUpload;
import io.micronaut.objectstorage.response.UploadResponse;
import io.micronaut.supabase.SupabaseConfigurationProperties;
import io.micronaut.supabase.storage.CopyObjectRequest;
import io.micronaut.supabase.storage.CopyObjectResponse;
import io.micronaut.supabase.storage.DeleteObjectsRequest;
import io.micronaut.supabase.storage.ObjectInfoSchema;
import io.micronaut.supabase.storage.ObjectSchema;
import io.micronaut.supabase.storage.SignedUploadUrlResponse;
import io.micronaut.supabase.storage.StorageErrorSchema;
import io.micronaut.supabase.storage.SupabaseStorageClient;
import io.micronaut.supabase.storage.UploadObjectResponse;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Behaviour of {@link SupabaseStorageOperations} that depends on the client responses rather than on a live
 * server: credential selection, error mapping and the request parameters sent to Supabase Storage.
 */
class SupabaseStorageOperationsClientTest {

    private static final String BUCKET = "avatars";
    private static final String SERVICE_KEY = "eyJ.service.role";
    private static final String PUBLISHABLE_KEY = "sb_publishable_fixture";
    private static final String SERVICE_AUTHORIZATION = "Bearer " + SERVICE_KEY;

    static Stream<Arguments> keys() {
        return Stream.of(
            Arguments.of(SERVICE_KEY, PUBLISHABLE_KEY, SERVICE_AUTHORIZATION),
            Arguments.of(SERVICE_KEY, null, SERVICE_AUTHORIZATION),
            Arguments.of(null, PUBLISHABLE_KEY, "Bearer " + PUBLISHABLE_KEY),
            Arguments.of("", PUBLISHABLE_KEY, "Bearer " + PUBLISHABLE_KEY));
    }

    @ParameterizedTest
    @MethodSource("keys")
    void theSecretKeyIsPreferredAsBearerTokenThenThePublishableKey(@Nullable String secretKey, @Nullable String publishableKey, String expected) {
        assertEquals(expected, operations(mock(SupabaseStorageClient.class), supabase(secretKey, publishableKey)).getAuthorization());
    }

    @Test
    void withoutAnyKeyTheOperationsFailBeforeSendingARequest() {
        SupabaseStorageClient client = mock(SupabaseStorageClient.class);
        SupabaseStorageOperations operations = operations(client, supabase(null, null));

        ObjectStorageException e = assertThrows(ObjectStorageException.class, () -> operations.exists("a.txt"));

        assertTrue(e.getMessage().contains("supabase.secret-key"), e.getMessage());
        verifyNoInteractions(client);
    }

    @Test
    void uploadsSendTheContentTypeUpsertAndBase64JsonMetadataAndReadTheEntityTagFromTheObjectInfo() {
        SupabaseStorageClient client = mock(SupabaseStorageClient.class);
        SupabaseStorageOperations operations = operations(client, supabase(SERVICE_KEY, null));
        byte[] body = "hello".getBytes(StandardCharsets.UTF_8);
        UploadRequest request = UploadRequest.fromBytes(body, "folder/hello.txt", "text/plain");
        request.setMetadata(Map.of("owner", "alice"));
        when(client.uploadObject(eq(SERVICE_AUTHORIZATION), eq(BUCKET), eq("folder/hello.txt"), eq("text/plain"), isNull(), eq(true), anyString(), eq(body)))
            .thenReturn(HttpResponse.ok(new UploadObjectResponse("object-id", BUCKET + "/folder/hello.txt")));
        when(client.getObjectInfo(SERVICE_AUTHORIZATION, BUCKET, "folder/hello.txt", null))
            .thenReturn(HttpResponse.ok(info("folder/hello.txt", "etag-1")));

        UploadResponse<UploadObjectResponse> response = operations.upload(request);

        ArgumentCaptor<String> metadata = ArgumentCaptor.forClass(String.class);
        verify(client).uploadObject(eq(SERVICE_AUTHORIZATION), eq(BUCKET), eq("folder/hello.txt"), eq("text/plain"), isNull(), eq(true), metadata.capture(), eq(body));
        assertEquals("{\"owner\":\"alice\"}", new String(Base64.getDecoder().decode(metadata.getValue()), StandardCharsets.UTF_8));
        assertEquals("folder/hello.txt", response.getKey());
        assertEquals("etag-1", response.getETag());
        assertEquals("object-id", response.getNativeResponse().id());
    }

    static Stream<Arguments> eTagFallbacks() {
        return Stream.of(Arguments.of("v-1", "v-1"), Arguments.of(null, "object-id"));
    }

    @ParameterizedTest
    @MethodSource("eTagFallbacks")
    void theEntityTagFallsBackToTheVersionAndThenTheObjectId(@Nullable String version, String expected) {
        SupabaseStorageClient client = mock(SupabaseStorageClient.class);
        SupabaseStorageOperations operations = operations(client, supabase(SERVICE_KEY, null));
        when(client.uploadObject(eq(SERVICE_AUTHORIZATION), eq(BUCKET), eq("hello.txt"), anyString(), isNull(), eq(true), isNull(), any()))
            .thenReturn(HttpResponse.ok(new UploadObjectResponse("object-id", BUCKET + "/hello.txt")));
        when(client.getObjectInfo(SERVICE_AUTHORIZATION, BUCKET, "hello.txt", null))
            .thenReturn(HttpResponse.ok(new ObjectInfoSchema("object-id", "hello.txt", version, BUCKET, 5L, null, null, null, null, null, null, null, null, null, null)));

        UploadResponse<UploadObjectResponse> response = operations.upload(UploadRequest.fromBytes("hello".getBytes(StandardCharsets.UTF_8), "hello.txt"));

        assertEquals(expected, response.getETag());
    }

    @Test
    void notFoundAnswersMapToEmptyResultsAndOtherErrorsToSupabaseStorageException() {
        SupabaseStorageClient client = mock(SupabaseStorageClient.class);
        SupabaseStorageOperations operations = operations(client, supabase(SERVICE_KEY, null));
        when(client.getObjectInfo(SERVICE_AUTHORIZATION, BUCKET, "missing.txt", null))
            .thenThrow(error(HttpStatus.BAD_REQUEST, "404", "NoSuchKey", "Object not found"));
        when(client.getObjectInfo(SERVICE_AUTHORIZATION, BUCKET, "route.txt", null))
            .thenReturn(HttpResponse.notFound());
        when(client.getObjectInfo(SERVICE_AUTHORIZATION, BUCKET, "denied.txt", null))
            .thenThrow(error(HttpStatus.BAD_REQUEST, "403", "AccessDenied", "new row violates row-level security policy"));
        when(client.getObjectInfo(SERVICE_AUTHORIZATION, BUCKET, "server-error.txt", null))
            .thenReturn(HttpResponse.serverError());
        when(client.getObjectInfo(SERVICE_AUTHORIZATION, BUCKET, "unreachable.txt", null))
            .thenThrow(new HttpClientException("Connect Error: Connection refused"));

        assertFalse(operations.exists("missing.txt"));
        assertTrue(operations.retrieve("missing.txt").isEmpty());
        assertFalse(operations.exists("route.txt"));

        SupabaseStorageException denied = assertThrows(SupabaseStorageException.class, () -> operations.exists("denied.txt"));
        assertEquals(HttpStatus.BAD_REQUEST, denied.getStatus());
        assertEquals(Optional.of("AccessDenied"), denied.getCode());
        assertEquals("new row violates row-level security policy", denied.getError().orElseThrow().message());
        assertFalse(denied.isNotFound());
        assertEquals("Error when trying to check the existence of a file with key [denied.txt] in Supabase Storage: AccessDenied (new row violates row-level security policy)", denied.getMessage());
        assertInstanceOf(HttpClientResponseException.class, denied.getCause());

        SupabaseStorageException serverError = assertThrows(SupabaseStorageException.class, () -> operations.exists("server-error.txt"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, serverError.getStatus());
        assertTrue(serverError.getError().isEmpty());
        assertTrue(serverError.getMessage().endsWith(": HTTP 500"), serverError.getMessage());

        ObjectStorageException unreachable = assertThrows(ObjectStorageException.class, () -> operations.exists("unreachable.txt"));
        assertFalse(unreachable instanceof SupabaseStorageException);
        assertInstanceOf(HttpClientException.class, unreachable.getCause());
    }

    @Test
    void copyDeletesAnExistingDestinationBeforeCopyingAgain() {
        SupabaseStorageClient client = mock(SupabaseStorageClient.class);
        SupabaseStorageOperations operations = operations(client, supabase(SERVICE_KEY, null));
        CopyObjectResponse copied = new CopyObjectResponse("id", BUCKET + "/destination.txt", "destination.txt", BUCKET, null, null, null, "id", null, null, null, null, null, null, null, null, null);
        when(client.copyObject(eq(SERVICE_AUTHORIZATION), any(CopyObjectRequest.class)))
            .thenThrow(error(HttpStatus.BAD_REQUEST, "409", "KeyAlreadyExists", "The resource already exists"))
            .thenReturn(HttpResponse.ok(copied));
        when(client.deleteObjects(eq(SERVICE_AUTHORIZATION), eq(BUCKET), any(DeleteObjectsRequest.class)))
            .thenReturn(HttpResponse.ok(List.of(new ObjectSchema("destination.txt", BUCKET, null, null, null, "id", null, null, null, null, null, null, null, null, null, null))));

        operations.copy("source.txt", "destination.txt");

        verify(client, times(2)).copyObject(eq(SERVICE_AUTHORIZATION), argThat(request ->
            BUCKET.equals(request.bucketId()) && "source.txt".equals(request.sourceKey())
                && BUCKET.equals(request.destinationBucket()) && "destination.txt".equals(request.destinationKey())
                && Boolean.TRUE.equals(request.copyMetadata())));
        verify(client).deleteObjects(eq(SERVICE_AUTHORIZATION), eq(BUCKET), argThat(request -> List.of("destination.txt").equals(request.prefixes())));
        verifyNoMoreInteractions(client);
    }

    @Test
    void preSignedUploadsNeedAnAbsoluteStorageUrl() {
        SupabaseStorageClient client = mock(SupabaseStorageClient.class);
        SupabaseStorageOperations operations = operations(client, supabase(SERVICE_KEY, null));

        Optional<PresignedUpload> upload = operations.createPresignedUpload(new CreatePresignedUploadRequest("a.txt", Duration.ofMinutes(5)));

        assertTrue(upload.isEmpty());
        verifyNoInteractions(client);
    }

    @Test
    void preSignedUploadsResolveTheProjectUrlForwardTheContentTypeAndMetadataAndReadTheTokenExpiry() {
        SupabaseStorageClient client = mock(SupabaseStorageClient.class);
        SupabaseConfigurationProperties supabase = supabase(SERVICE_KEY, null);
        supabase.setUrl("https://abcdefghijklmnopqrst.supabase.co/");
        SupabaseStorageOperations operations = operations(client, supabase);
        CreatePresignedUploadRequest request = new CreatePresignedUploadRequest("avatars/alice.png", Duration.ofMinutes(5));
        request.setContentType("image/png");
        request.setMetadata(Map.of("owner", "alice"));
        long exp = Instant.now().plusSeconds(7200).getEpochSecond();
        String token = "eyJhbGciOiJIUzI1NiJ9." + base64Url("{\"url\":\"" + BUCKET + "/avatars/alice.png\",\"exp\":" + exp + "}") + ".signature";
        String signedPath = "/object/upload/sign/" + BUCKET + "/avatars/alice.png?token=" + token;
        when(client.signUploadUrl(SERVICE_AUTHORIZATION, BUCKET, "avatars/alice.png", true))
            .thenReturn(HttpResponse.ok(new SignedUploadUrlResponse(signedPath, token)));

        PresignedUpload upload = operations.createPresignedUpload(request).orElseThrow();

        assertEquals("PUT", upload.getMethod());
        assertEquals("https://abcdefghijklmnopqrst.supabase.co/storage/v1" + signedPath, upload.getUri().toString());
        assertEquals(List.of("image/png"), upload.getHeaders().get("Content-Type"));
        assertEquals("{\"owner\":\"alice\"}", new String(Base64.getDecoder().decode(upload.getHeaders().get("x-metadata").getFirst()), StandardCharsets.UTF_8));
        assertEquals(Instant.ofEpochSecond(exp), upload.getExpiration());
    }

    static Stream<String> tokensWithoutExpiry() {
        return Stream.of(null, "opaque", "a." + base64Url("{\"exp\":\"soon\"}") + ".c");
    }

    @ParameterizedTest
    @MethodSource("tokensWithoutExpiry")
    void theRequestedDurationIsUsedWhenTheTokenCarriesNoExpiry(@Nullable String token) {
        SupabaseStorageClient client = mock(SupabaseStorageClient.class);
        SupabaseConfigurationProperties supabase = supabase(SERVICE_KEY, null);
        supabase.setProjectId("abcdefghijklmnopqrst");
        SupabaseStorageOperations operations = operations(client, supabase);
        Instant before = Instant.now();
        when(client.signUploadUrl(SERVICE_AUTHORIZATION, BUCKET, "a.txt", true))
            .thenReturn(HttpResponse.ok(new SignedUploadUrlResponse("https://cdn.example.com/upload?token=" + token, token)));

        PresignedUpload upload = operations.createPresignedUpload(new CreatePresignedUploadRequest("a.txt", Duration.ofMinutes(5))).orElseThrow();

        assertEquals("https://cdn.example.com/upload?token=" + token, upload.getUri().toString());
        assertFalse(upload.getExpiration().isBefore(before.plus(Duration.ofMinutes(5))));
        assertTrue(upload.getHeaders().isEmpty());
    }

    private static SupabaseStorageOperations operations(SupabaseStorageClient client, SupabaseConfigurationProperties supabase) {
        SupabaseStorageConfiguration configuration = new SupabaseStorageConfiguration("default");
        configuration.setBucket(BUCKET);
        return new SupabaseStorageOperations(configuration, client, supabase, new DefaultInputStreamMapper(), JsonMapper.createDefault(), null);
    }

    private static SupabaseConfigurationProperties supabase(@Nullable String secretKey, @Nullable String publishableKey) {
        SupabaseConfigurationProperties configuration = new SupabaseConfigurationProperties();
        configuration.setSecretKey(secretKey);
        configuration.setPublishableKey(publishableKey);
        return configuration;
    }

    private static ObjectInfoSchema info(String name, String etag) {
        return new ObjectInfoSchema("object-id", name, "version-1", BUCKET, 5L, "text/plain", null, null, etag, Map.of("owner", "alice"), null, null, null, false, false);
    }

    private static HttpClientResponseException error(HttpStatus status, String statusCode, String code, String message) {
        return new HttpClientResponseException(message, HttpResponse.status(status).body(new StorageErrorSchema(statusCode, code, message, code)));
    }

    private static String base64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
