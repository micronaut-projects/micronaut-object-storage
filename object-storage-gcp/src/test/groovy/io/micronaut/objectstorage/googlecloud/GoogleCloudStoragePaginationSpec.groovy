package io.micronaut.objectstorage.googlecloud

import io.micronaut.objectstorage.request.ListObjectsRequest
import io.micronaut.objectstorage.response.ListObjectsResponse
import io.micronaut.test.extensions.spock.annotation.MicronautTest

@MicronautTest
@io.micronaut.context.annotation.Property(name = 'spec.name', value = GoogleCloudStorageFakeGcsServerSpec.SPEC_NAME)
class GoogleCloudStoragePaginationSpec extends AbstractGoogleCloudStorageSpec {

    private static final List<String> ANIMAL_KEYS = [
            'animals/antelope.txt',
            'animals/bear.txt',
            'animals/cat.txt',
    ]
    private static final List<String> PLANT_KEYS = [
            'plants/aloe.txt',
            'plants/bonsai.txt',
    ]
    private static final List<String> ALL_KEYS = ANIMAL_KEYS + PLANT_KEYS

    void 'paginated listing replays native GCP page tokens until the terminal page'() {
        given:
        ALL_KEYS.each { key ->
            cloudObjectStorage.upload(createTestFileForKey(key).uploadRequest)
        }

        when:
        ListObjectsResponse firstPage = cloudObjectStorage.listObjects(new ListObjectsRequest(2, 'animals/'))
        String nativeToken = firstPage.continuationToken.orElseThrow()
        ListObjectsResponse replayedFirstPage = cloudObjectStorage.listObjects(new ListObjectsRequest(2, 'animals/'))
        ListObjectsResponse replayedSecondPage = cloudObjectStorage.listObjects(new ListObjectsRequest(2, 'animals/', nativeToken))
        ListObjectsResponse secondPage = cloudObjectStorage.listObjects(new ListObjectsRequest(2, 'animals/', nativeToken))

        then:
        firstPage.keys == ANIMAL_KEYS.take(2)
        firstPage.continuationToken.present
        replayedFirstPage.keys == firstPage.keys
        replayedFirstPage.continuationToken == firstPage.continuationToken
        replayedSecondPage.keys == ANIMAL_KEYS.drop(2)
        !replayedSecondPage.continuationToken.present
        secondPage.keys == replayedSecondPage.keys
        secondPage.continuationToken == replayedSecondPage.continuationToken

        when:
        Set<String> allObjects = cloudObjectStorage.listObjects()

        then:
        allObjects instanceof LinkedHashSet
        new ArrayList<>(allObjects) == ALL_KEYS

        cleanup:
        ALL_KEYS.each { key ->
            cloudObjectStorage.delete(key)
        }
    }
}
