package io.micronaut.objectstorage.supabase;

import io.micronaut.objectstorage.ObjectStorageEntry;
import io.micronaut.objectstorage.ReactiveObjectStorageOperations;
import io.micronaut.objectstorage.bucket.ReactiveBucketOperations;
import io.micronaut.objectstorage.request.UploadRequest;
import io.micronaut.supabase.storage.BucketSchema;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the reactive beans are the native Supabase implementations and that they share the bucket with the
 * blocking ones against the Storage container. The portable reactive contract is covered by the shared TCK in
 * {@code SupabaseStorageReactiveOperationsSpec}.
 */
@MicronautTest(startApplication = false)
class SupabaseStorageReactiveOperationsTest extends AbstractSupabaseStorageTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Inject
    @Named("default")
    ReactiveObjectStorageOperations<?, ?, ?> reactiveStorage;

    @Inject
    @Named("default")
    ReactiveBucketOperations<?> reactiveBucketOperations;

    SupabaseStorageReactiveOperationsTest() {
        super("reactive");
    }

    @Test
    void theReactiveBeansAreTheNativeSupabaseImplementations() throws IOException {
        assertInstanceOf(SupabaseStorageReactiveOperations.class, reactiveStorage);
        assertInstanceOf(SupabaseReactiveBucketOperations.class, reactiveBucketOperations);
        assertEquals(bucketName, await(reactiveBucketOperations.retrieve(bucketName)).orElseThrow().name());

        await(reactiveStorage.upload(UploadRequest.fromBytes(TEXT.getBytes(StandardCharsets.UTF_8), "reactive.txt", CONTENT_TYPE)));
        Optional<? extends ObjectStorageEntry<?>> entry = await(reactiveStorage.retrieve("reactive.txt"));

        assertTrue(await(reactiveStorage.exists("reactive.txt")));
        assertEquals(TEXT, new String(entry.orElseThrow().getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        assertEquals(Optional.of(CONTENT_TYPE), entry.orElseThrow().getContentType());
        assertTrue(supabaseStorage.exists("reactive.txt"));

        await(reactiveStorage.copy("reactive.txt", "copied.txt"));
        await(reactiveStorage.copy("reactive.txt", "copied.txt"));
        assertTrue(supabaseStorage.exists("copied.txt"));

        assertFalse(await(reactiveStorage.delete("reactive.txt")).toString().isEmpty());
        assertFalse(supabaseStorage.exists("reactive.txt"));
    }

    @Test
    void bucketsCanBeManagedReactively() {
        String name = "micronaut-object-storage-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);

        assertFalse(await(reactiveBucketOperations.exists(name)));
        assertTrue(await(reactiveBucketOperations.retrieve(name)).isEmpty());

        await(reactiveBucketOperations.create(name));
        assertTrue(supabaseBucketOperations.exists(name));
        BucketSchema bucket = (BucketSchema) await(reactiveBucketOperations.retrieve(name)).orElseThrow().nativeEntry();
        assertEquals(name, bucket.name());
        assertEquals(Boolean.FALSE, bucket.isPublic());

        SupabaseStorageException duplicate = assertThrows(SupabaseStorageException.class, () -> await(reactiveBucketOperations.create(name)));
        assertTrue(duplicate.getMessage().contains(name), duplicate.getMessage());

        await(reactiveBucketOperations.delete(name));
        assertFalse(await(reactiveBucketOperations.exists(name)));
        assertFalse(supabaseBucketOperations.exists(name));

        SupabaseStorageException missing = assertThrows(SupabaseStorageException.class, () -> await(reactiveBucketOperations.delete(name)));
        assertTrue(missing.isNotFound());
    }

    private static <T> T await(Publisher<T> publisher) {
        return Mono.from(publisher).block(TIMEOUT);
    }
}
