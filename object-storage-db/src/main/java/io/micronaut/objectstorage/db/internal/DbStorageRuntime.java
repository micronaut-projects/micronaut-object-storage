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
package io.micronaut.objectstorage.db.internal;

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.type.Argument;
import io.micronaut.data.jdbc.config.DataJdbcConfiguration;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.jdbc.DataSourceResolver;
import io.micronaut.objectstorage.ObjectStorageException;
import io.micronaut.objectstorage.db.DbContentLayout;
import io.micronaut.objectstorage.db.DbSchemaGenerate;
import io.micronaut.objectstorage.db.DbStorageConfiguration;
import io.micronaut.objectstorage.db.spi.DbContentStore;
import io.micronaut.objectstorage.db.spi.DbContentStoreProvider;
import io.micronaut.objectstorage.db.spi.DbObjectChangeListener;
import io.micronaut.objectstorage.db.spi.DbObjectRef;
import io.micronaut.objectstorage.db.spi.DbSqlDialect;
import io.micronaut.objectstorage.db.spi.DbStorageContext;
import io.micronaut.objectstorage.db.spi.DbTableMapping;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.transaction.TransactionOperations;
import jakarta.inject.Named;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * The runtime state of one database storage, shared by its operation beans. Creating it does no database work:
 * the dialect, transaction manager and content store are resolved on first use, which is also when the schema is
 * created or migrated and the mapping validated, if configured.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.2.0
 */
@Internal
@EachBean(DbStorageConfiguration.class)
public final class DbStorageRuntime {

    private static final Logger LOG = LoggerFactory.getLogger(DbStorageRuntime.class);
    private static final Map<String, Object> SCHEMA_LOCKS = new ConcurrentHashMap<>();

    private final DbStorageConfiguration configuration;
    private final BeanContext beanContext;
    private final List<DbContentStoreProvider> providers;
    private final ExecutorService prefetchExecutor;
    private final List<DbObjectChangeListener> listeners;

    private volatile @Nullable Resolved resolved;

    DbStorageRuntime(@Parameter DbStorageConfiguration configuration,
                     BeanContext beanContext,
                     List<DbContentStoreProvider> providers,
                     List<DbObjectChangeListener> listeners,
                     @Named(TaskExecutors.BLOCKING) ExecutorService prefetchExecutor) {
        this.configuration = configuration;
        this.listeners = List.copyOf(listeners);
        this.beanContext = beanContext;
        List<DbContentStoreProvider> sorted = new ArrayList<>(providers);
        OrderUtil.sort(sorted);
        this.providers = sorted;
        this.prefetchExecutor = prefetchExecutor;
    }

    /**
     * @return The storage configuration.
     */
    @NonNull
    public DbStorageConfiguration configuration() {
        return configuration;
    }

    /**
     * @return The storage context.
     */
    @NonNull
    public DbStorageContext context() {
        return resolve().context;
    }

    /**
     * @return The transactions of the storage's datasource.
     */
    @NonNull
    public DbTransactions transactions() {
        return resolve().transactions;
    }

    /**
     * @return The content store.
     */
    @NonNull
    public DbContentStore store() {
        return resolve().store;
    }

    /**
     * @return The SQL of object rows.
     */
    @NonNull
    public DbObjectSql objects() {
        return resolve().objects;
    }

    /**
     * Notifies the change listeners, inside the transaction of the change.
     *
     * @param connection The connection of the change.
     * @param change The change.
     * @throws SQLException If a listener fails.
     */
    public void changed(@NonNull Connection connection, DbObjectChangeListener.@NonNull Change change) throws SQLException {
        if (listeners.isEmpty()) {
            return;
        }
        DbStorageContext context = context();
        for (DbObjectChangeListener listener : listeners) {
            listener.onChange(context, connection, change);
        }
    }

    /**
     * Fails unless the storage uses the built-in tables.
     *
     * @param feature The feature that needs them.
     */
    public void requireBuiltInTables(@NonNull String feature) {
        if (!DbTableMapping.of(configuration).builtIn()) {
            throw new ObjectStorageException(feature + " are not supported by database storage '" + configuration.getName()
                + "' because it is mapped onto table " + configuration.getTable().getName());
        }
    }

