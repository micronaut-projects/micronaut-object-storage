package io.micronaut.objectstorage.supabase;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.json.JsonMapper;
import io.micronaut.objectstorage.DefaultInputStreamMapper;
import io.micronaut.objectstorage.ObjectStorageEntry;
import io.micronaut.objectstorage.ObjectStorageException;
import io.micronaut.objectstorage.request.ListObjectsRequest;
import io.micronaut.objectstorage.request.UploadRequest;
import io.micronaut.objectstorage.response.ListObjectsResponse;
import io.micronaut.objectstorage.response.UploadResponse;
import io.micronaut.supabase.SupabaseConfigurationProperties;
import io.micronaut.supabase.storage.CopyObjectRequest;
import io.micronaut.supabase.storage.CopyObjectResponse;
import io.micronaut.supabase.storage.DeleteObjectsRequest;
import io.micronaut.supabase.storage.ListObjectsV2Request;
import io.micronaut.supabase.storage.ListObjectsV2Response;
import io.micronaut.supabase.storage.ObjectInfoSchema;
import io.micronaut.supabase.storage.ObjectSchema;
import io.micronaut.supabase.storage.ReactiveSupabaseStorageClient;
import io.micronaut.supabase.storage.StorageErrorSchema;
import io.micronaut.supabase.storage.UploadObjectResponse;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
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
 * Behaviour of {@link SupabaseStorageReactiveOperations} that depends on the reactive client responses rather than
 * on a live server: laziness, credential selection, error mapping and the requests sent to Supabase Storage.
 */
class SupabaseStorageReactiveOperationsClientTest {

    private static final String BUCKET = "avatars";
    private static final String SERVICE_KEY = "eyJ.service.role";
    private static final String PUBLISHABLE_KEY = "sb_publishable_fixture";
    private static final String SERVICE_AUTHORIZATION = "Bearer " + SERVICE_KEY;
    private static final String BLOCKING_THREAD = "object-storage-blocking";
    private static final ExecutorService BLOCKING_EXECUTOR = Executors.newSingleThreadExecutor(r -> new Thread(r, BLOCKING_THREAD));

