package example;

import io.micronaut.objectstorage.ReactiveObjectStorageOperations;
import jakarta.inject.Singleton;

//tag::class[]
@Singleton
public class ReactiveProfileService {

    private final ReactiveObjectStorageOperations<?, ?, ?> objectStorage;

    public ReactiveProfileService(ReactiveObjectStorageOperations<?, ?, ?> objectStorage) {
        this.objectStorage = objectStorage;
    }
}
//end::class[]
