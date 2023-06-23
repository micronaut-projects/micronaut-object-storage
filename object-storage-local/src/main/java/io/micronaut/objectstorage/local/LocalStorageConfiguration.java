package io.micronaut.objectstorage.local;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.objectstorage.ObjectStorageException;
import io.micronaut.objectstorage.configuration.AbstractObjectStorageConfiguration;
import io.micronaut.objectstorage.configuration.ObjectStorageConfiguration;

import java.nio.file.Files;
import java.nio.file.Path;

import static io.micronaut.objectstorage.local.LocalStorageConfiguration.PREFIX;

/**
 * Configuration class for local storage.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.0.0
 */
@EachProperty(PREFIX)
public class LocalStorageConfiguration extends AbstractObjectStorageConfiguration {

    public static final String NAME = "local";

    public static final String PREFIX = ObjectStorageConfiguration.PREFIX + '.' + NAME;

    @NonNull
    private Path path;

    public LocalStorageConfiguration(@Parameter String name) {
        super(name);
    }

    /**
     * @return The path of the local storage.
     */
    @NonNull
    public Path getPath() {
        if (path == null) {
            try {
                path = Files.createTempDirectory("micronaut-object-storage-local");
            } catch (Exception e) {
                throw new ObjectStorageException("Error creating temp directory for local storage", e);
            }
        }
        return path;
    }

    /**
     * @param path The path of the local storage.
     */
    public void setPath(@NonNull Path path) {
        this.path = path;
    }
}