    @AfterAll
    static void shutdownExecutor() {
        BLOCKING_EXECUTOR.shutdownNow();
    }

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
        assertEquals(expected, operations(mock(ReactiveSupabaseStorageClient.class), supabase(secretKey, publishableKey)).getAuthorization());
    }

    @Test
    void nothingIsSentUntilSubscriptionAndAMissingKeyFailsThePublisherInsteadOfThrowing() {
        ReactiveSupabaseStorageClient client = mock(ReactiveSupabaseStorageClient.class);
        SupabaseStorageReactiveOperations operations = operations(client, supabase(null, null));

        Publisher<Boolean> exists = operations.exists("a.txt");
        verifyNoInteractions(client);

        ObjectStorageException e = assertThrows(ObjectStorageException.class, () -> Mono.from(exists).block());

        assertTrue(e.getMessage().contains("supabase.secret-key"), e.getMessage());
        verifyNoInteractions(client);
    }

    @Test
    void eachSubscriptionSendsTheRequestAgain() {
        ReactiveSupabaseStorageClient client = mock(ReactiveSupabaseStorageClient.class);
        SupabaseStorageReactiveOperations operations = operations(client, supabase(SERVICE_KEY, null));
        when(client.getObjectInfo(SERVICE_AUTHORIZATION, BUCKET, "a.txt", null))
            .thenReturn(Mono.just(HttpResponse.ok(info("a.txt", "etag-1"))));

        Publisher<Boolean> exists = operations.exists("a.txt");
        verifyNoInteractions(client);

        assertEquals(Boolean.TRUE, Mono.from(exists).block());
        assertEquals(Boolean.TRUE, Mono.from(exists).block());
        verify(client, times(2)).getObjectInfo(SERVICE_AUTHORIZATION, BUCKET, "a.txt", null);
    }

    @Test
    void uploadsSendTheContentTypeUpsertAndBase64JsonMetadataAndReadTheEntityTagFromTheObjectInfo() {
        ReactiveSupabaseStorageClient client = mock(ReactiveSupabaseStorageClient.class);
        SupabaseStorageReactiveOperations operations = operations(client, supabase(SERVICE_KEY, null));
        byte[] body = "hello".getBytes(StandardCharsets.UTF_8);
        UploadRequest request = UploadRequest.fromBytes(body, "folder/hello.txt", "text/plain");
        request.setMetadata(Map.of("owner", "alice"));
        when(client.uploadObject(eq(SERVICE_AUTHORIZATION), eq(BUCKET), eq("folder/hello.txt"), eq("text/plain"), isNull(), eq(true), anyString(), eq(body)))
            .thenReturn(Mono.just(HttpResponse.ok(new UploadObjectResponse("object-id", BUCKET + "/folder/hello.txt"))));
        when(client.getObjectInfo(SERVICE_AUTHORIZATION, BUCKET, "folder/hello.txt", null))
            .thenReturn(Mono.just(HttpResponse.ok(info("folder/hello.txt", "etag-1"))));

        UploadResponse<UploadObjectResponse> response = Mono.from(operations.upload(request)).block();

        ArgumentCaptor<String> metadata = ArgumentCaptor.forClass(String.class);
        verify(client).uploadObject(eq(SERVICE_AUTHORIZATION), eq(BUCKET), eq("folder/hello.txt"), eq("text/plain"), isNull(), eq(true), metadata.capture(), eq(body));
        assertEquals("{\"owner\":\"alice\"}", new String(Base64.getDecoder().decode(metadata.getValue()), StandardCharsets.UTF_8));
        assertEquals("folder/hello.txt", response.getKey());
        assertEquals("etag-1", response.getETag());
        assertEquals("object-id", response.getNativeResponse().id());
    }

    @Test
    void theRequestConsumerCanChangeTheRequestBeforeItIsSent() {
        ReactiveSupabaseStorageClient client = mock(ReactiveSupabaseStorageClient.class);
        SupabaseStorageReactiveOperations operations = operations(client, supabase(SERVICE_KEY, null));
        byte[] body = "hello".getBytes(StandardCharsets.UTF_8);
        when(client.uploadObject(eq(SERVICE_AUTHORIZATION), eq("other"), eq("hello.bin"), eq("application/octet-stream"), eq("max-age=60"), eq(false), isNull(), eq(body)))
            .thenReturn(Mono.just(HttpResponse.ok(new UploadObjectResponse("object-id", "other/hello.txt"))));
        when(client.getObjectInfo(SERVICE_AUTHORIZATION, "other", "hello.bin", null))
            .thenReturn(Mono.just(HttpResponse.ok(info("hello.bin", "etag-2"))));

        UploadResponse<UploadObjectResponse> response = Mono.from(operations.upload(UploadRequest.fromBytes(body, "hello.bin"), supabaseRequest -> {
            supabaseRequest.setBucketName("other");
            supabaseRequest.setCacheControl("max-age=60");
            supabaseRequest.setUpsert(false);
        })).block();

        assertEquals("etag-2", response.getETag());
        verify(client).uploadObject(eq(SERVICE_AUTHORIZATION), eq("other"), eq("hello.bin"), eq("application/octet-stream"), eq("max-age=60"), eq(false), isNull(), eq(body));
    }

    @Test
    void streamedContentIsReadOnTheBlockingExecutor() {
        ReactiveSupabaseStorageClient client = mock(ReactiveSupabaseStorageClient.class);
        SupabaseStorageReactiveOperations operations = operations(client, supabase(SERVICE_KEY, null));
        byte[] body = "streamed".getBytes(StandardCharsets.UTF_8);
        AtomicReference<String> readingThread = new AtomicReference<>();
        InputStream inputStream = new ByteArrayInputStream(body) {
            @Override
            public int read(byte[] b, int off, int len) {
                readingThread.set(Thread.currentThread().getName());
                return super.read(b, off, len);
            }
        };
        when(client.uploadObject(eq(SERVICE_AUTHORIZATION), eq(BUCKET), eq("stream.txt"), anyString(), isNull(), eq(true), isNull(), eq(body)))
            .thenReturn(Mono.just(HttpResponse.ok(new UploadObjectResponse("object-id", BUCKET + "/stream.txt"))));
        when(client.getObjectInfo(SERVICE_AUTHORIZATION, BUCKET, "stream.txt", null))
            .thenReturn(Mono.just(HttpResponse.ok(info("stream.txt", "etag-1"))));

        Publisher<UploadResponse<UploadObjectResponse>> upload = operations.upload(UploadRequest.fromInputStream(inputStream, "stream.txt"));
        assertEquals(null, readingThread.get());

        assertEquals("etag-1", Mono.from(upload).block().getETag());
        assertEquals(BLOCKING_THREAD, readingThread.get());
    }

    static Stream<Arguments> eTagFallbacks() {
        return Stream.of(Arguments.of("v-1", "v-1"), Arguments.of(null, "object-id"));
    }

    @ParameterizedTest
    @MethodSource("eTagFallbacks")
    void theEntityTagFallsBackToTheVersionAndThenTheObjectId(@Nullable String version, String expected) {
        ReactiveSupabaseStorageClient client = mock(ReactiveSupabaseStorageClient.class);
        SupabaseStorageReactiveOperations operations = operations(client, supabase(SERVICE_KEY, null));
        when(client.uploadObject(eq(SERVICE_AUTHORIZATION), eq(BUCKET), eq("hello.txt"), anyString(), isNull(), eq(true), isNull(), any()))
            .thenReturn(Mono.just(HttpResponse.ok(new UploadObjectResponse("object-id", BUCKET + "/hello.txt"))));
        when(client.getObjectInfo(SERVICE_AUTHORIZATION, BUCKET, "hello.txt", null))
            .thenReturn(Mono.just(HttpResponse.ok(new ObjectInfoSchema("object-id", "hello.txt", version, BUCKET, 5L, null, null, null, null, null, null, null, null, null, null))));

        UploadResponse<UploadObjectResponse> response = Mono.from(operations.upload(UploadRequest.fromBytes("hello".getBytes(StandardCharsets.UTF_8), "hello.txt"))).block();

        assertEquals(expected, response.getETag());
    }

    @Test
    void retrieveDownloadsTheContentAndFallsBackToTheResponseContentType() throws IOException {
        ReactiveSupabaseStorageClient client = mock(ReactiveSupabaseStorageClient.class);
        SupabaseStorageReactiveOperations operations = operations(client, supabase(SERVICE_KEY, null));
        byte[] body = "hello".getBytes(StandardCharsets.UTF_8);
        when(client.getObjectInfo(SERVICE_AUTHORIZATION, BUCKET, "hello.txt", null))
            .thenReturn(Mono.just(HttpResponse.ok(new ObjectInfoSchema("object-id", "hello.txt", "v-1", BUCKET, 5L, null, null, null, "etag-1", Map.of("owner", "alice"), null, null, null, false, false))));
        when(client.getObject(SERVICE_AUTHORIZATION, BUCKET, "hello.txt", null, null))
            .thenReturn(Mono.just(HttpResponse.ok(body).contentType(MediaType.TEXT_PLAIN_TYPE)));

        Optional<ObjectStorageEntry<?>> entry = Mono.from(operations.<ObjectStorageEntry<?>>retrieve("hello.txt")).block();

        assertEquals("hello.txt", entry.orElseThrow().getKey());
        assertEquals("hello", new String(entry.orElseThrow().getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        assertEquals(Optional.of("text/plain"), entry.orElseThrow().getContentType());
        assertEquals(Map.of("owner", "alice"), entry.orElseThrow().getMetadata());
        assertInstanceOf(SupabaseStorageEntry.class, entry.orElseThrow());
    }

    @Test
    void notFoundAnswersMapToEmptyResultsAndOtherErrorsToSupabaseStorageException() {
        ReactiveSupabaseStorageClient client = mock(ReactiveSupabaseStorageClient.class);
        SupabaseStorageReactiveOperations operations = operations(client, supabase(SERVICE_KEY, null));
        when(client.getObjectInfo(SERVICE_AUTHORIZATION, BUCKET, "missing.txt", null))
            .thenReturn(Mono.error(error(HttpStatus.BAD_REQUEST, "404", "NoSuchKey", "Object not found")));
        when(client.getObjectInfo(SERVICE_AUTHORIZATION, BUCKET, "route.txt", null))
            .thenReturn(Mono.error(new HttpClientResponseException("Not Found", HttpResponse.notFound())));
        when(client.getObjectInfo(SERVICE_AUTHORIZATION, BUCKET, "value.txt", null))
            .thenReturn(Mono.just(HttpResponse.notFound()));
        when(client.getObjectInfo(SERVICE_AUTHORIZATION, BUCKET, "denied.txt", null))
            .thenReturn(Mono.error(error(HttpStatus.BAD_REQUEST, "403", "AccessDenied", "new row violates row-level security policy")));
        when(client.getObjectInfo(SERVICE_AUTHORIZATION, BUCKET, "server-error.txt", null))
            .thenReturn(Mono.just(HttpResponse.serverError()));
        when(client.getObjectInfo(SERVICE_AUTHORIZATION, BUCKET, "unreachable.txt", null))
            .thenReturn(Mono.error(new HttpClientException("Connect Error: Connection refused")));

        assertEquals(Boolean.FALSE, Mono.from(operations.exists("missing.txt")).block());
        assertTrue(Mono.from(operations.retrieve("missing.txt")).block().isEmpty());
        assertEquals(Boolean.FALSE, Mono.from(operations.exists("route.txt")).block());
        assertTrue(Mono.from(operations.retrieve("route.txt")).block().isEmpty());
        assertEquals(Boolean.FALSE, Mono.from(operations.exists("value.txt")).block());

        SupabaseStorageException denied = assertThrows(SupabaseStorageException.class, () -> Mono.from(operations.exists("denied.txt")).block());
        assertEquals(HttpStatus.BAD_REQUEST, denied.getStatus());
        assertEquals(Optional.of("AccessDenied"), denied.getCode());
        assertEquals("new row violates row-level security policy", denied.getError().orElseThrow().message());
        assertFalse(denied.isNotFound());
        assertEquals("Error when trying to check the existence of a file with key [denied.txt] in Supabase Storage: AccessDenied (new row violates row-level security policy)", denied.getMessage());
        assertInstanceOf(HttpClientResponseException.class, denied.getCause());

        SupabaseStorageException retrieval = assertThrows(SupabaseStorageException.class, () -> Mono.from(operations.retrieve("denied.txt")).block());
        assertTrue(retrieval.getMessage().startsWith("Error when trying to retrieve a file with key [denied.txt]"), retrieval.getMessage());

        SupabaseStorageException serverError = assertThrows(SupabaseStorageException.class, () -> Mono.from(operations.exists("server-error.txt")).block());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, serverError.getStatus());
        assertTrue(serverError.getError().isEmpty());
        assertTrue(serverError.getMessage().endsWith(": HTTP 500"), serverError.getMessage());

        ObjectStorageException unreachable = assertThrows(ObjectStorageException.class, () -> Mono.from(operations.exists("unreachable.txt")).block());
        assertFalse(unreachable instanceof SupabaseStorageException);
        assertInstanceOf(HttpClientException.class, unreachable.getCause());
    }

    @Test
    void deleteReportsTheDeletedObjectsAndAnEmptyListWhenTheServerSendsNoBody() {
        ReactiveSupabaseStorageClient client = mock(ReactiveSupabaseStorageClient.class);
        SupabaseStorageReactiveOperations operations = operations(client, supabase(SERVICE_KEY, null));
        when(client.deleteObjects(eq(SERVICE_AUTHORIZATION), eq(BUCKET), argThat(request -> request != null && List.of("a.txt").equals(request.prefixes()))))
            .thenReturn(Mono.just(HttpResponse.ok(List.of(object("a.txt")))));
        when(client.deleteObjects(eq(SERVICE_AUTHORIZATION), eq(BUCKET), argThat(request -> request != null && List.of("missing.txt").equals(request.prefixes()))))
            .thenReturn(Mono.just(HttpResponse.ok()));

        List<ObjectSchema> deleted = Mono.from(operations.delete("a.txt")).block();
        List<ObjectSchema> nothing = Mono.from(operations.delete("missing.txt")).block();

        assertEquals(List.of("a.txt"), deleted.stream().map(ObjectSchema::name).toList());
        assertTrue(nothing.isEmpty());
    }

    @Test
    void copyDeletesAnExistingDestinationBeforeCopyingAgain() {
        ReactiveSupabaseStorageClient client = mock(ReactiveSupabaseStorageClient.class);
        SupabaseStorageReactiveOperations operations = operations(client, supabase(SERVICE_KEY, null));
        CopyObjectResponse copied = new CopyObjectResponse("id", BUCKET + "/destination.txt", "destination.txt", BUCKET, null, null, null, "id", null, null, null, null, null, null, null, null, null);
        when(client.copyObject(eq(SERVICE_AUTHORIZATION), any(CopyObjectRequest.class)))
            .thenReturn(Mono.error(error(HttpStatus.BAD_REQUEST, "409", "KeyAlreadyExists", "The resource already exists")))
            .thenReturn(Mono.just(HttpResponse.ok(copied)));
        when(client.deleteObjects(eq(SERVICE_AUTHORIZATION), eq(BUCKET), any(DeleteObjectsRequest.class)))
            .thenReturn(Mono.just(HttpResponse.ok(List.of(object("destination.txt")))));

        Mono.from(operations.copy("source.txt", "destination.txt")).block();

        verify(client, times(2)).copyObject(eq(SERVICE_AUTHORIZATION), argThat(request ->
            request != null && BUCKET.equals(request.bucketId()) && "source.txt".equals(request.sourceKey())
                && BUCKET.equals(request.destinationBucket()) && "destination.txt".equals(request.destinationKey())
                && Boolean.TRUE.equals(request.copyMetadata())));
        verify(client).deleteObjects(eq(SERVICE_AUTHORIZATION), eq(BUCKET), argThat(request -> request != null && List.of("destination.txt").equals(request.prefixes())));
        verifyNoMoreInteractions(client);
    }

    @Test
    void otherCopyFailuresAreNotRetried() {
        ReactiveSupabaseStorageClient client = mock(ReactiveSupabaseStorageClient.class);
        SupabaseStorageReactiveOperations operations = operations(client, supabase(SERVICE_KEY, null));
        when(client.copyObject(eq(SERVICE_AUTHORIZATION), any(CopyObjectRequest.class)))
            .thenReturn(Mono.error(error(HttpStatus.BAD_REQUEST, "404", "NoSuchKey", "Object not found")));

        SupabaseStorageException e = assertThrows(SupabaseStorageException.class, () -> Mono.from(operations.copy("source.txt", "destination.txt")).block());

        assertTrue(e.hasCode(SupabaseStorageException.NO_SUCH_KEY));
        verify(client).copyObject(eq(SERVICE_AUTHORIZATION), any(CopyObjectRequest.class));
        verifyNoMoreInteractions(client);
    }

    @Test
    void listingEveryObjectFollowsTheSupabaseCursorWithFlatPagesOrderedByName() {
        ReactiveSupabaseStorageClient client = mock(ReactiveSupabaseStorageClient.class);
        SupabaseStorageReactiveOperations operations = operations(client, supabase(SERVICE_KEY, null));
        when(client.listObjectsV2(eq(SERVICE_AUTHORIZATION), eq(BUCKET), argThat(request -> request != null && request.cursor() == null)))
            .thenReturn(Mono.just(HttpResponse.ok(new ListObjectsV2Response(List.of(), List.of(object("a.txt"), object("b.txt")), true, "cursor-1", null))));
        when(client.listObjectsV2(eq(SERVICE_AUTHORIZATION), eq(BUCKET), argThat(request -> request != null && "cursor-1".equals(request.cursor()))))
            .thenReturn(Mono.just(HttpResponse.ok(new ListObjectsV2Response(List.of(), List.of(object("c.txt")), false, null, null))));

        Set<String> keys = Mono.from(operations.listObjects()).block();

        assertEquals(List.of("a.txt", "b.txt", "c.txt"), new ArrayList<>(keys));
        ArgumentCaptor<ListObjectsV2Request> requests = ArgumentCaptor.forClass(ListObjectsV2Request.class);
        verify(client, times(2)).listObjectsV2(eq(SERVICE_AUTHORIZATION), eq(BUCKET), requests.capture());
        for (ListObjectsV2Request request : requests.getAllValues()) {
            assertEquals(null, request.prefix());
            assertEquals(1_000, request.limit());
            assertEquals(Boolean.FALSE, request.withDelimiter());
            assertEquals("name", request.sortBy().column());
            assertEquals("asc", request.sortBy().order());
        }
    }

    @Test
    void pagedListingBindsTheContinuationTokenToThePrefixAndPageSize() {
        ReactiveSupabaseStorageClient client = mock(ReactiveSupabaseStorageClient.class);
        SupabaseStorageReactiveOperations operations = operations(client, supabase(SERVICE_KEY, null));
        when(client.listObjectsV2(eq(SERVICE_AUTHORIZATION), eq(BUCKET), argThat(request -> request != null && "animals/".equals(request.prefix()) && request.cursor() == null)))
            .thenReturn(Mono.just(HttpResponse.ok(new ListObjectsV2Response(List.of(), List.of(object("animals/cat.txt")), true, "cursor-1", null))));
        when(client.listObjectsV2(eq(SERVICE_AUTHORIZATION), eq(BUCKET), argThat(request -> request != null && "animals/".equals(request.prefix()) && "cursor-1".equals(request.cursor()))))
            .thenReturn(Mono.just(HttpResponse.ok(new ListObjectsV2Response(List.of(), List.of(object("animals/dog.txt")), false, null, null))));

        ListObjectsResponse first = Mono.from(operations.listObjects(new ListObjectsRequest(1, "animals/"))).block();
        String token = first.getContinuationToken().orElseThrow();
        ListObjectsResponse second = Mono.from(operations.listObjects(new ListObjectsRequest(1, "animals/", token))).block();

        assertEquals(List.of("animals/cat.txt"), first.getKeys());
        assertEquals(List.of("animals/dog.txt"), second.getKeys());
        assertTrue(second.getContinuationToken().isEmpty());

        Publisher<ListObjectsResponse> otherPrefix = operations.listObjects(new ListObjectsRequest(1, "plants/", token));
        Publisher<ListObjectsResponse> malformed = operations.listObjects(new ListObjectsRequest(1, "animals/", "bogus"));
        assertThrows(ObjectStorageException.class, () -> Mono.from(otherPrefix).block());
        assertThrows(ObjectStorageException.class, () -> Mono.from(malformed).block());
        verify(client, times(2)).listObjectsV2(eq(SERVICE_AUTHORIZATION), eq(BUCKET), any(ListObjectsV2Request.class));
    }

    @Test
    void listingAMissingBucketListsNothing() {
        ReactiveSupabaseStorageClient client = mock(ReactiveSupabaseStorageClient.class);
        SupabaseStorageReactiveOperations operations = operations(client, supabase(SERVICE_KEY, null));
        when(client.listObjectsV2(eq(SERVICE_AUTHORIZATION), eq(BUCKET), any(ListObjectsV2Request.class)))
            .thenReturn(Mono.error(error(HttpStatus.BAD_REQUEST, "404", "NoSuchBucket", "Bucket not found")));

        assertTrue(Mono.from(operations.listObjects()).block().isEmpty());
        ListObjectsResponse page = Mono.from(operations.listObjects(new ListObjectsRequest(10))).block();
        assertTrue(page.getKeys().isEmpty());
        assertTrue(page.getContinuationToken().isEmpty());
    }

    private static SupabaseStorageReactiveOperations operations(ReactiveSupabaseStorageClient client, SupabaseConfigurationProperties supabase) {
        SupabaseStorageConfiguration configuration = new SupabaseStorageConfiguration("default");
        configuration.setBucket(BUCKET);
        return new SupabaseStorageReactiveOperations(configuration, client, supabase, new DefaultInputStreamMapper(), JsonMapper.createDefault(), BLOCKING_EXECUTOR);
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

    private static ObjectSchema object(String name) {
        return new ObjectSchema(name, BUCKET, null, null, null, "id", null, null, null, null, null, null, null, null, null, null);
    }

    private static HttpClientResponseException error(HttpStatus status, String statusCode, String code, String message) {
        return new HttpClientResponseException(message, HttpResponse.status(status).body(new StorageErrorSchema(statusCode, code, message, code)));
    }
}
