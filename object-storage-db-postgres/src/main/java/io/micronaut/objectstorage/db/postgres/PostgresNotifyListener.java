/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.objectstorage.db.postgres;

import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.objectstorage.ObjectStorageException;
import io.micronaut.objectstorage.db.DbStorageConfiguration;
import io.micronaut.objectstorage.db.internal.JsonMaps;
import io.micronaut.objectstorage.db.spi.DbObjectChangeListener;
import io.micronaut.objectstorage.db.spi.DbStorageContext;
import jakarta.inject.Singleton;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Publishes object changes with {@code pg_notify} when a storage sets
 * {@code micronaut.object-storage.db.<name>.postgres.notify-channel}. PostgreSQL delivers the notification to
 * {@code LISTEN}ers only when the transaction of the change commits.
 *
 * <p>The payload is a JSON object with the {@code storage}, {@code bucket}, {@code key}, {@code event}
 * ({@code UPLOADED} or {@code DELETED}) and, for uploads, {@code etag}.</p>
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.2.0
 */
@Internal
@Singleton
final class PostgresNotifyListener implements DbObjectChangeListener {

    /**
     * The property, under each storage, that names the notification channel.
     */
    static final String NOTIFY_CHANNEL = "postgres.notify-channel";

    private final Environment environment;
    private final Map<String, Optional<String>> channels = new ConcurrentHashMap<>();

    PostgresNotifyListener(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void onChange(@NonNull DbStorageContext context, @NonNull Connection connection, @NonNull Change change) throws SQLException {
        String channel = channel(context);
        if (channel == null) {
            return;
        }
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("storage", context.storageName());
        payload.put("bucket", change.bucket());
        payload.put("key", change.key());
        payload.put("event", change.type().name());
        if (change.etag() != null) {
            payload.put("etag", change.etag());
        }
        try (PreparedStatement ps = connection.prepareStatement("SELECT pg_notify(?, ?)")) {
            ps.setString(1, channel);
            ps.setString(2, JsonMaps.write(payload));
            ps.executeQuery().close();
        }
    }

    private @Nullable String channel(DbStorageContext context) {
        return channels.computeIfAbsent(context.storageName(), name -> {
            Optional<String> channel = environment.getProperty(
                DbStorageConfiguration.PREFIX + "." + name + "." + NOTIFY_CHANNEL, String.class).filter(c -> !c.isBlank());
            if (channel.isPresent() && context.sql().getDialect() != Dialect.POSTGRES) {
                throw new ObjectStorageException("Database storage '" + name + "' sets " + NOTIFY_CHANNEL
                    + " but its datasource is not PostgreSQL");
            }
            return channel;
        }).orElse(null);
    }
}
