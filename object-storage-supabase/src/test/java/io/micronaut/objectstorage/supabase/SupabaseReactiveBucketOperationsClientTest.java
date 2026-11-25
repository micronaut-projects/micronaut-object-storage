package io.micronaut.objectstorage.supabase;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.objectstorage.ObjectStorageException;
import io.micronaut.objectstorage.bucket.BucketEntry;
import io.micronaut.supabase.SupabaseConfigurationProperties;
import io.micronaut.supabase.storage.BucketSchema;
import io.micronaut.supabase.storage.CreateBucketRequest;
import io.micronaut.supabase.storage.CreateBucketResponse;
import io.micronaut.supabase.storage.MessageResponse;
import io.micronaut.supabase.storage.ReactiveSupabaseStorageClient;
import io.micronaut.supabase.storage.StorageErrorSchema;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Behaviour of {@link SupabaseReactiveBucketOperations} that depends on the reactive client responses rather than
 * on a live server: laziness, error mapping and the requests sent to Supabase Storage.
 */
class SupabaseReactiveBucketOperationsClientTest {

    private static final String SERVICE_KEY = "eyJ.service.role";
    private static final String SERVICE_AUTHORIZATION = "Bearer " + SERVICE_KEY;

    @Test
    void nothingIsSentUntilSubscriptionAndAMissingKeyFailsThePublisherInsteadOfThrowing() {
        ReactiveSupabaseStorageClient client = mock(ReactiveSupabaseStorageClient.class);
        SupabaseReactiveBucketOperations operations = operations(client, supabase(null));

        Publisher<Void> create = operations.create("avatars");
        verifyNoInteractions(client);

        ObjectStorageException e = assertThrows(ObjectStorageException.class, () -> Mono.from(create).block());

        assertTrue(e.getMessage().contains("supabase.secret-key"), e.getMessage());
        verifyNoInteractions(client);
    }

    @Test
    void bucketsAreCreatedPrivateWithoutLimits() {
        ReactiveSupabaseStorageClient client = mock(ReactiveSupabaseStorageClient.class);
        SupabaseReactiveBucketOperations operations = operations(client, supabase(SERVICE_KEY));
        when(client.createBucket(eq(SERVICE_AUTHORIZATION), any(CreateBucketRequest.class)))
            .thenReturn(Mono.just(HttpResponse.ok(new CreateBucketResponse("avatars"))));

        Mono.from(operations.create("avatars")).block();

        verify(client).createBucket(eq(SERVICE_AUTHORIZATION), argThat(request -> request != null
            && "avatars".equals(request.name()) && Boolean.FALSE.equals(request.isPublic())
            && request.fileSizeLimit() == null && request.allowedMimeTypes() == null));
    }

    @Test
    void retrieveAndExistsMapNotFoundToEmptyAndFalseAndOtherErrorsToSupabaseStorageException() {
        ReactiveSupabaseStorageClient client = mock(ReactiveSupabaseStorageClient.class);
        SupabaseReactiveBucketOperations operations = operations(client, supabase(SERVICE_KEY));
        BucketSchema avatars = new BucketSchema("avatars", "avatars", null, null, false, null, null, null, null, null, null);
        when(client.getBucket(SERVICE_AUTHORIZATION, "avatars")).thenReturn(Mono.just(HttpResponse.ok(avatars)));
        when(client.getBucket(SERVICE_AUTHORIZATION, "missing"))
            .thenReturn(Mono.error(error(HttpStatus.NOT_FOUND, "404", "BucketNotFound", "Bucket not found")));
        when(client.getBucket(SERVICE_AUTHORIZATION, "denied"))
            .thenReturn(Mono.error(error(HttpStatus.BAD_REQUEST, "403", "AccessDenied", "Access denied")));

        Optional<BucketEntry<BucketSchema>> entry = Mono.from(operations.retrieve("avatars")).block();
        assertEquals("avatars", entry.orElseThrow().name());
        assertEquals(avatars, entry.orElseThrow().nativeEntry());
        assertEquals(Boolean.TRUE, Mono.from(operations.exists("avatars")).block());

        assertTrue(Mono.from(operations.retrieve("missing")).block().isEmpty());
        assertEquals(Boolean.FALSE, Mono.from(operations.exists("missing")).block());

        SupabaseStorageException denied = assertThrows(SupabaseStorageException.class, () -> Mono.from(operations.retrieve("denied")).block());
        assertEquals(Optional.of("AccessDenied"), denied.getCode());
        assertThrows(SupabaseStorageException.class, () -> Mono.from(operations.exists("denied")).block());
    }

    @Test
    void deletingAMissingBucketFails() {
        ReactiveSupabaseStorageClient client = mock(ReactiveSupabaseStorageClient.class);
        SupabaseReactiveBucketOperations operations = operations(client, supabase(SERVICE_KEY));
        when(client.deleteBucket(SERVICE_AUTHORIZATION, "avatars")).thenReturn(Mono.just(HttpResponse.ok(new MessageResponse("Successfully deleted"))));
        when(client.deleteBucket(SERVICE_AUTHORIZATION, "missing"))
            .thenReturn(Mono.error(error(HttpStatus.NOT_FOUND, "404", "NoSuchBucket", "Bucket not found")));

        Mono.from(operations.delete("avatars")).block();
        SupabaseStorageException missing = assertThrows(SupabaseStorageException.class, () -> Mono.from(operations.delete("missing")).block());

        assertTrue(missing.hasCode(SupabaseStorageException.NO_SUCH_BUCKET));
        assertTrue(missing.isNotFound());
        assertEquals("Error when trying to delete a bucket with name [missing] on Supabase Storage: NoSuchBucket (Bucket not found)", missing.getMessage());
    }

    private static SupabaseReactiveBucketOperations operations(ReactiveSupabaseStorageClient client, SupabaseConfigurationProperties supabase) {
        return new SupabaseReactiveBucketOperations(new SupabaseStorageConfiguration("default"), client, supabase);
    }

    private static SupabaseConfigurationProperties supabase(@Nullable String secretKey) {
        SupabaseConfigurationProperties configuration = new SupabaseConfigurationProperties();
        configuration.setSecretKey(secretKey);
        return configuration;
    }

    private static HttpClientResponseException error(HttpStatus status, String statusCode, String code, String message) {
        return new HttpClientResponseException(message, HttpResponse.status(status).body(new StorageErrorSchema(statusCode, code, message, code)));
    }
}
