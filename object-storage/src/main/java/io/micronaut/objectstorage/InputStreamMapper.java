package io.micronaut.objectstorage;

import java.io.InputStream;

/**
 * @author Pavol Gressa
 * @since 2.5
 */
public interface InputStreamMapper {

    byte[] toByteArray(InputStream inputStream);

}