    /**
     * Opens a stream over the content of an object version. No database work happens until the first read.
     * Inside a transaction the windows are read on the caller's connection, without prefetching; otherwise each
     * window is a short transaction and the next one is prefetched.
     *
     * @param object The object version.
     * @return The stream.
     */
    @NonNull
    public InputStream openStream(@NonNull DbObjectRef object) {
        Resolved r = resolve();
        int window = configuration.getChunkSize();
        long length = object.contentLength() != null ? object.contentLength() : -1;
        return new WindowedInputStream(
            index -> r.transactions.read(connection -> r.store.readWindow(r.context, connection, object, index, window)),
            length,
            window,
            r.budget,
            r.transactions.isActive() ? null : prefetchExecutor,
            "object '" + object.key() + "' of database storage '" + configuration.getName() + "'"
        );
    }

    private Resolved resolve() {
        Resolved r = resolved;
        if (r == null) {
            synchronized (this) {
                r = resolved;
                if (r == null) {
                    r = doResolve();
                    resolved = r;
                }
            }
        }
        return r;
    }

    @SuppressWarnings("unchecked")
    private Resolved doResolve() {
        String datasource = configuration.getDatasource();
        DbTableMapping mapping = DbTableMapping.of(configuration);
        TransactionOperations<Connection> operations = beanContext.findBean(
            Argument.of(TransactionOperations.class, Connection.class), Qualifiers.byName(datasource)
        ).orElseThrow(() -> new ObjectStorageException("Database storage '" + configuration.getName()
            + "' uses datasource '" + datasource + "' but no such JDBC datasource is configured"));
        DbTransactions transactions = new DbTransactions(operations, configuration.getTransactionPropagation(),
            configuration.getName(), sqlErrorHint(mapping));

        Dialect dialect = beanContext.findBean(DataJdbcConfiguration.class, Qualifiers.byName(datasource))
            .map(DataJdbcConfiguration::getDialect)
            .filter(d -> d != Dialect.ANSI)
            .orElseGet(() -> transactions.read(DbStorageRuntime::detectDialect));
        DbSqlDialect sql = new DbSqlDialect(dialect);
        DbStorageContext context = new DbStorageContext(configuration, mapping, sql);

        DbContentLayout layout = configuration.getContentLayout();
        if (layout == null) {
            layout = mapping.builtIn()
                ? providers.stream().map(p -> p.getDefaultLayout(dialect)).flatMap(Optional::stream).findFirst().orElse(DbContentLayout.CHUNKED)
                : DbContentLayout.SINGLE_COLUMN;
        }
        DbContentLayout effectiveLayout = layout;
        DbContentStore store = providers.stream()
            .map(p -> p.create(dialect, effectiveLayout, mapping))
            .flatMap(Optional::stream)
            .findFirst()
            .orElseThrow(() -> new ObjectStorageException(unsupportedLayoutMessage(effectiveLayout, dialect, mapping)));
        if (configuration.getContentLayout() == null && mapping.builtIn() && store.getLayout() == DbContentLayout.CHUNKED
            && (dialect == Dialect.POSTGRES || dialect == Dialect.ORACLE) && LOG.isInfoEnabled()) {
            LOG.info("Database storage '{}' stores content as chunk rows on {}. Add micronaut-object-storage-db-{} for {}",
                configuration.getName(), dialect, dialect == Dialect.POSTGRES ? "postgres" : "oracle",
                dialect == Dialect.POSTGRES ? "Large Object streaming" : "SecureFile BLOB storage");
        }

        if (mapping.builtIn() && configuration.getSchemaGenerate() != DbSchemaGenerate.NONE) {
            synchronized (SCHEMA_LOCKS.computeIfAbsent(datasource, k -> new Object())) {
                generateSchema(transactions, sql);
            }
        }
        long maxBuffered = configuration.getMaxBufferedBytes() != null
            ? configuration.getMaxBufferedBytes()
            : Runtime.getRuntime().maxMemory() / 4;
        Resolved r = new Resolved(context, transactions, store, new DbObjectSql(context), new ByteBudget(maxBuffered));
        if (configuration.isValidateMapping()) {
            List<String> problems = transactions.read(connection -> DbMappingValidation.validate(connection, mapping, store));
            if (!problems.isEmpty()) {
                throw new ObjectStorageException(DbMappingValidation.message(configuration, problems));
            }
        }
        return r;
    }

