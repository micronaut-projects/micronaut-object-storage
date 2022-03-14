package io.micronaut.objectstorage;

import java.util.Optional;

/**
 * @author Pavol Gressa
 * @since 2.5
 */
public interface ObjectStorage {

    /**
     * Upload object to object storage.
     *
     * @param uploadRequest the upload request
     * @throws ObjectStorageException
     */
    void put(UploadRequest uploadRequest) throws ObjectStorageException;

    /**
     * Get the object from object storage.
     *
     * @param objectPath the object path
     * @return the object or null if object not exists
     * @throws ObjectStorageException if there was a failure to store object
     */
    Optional<ObjectStorageEntry> get(String objectPath) throws ObjectStorageException;

    /**
     * Delete the object.
     * @param path object name in format {@code /foo/bar/file}
     */
    void delete(String path) throws ObjectStorageException;
}
