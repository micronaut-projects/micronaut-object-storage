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

import io.micronaut.objectstorage.ObjectStorageException;
import io.micronaut.objectstorage.request.CreatePresignedUploadRequest;
import io.micronaut.objectstorage.request.ListObjectsRequest;
import io.micronaut.objectstorage.request.UploadRequest;
import io.micronaut.objectstorage.response.ListObjectsResponse;
import io.micronaut.objectstorage.response.PresignedUpload;
import io.micronaut.objectstorage.response.UploadResponse;
import io.micronaut.supabase.storage.UploadObjectResponse;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Supabase-specific behaviour of {@link SupabaseStorageOperations} against the Storage container. The portable
 * contract is covered by the shared TCK in {@code SupabaseStorageOperationsSpec}.
 */
@MicronautTest(startApplication = false)
class SupabaseStorageOperationsTest extends AbstractSupabaseStorageTest {

    SupabaseStorageOperationsTest() {
        super("operations");
    }

    @Test
    void theUploadConsumerCustomizesTheSupabaseRequest() throws IOException {
        String key = "consumer/custom.txt";

        UploadResponse<UploadObjectResponse> response = supabaseStorage.upload(UploadRequest.fromBytes(TEXT.getBytes(StandardCharsets.UTF_8), key, CONTENT_TYPE), request -> {
            request.setCacheControl("max-age=60");
            request.setMetadata(Map.of("owner", "alice"));
        });
        SupabaseStorageEntry entry = supabaseStorage.retrieve(key).orElseThrow();

        assertEquals(key, response.getKey());
        assertFalse(response.getETag().isEmpty());
        assertEquals(bucketName + "/" + key, response.getNativeResponse().key());
        assertNotNull(response.getNativeResponse().id());
        assertEquals("max-age=60", entry.getNativeEntry().cacheControl());
        assertEquals(response.getETag(), entry.getNativeEntry().etag());
        assertEquals(Map.of("owner", "alice"), entry.getMetadata());
        assertEquals(CONTENT_TYPE, entry.getContentType().orElseThrow());
        assertEquals(TEXT, new String(entry.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        assertEquals(TEXT, new String(entry.getInputStream().readAllBytes(), StandardCharsets.UTF_8), "every call returns a fresh stream");
    }

    @Test
    void uploadsReplaceExistingObjectsUnlessTheConsumerDisablesUpsert() throws IOException {
        String key = "upsert/file.txt";
        supabaseStorage.upload(UploadRequest.fromBytes(TEXT.getBytes(StandardCharsets.UTF_8), key, CONTENT_TYPE));

        supabaseStorage.upload(UploadRequest.fromBytes(NEW_TEXT.getBytes(StandardCharsets.UTF_8), key, CONTENT_TYPE));
        assertEquals(NEW_TEXT, content(key));

        SupabaseStorageException e = assertThrows(SupabaseStorageException.class,
            () -> supabaseStorage.upload(UploadRequest.fromBytes(TEXT.getBytes(StandardCharsets.UTF_8), key, CONTENT_TYPE), request -> request.setUpsert(false)));
        assertTrue(e.hasCode(SupabaseStorageException.KEY_ALREADY_EXISTS));
        assertEquals(400, e.getStatus().getCode());
        assertEquals("409", e.getError().orElseThrow().statusCode());
        assertTrue(e.getMessage().contains(key), e.getMessage());
        assertEquals(NEW_TEXT, content(key));
    }

    @Test
    void deletingAMissingObjectIsNotAnError() {
        assertTrue(supabaseStorage.delete("missing/object.txt").isEmpty());
        assertFalse(supabaseStorage.exists("missing/object.txt"));
        assertTrue(supabaseStorage.retrieve("missing/object.txt").isEmpty());
    }

    @Test
    void copyOverwritesAnExistingDestination() throws IOException {
        supabaseStorage.upload(UploadRequest.fromBytes(TEXT.getBytes(StandardCharsets.UTF_8), "copy/source.txt", CONTENT_TYPE));
        supabaseStorage.upload(UploadRequest.fromBytes(NEW_TEXT.getBytes(StandardCharsets.UTF_8), "copy/destination.txt", CONTENT_TYPE));

        supabaseStorage.copy("copy/source.txt", "copy/destination.txt");

        assertEquals(TEXT, content("copy/destination.txt"));
        assertEquals(Set.of("copy/destination.txt", "copy/source.txt"), supabaseStorage.listObjects());

        SupabaseStorageException e = assertThrows(SupabaseStorageException.class, () -> supabaseStorage.copy("copy/missing.txt", "copy/other.txt"));
        assertTrue(e.hasCode(SupabaseStorageException.NO_SUCH_KEY));
    }

    @Test
    void listingIsFlatOrderedByNameAndBoundToTheRequestThatCreatedTheContinuationToken() {
        List<String> keys = List.of("b.txt", "a/1.txt", "a/b/2.txt", "a/b/c/3.txt", "c.txt");
        keys.forEach(key -> supabaseStorage.upload(UploadRequest.fromBytes(TEXT.getBytes(StandardCharsets.UTF_8), key, CONTENT_TYPE)));

        ListObjectsResponse firstPage = supabaseStorage.listObjects(new ListObjectsRequest(2, "a/"));
        String token = firstPage.getContinuationToken().orElseThrow();
        ListObjectsResponse secondPage = supabaseStorage.listObjects(new ListObjectsRequest(2, "a/", token));

        assertEquals(List.of("a/1.txt", "a/b/2.txt"), firstPage.getKeys());
        assertEquals(List.of("a/b/c/3.txt"), secondPage.getKeys());
        assertTrue(secondPage.getContinuationToken().isEmpty());
        assertEquals(List.of("a/1.txt", "a/b/2.txt", "a/b/c/3.txt", "b.txt", "c.txt"), List.copyOf(supabaseStorage.listObjects()));

        ObjectStorageException prefixMismatch = assertThrows(ObjectStorageException.class,
            () -> supabaseStorage.listObjects(new ListObjectsRequest(2, "b", token)));
        assertEquals("Supabase Storage continuation token does not match the current request", prefixMismatch.getMessage());

        ObjectStorageException pageSizeMismatch = assertThrows(ObjectStorageException.class,
            () -> supabaseStorage.listObjects(new ListObjectsRequest(3, "a/", token)));
        assertEquals("Supabase Storage continuation token does not match the current request", pageSizeMismatch.getMessage());

        ObjectStorageException invalid = assertThrows(ObjectStorageException.class,
            () -> supabaseStorage.listObjects(new ListObjectsRequest(2, "a/", "not-a-token")));
        assertEquals("Invalid Supabase Storage continuation token", invalid.getMessage());
    }

    @Test
    void thePreSignedUploadExpirationComesFromTheSignedToken() {
        CreatePresignedUploadRequest request = new CreatePresignedUploadRequest("presigned/expiry.txt", Duration.ofDays(30));

        PresignedUpload upload = supabaseStorage.createPresignedUpload(request).orElseThrow();

        // The server decides the validity: UPLOAD_SIGNED_URL_EXPIRATION_TIME is 120 seconds in the test container.
        assertEquals("PUT", upload.getMethod());
        assertTrue(upload.getUri().toString().startsWith(SupabaseStorageContainer.baseUrl() + "/object/upload/sign/" + bucketName + "/presigned/expiry.txt?token="), upload.getUri().toString());
        assertTrue(upload.getExpiration().isAfter(Instant.now()));
        assertTrue(upload.getExpiration().isBefore(Instant.now().plus(Duration.ofMinutes(3))));
        assertTrue(upload.getHeaders().isEmpty());
    }

    private String content(String key) throws IOException {
        return new String(supabaseStorage.retrieve(key).orElseThrow().getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }
}