    private void generateSchema(DbTransactions transactions, DbSqlDialect sql) {
        String directory = sql.getSchemaDirectory();
        if (configuration.getSchemaGenerate() == DbSchemaGenerate.FLYWAY) {
            // Flyway needs the target datasource, not the contextual proxy that Micronaut Data registers
            DataSource dataSource = beanContext.findBean(DataSourceResolver.class)
                .orElse(DataSourceResolver.DEFAULT)
                .resolve(beanContext.getBean(DataSource.class, Qualifiers.byName(configuration.getDatasource())));
            try {
                DbFlywayMigrator.migrate(dataSource, directory);
            } catch (NoClassDefFoundError e) {
                throw new ObjectStorageException("Database storage '" + configuration.getName()
                    + "' uses schema-generate FLYWAY but Flyway is not on the classpath", e);
            }
            return;
        }
        boolean exists;
        try {
            exists = transactions.requiresNew(connection -> {
                try (PreparedStatement ps = connection.prepareStatement("SELECT 1 FROM " + DbTableMapping.OBJECT_TABLE + " WHERE 1 = 0")) {
                    ps.executeQuery().close();
                    return true;
                }
            });
        } catch (ObjectStorageException e) {
            exists = false;
        }
        if (exists) {
            return;
        }
        LOG.info("Creating the tables of database storage '{}' in datasource '{}'", configuration.getName(), configuration.getDatasource());
        List<String> statements = DbSchemaScripts.load(directory);
        transactions.requiresNew(connection -> {
            try (Statement statement = connection.createStatement()) {
                for (String sqlStatement : statements) {
                    statement.execute(sqlStatement);
                }
            }
            return null;
        });
    }

    private static Dialect detectDialect(Connection connection) throws SQLException {
        String product = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
        if (product.contains("postgres")) {
            return Dialect.POSTGRES;
        } else if (product.contains("oracle")) {
            return Dialect.ORACLE;
        } else if (product.contains("mysql") || product.contains("mariadb")) {
            return Dialect.MYSQL;
        } else if (product.contains("sql server")) {
            return Dialect.SQL_SERVER;
        } else if (product.contains("h2")) {
            return Dialect.H2;
        }
        return Dialect.ANSI;
    }

    private String sqlErrorHint(DbTableMapping mapping) {
        if (mapping.builtIn()) {
            return " (built-in tables of datasource '" + configuration.getDatasource() + "'; check that the schema is applied)";
        }
        StringBuilder hint = new StringBuilder(" (table ").append(mapping.objectTable()).append(" mapped with columns key=")
            .append(mapping.keyColumn()).append(", content=").append(mapping.contentColumn());
        appendColumn(hint, "content-length", mapping.contentLengthColumn());
        appendColumn(hint, "content-type", mapping.contentTypeColumn());
        appendColumn(hint, "etag", mapping.etagColumn());
        appendColumn(hint, "metadata", mapping.metadataColumn());
        appendColumn(hint, "bucket", mapping.bucketColumn());
        appendColumn(hint, "updated-at", mapping.updatedAtColumn());
        return hint.append("; check ").append(DbStorageConfiguration.PREFIX).append('.').append(configuration.getName())
            .append(".table)").toString();
    }

    private static void appendColumn(StringBuilder hint, String name, @Nullable String column) {
        if (column != null) {
            hint.append(", ").append(name).append('=').append(column);
        }
    }

    private String unsupportedLayoutMessage(DbContentLayout layout, Dialect dialect, DbTableMapping mapping) {
        String message = "Database storage '" + configuration.getName() + "' uses content layout " + layout;
        if (!mapping.builtIn()) {
            return message + ", but tables mapped with " + DbStorageConfiguration.PREFIX + "." + configuration.getName()
                + ".table support only SINGLE_COLUMN";
        }
        return switch (layout) {
            case LARGE_OBJECT -> message + ", which requires PostgreSQL and the micronaut-object-storage-db-postgres module";
            case BYTEA_CHUNKS -> message + ", which requires PostgreSQL";
            default -> message + ", which is not supported on " + dialect;
        };
    }

    private record Resolved(DbStorageContext context,
                            DbTransactions transactions,
                            DbContentStore store,
                            DbObjectSql objects,
                            ByteBudget budget) {
    }
}
