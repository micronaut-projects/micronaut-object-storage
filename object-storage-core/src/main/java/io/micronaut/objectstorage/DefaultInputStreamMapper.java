/*
 * Copyright 2017-2021 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.objectstorage;

import jakarta.inject.Singleton;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author Pavol Gressa
 * @since 1.0
 */
@Singleton
public class DefaultInputStreamMapper implements InputStreamMapper {
    @Override
    public byte[] toByteArray(InputStream inputStream) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            int nRead;
            byte[] data = new byte[4];

            while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
                output.write(data, 0, nRead);
            }

            output.flush();
            return output.toByteArray();
        } catch (IOException e) {
            throw new ObjectStorageException("Failed to transform input stream to byte array", e);
        }
    }
}
