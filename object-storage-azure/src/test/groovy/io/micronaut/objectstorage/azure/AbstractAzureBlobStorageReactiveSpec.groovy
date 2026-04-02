package io.micronaut.objectstorage.azure

import com.azure.storage.blob.BlobContainerClient
import com.azure.storage.blob.BlobServiceClient
import com.azure.storage.blob.models.BlobErrorCode
import com.azure.storage.blob.models.BlobStorageException
import io.micronaut.objectstorage.ReactiveObjectStorageOperations
import io.micronaut.objectstorage.ReactiveObjectStorageOperationsSpecification
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import jakarta.inject.Named
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.Shared

import static io.micronaut.objectstorage.azure.AzureBlobStorageConfiguration.PREFIX

abstract class AbstractAzureBlobStorageReactiveSpec extends ReactiveObjectStorageOperationsSpecification implements TestPropertyProvider {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractAzureBlobStorageReactiveSpec.class);
    public static final String CONTAINER_NAME = System.currentTimeMillis()
    public static final String OBJECT_STORAGE_NAME = 'default'

    @Inject
    @Named(OBJECT_STORAGE_NAME)
    ReactiveObjectStorageOperations<?, ?, ?> azureBlobContainer

    @Shared
    @Inject
    BlobContainerClient blobContainerClient

    @Shared
    @Inject
    BlobServiceClient blobServiceClient

    void setupSpec() {
        blobServiceClient.createBlobContainer(CONTAINER_NAME)
    }

    void cleanupSpec() {
        try {
            if (blobContainerClient.exists()) {
                blobContainerClient.delete()
                LOG.trace("Delete completed")
            }
        } catch (BlobStorageException error) {
            if (error.getErrorCode() == BlobErrorCode.CONTAINER_NOT_FOUND) {
                LOG.error("Delete failed. Container was not found")
            }
            LOG.error("Delete failed with error code: {} status code: {} and service message: {}", error.getErrorCode(), error.getStatusCode(), error.getServiceMessage())
        }
    }

    @Override
    ReactiveObjectStorageOperations<?, ?, ?> getObjectStorage() {
        azureBlobContainer
    }

    @Override
    Map<String, String> getProperties() {
        [(PREFIX + '.' + OBJECT_STORAGE_NAME + '.container'): CONTAINER_NAME]
    }
}
