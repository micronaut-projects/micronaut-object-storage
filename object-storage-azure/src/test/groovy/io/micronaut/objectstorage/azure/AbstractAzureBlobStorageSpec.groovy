package io.micronaut.objectstorage.azure

import com.azure.core.http.rest.Response
import com.azure.storage.blob.BlobContainerClient
import com.azure.storage.blob.BlobServiceClient
import com.azure.storage.blob.models.BlobErrorCode
import com.azure.storage.blob.models.BlobStorageException
import com.azure.storage.blob.models.BlockBlobItem
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.ObjectStorageOperationsSpecification
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import jakarta.inject.Named
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.Shared

import static io.micronaut.objectstorage.azure.AzureBlobStorageConfiguration.PREFIX

abstract class AbstractAzureBlobStorageSpec extends ObjectStorageOperationsSpecification implements TestPropertyProvider {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractAzureBlobStorageSpec.class);
    public static final String CONTAINER_NAME = System.currentTimeMillis()

    @Shared
    boolean containerCreated;
    public static final String OBJECT_STORAGE_NAME = 'default'

    @Inject
    @Named(OBJECT_STORAGE_NAME)
    AzureBlobStorageOperations azureBlobContainer

    @Shared
    @Inject
    BlobContainerClient blobContainerClient

    @Inject
    BlobServiceClient blobServiceClient

    void setup() {
        if (!containerCreated) {
            blobServiceClient.createBlobContainer(CONTAINER_NAME)
            containerCreated = true
        }
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
    ObjectStorageOperations<?, Response<BlockBlobItem>, ?> getObjectStorage() {
        azureBlobContainer
    }

    @Override
    Map<String, String> getProperties() {
        [(PREFIX + '.' + OBJECT_STORAGE_NAME + '.container'): CONTAINER_NAME]
    }
}
