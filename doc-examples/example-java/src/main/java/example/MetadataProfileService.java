package example;

import io.micronaut.objectstorage.metadata.MetadataAwareObjectStorageOperations;
import io.micronaut.objectstorage.metadata.ObjectStorageMetadataOperations;
import io.micronaut.objectstorage.metadata.StorageContainerMetadata;
import io.micronaut.objectstorage.metadata.StorageDescriptor;
import io.micronaut.objectstorage.metadata.StorageObjectMetadataQuery;
import io.micronaut.objectstorage.request.UploadRequest;
import io.micronaut.objectstorage.response.UploadResponse;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

//tag::beginclass[]
@Singleton
public class MetadataProfileService {

    private final MetadataAwareObjectStorageOperations<?, ?, ?> objectStorage;
    private final ObjectStorageMetadataOperations metadataOperations;
    private final StorageDescriptor storageDescriptor;

    public MetadataProfileService(@Named("pictures") MetadataAwareObjectStorageOperations<?, ?, ?> objectStorage,
                                  @Named("pictures") ObjectStorageMetadataOperations metadataOperations,
                                  @Named("pictures") StorageDescriptor storageDescriptor) {
        this.objectStorage = objectStorage;
        this.metadataOperations = metadataOperations;
        this.storageDescriptor = storageDescriptor;
    }
//end::beginclass[]

    //tag::upload[]
    public UploadResponse<?> uploadProfilePicture(String tenantId, String container, String userId) {
        UploadRequest request = UploadRequest.fromBytes((tenantId + ":" + userId).getBytes(), container + "/" + userId);
        return objectStorage.upload(request);
    }
    //end::upload[]

    //tag::prefix[]
    public List<String> listByPrefix(String prefix) {
        return metadataOperations.listObjects(StorageObjectMetadataQuery.all().withObjectKeyPrefix(prefix))
            .stream()
            .map(storageObject -> storageObject.getObjectKey())
            .toList();
    }
    //end::prefix[]

    //tag::hook[]
    public Optional<StorageContainerMetadata> containerMetadata() {
        return metadataOperations.findContainer(storageDescriptor);
    }
    //end::hook[]
}
//end::endclass[]
