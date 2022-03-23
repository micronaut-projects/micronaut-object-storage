package io.micronaut.objectstorage;

import org.reactivestreams.Publisher;

import java.io.InputStream;

/**
 * @author Pavol Gressa
 * @since 2.5
 */
public interface ObjectStorageEntry {

    /**
     * The object path on object storage. For example {@code /path/to}
     *
     * @return object path or empty string if the object is placed at the root of bucket
     */
    String getKey();


    /**
     * The object content.
     *
     * @return object content.
     */
    InputStream getInputStream();
}
