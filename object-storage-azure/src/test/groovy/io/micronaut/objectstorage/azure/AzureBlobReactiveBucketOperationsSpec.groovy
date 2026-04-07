package io.micronaut.objectstorage.azure

import com.azure.storage.blob.models.BlobContainerProperties
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.time.OffsetDateTime

class AzureBlobReactiveBucketOperationsSpec extends Specification {

    void "reactive Azure bucket operations use async container clients"() {
        given:
        AzureBlobReactiveBucketOperations.AzureAsyncBucketClient client = Mock()
        BlobContainerProperties properties = new BlobContainerProperties([:], 'etag', OffsetDateTime.now(), null, null, null, null, false, false)
        AzureBlobReactiveBucketOperations operations = new AzureBlobReactiveBucketOperations(client)

        when:
        Mono.from(operations.create('reports')).block()
        def retrieved = Mono.from(operations.retrieve('reports')).block()
        boolean exists = Mono.from(operations.exists('reports')).block()
        Mono.from(operations.delete('reports')).block()

        then:
        retrieved.present
        retrieved.get().name() == 'reports'
        retrieved.get().nativeEntry() == properties
        exists
        1 * client.create('reports') >> Mono.empty()
        1 * client.retrieve('reports') >> Mono.just(Optional.of(new io.micronaut.objectstorage.bucket.BucketEntry<>('reports', properties)))
        1 * client.exists('reports') >> Mono.just(true)
        1 * client.delete('reports') >> Mono.empty()
    }

    void "reactive Azure bucket operations emit empty when the container is absent"() {
        given:
        AzureBlobReactiveBucketOperations.AzureAsyncBucketClient client = Mock()
        AzureBlobReactiveBucketOperations operations = new AzureBlobReactiveBucketOperations(client)

        when:
        def retrieved = Mono.from(operations.retrieve('reports')).block()
        boolean exists = Mono.from(operations.exists('reports')).block()

        then:
        !retrieved.present
        !exists
        1 * client.retrieve('reports') >> Mono.just(Optional.empty())
        1 * client.exists('reports') >> Mono.just(false)
    }
}
