package io.micronaut.objectstorage.relational

import io.micronaut.context.annotation.Factory
import jakarta.inject.Named
import jakarta.inject.Singleton
import org.postgresql.ds.PGSimpleDataSource
import org.testcontainers.containers.PostgreSQLContainer

import javax.sql.DataSource
import java.io.PrintWriter
import java.sql.Connection
import java.sql.SQLException
import java.util.UUID
import java.util.logging.Logger

@Factory
class RelationalStoragePostgresDataSourceFactory {

    @Singleton
    @Named("postgres")
    DataSource postgresDataSource() {
        return new ContainerBackedDataSource("relational_storage_${UUID.randomUUID()}")
    }

    private static final class ContainerBackedDataSource implements DataSource, AutoCloseable {
        private final PostgreSQLContainer<?> container
        private final PGSimpleDataSource dataSource

        private ContainerBackedDataSource(String databaseName) {
            this.container = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName(databaseName)
                .withUsername("test")
                .withPassword("test")
            container.start()
            this.dataSource = new PGSimpleDataSource()
            dataSource.setURL(container.getJdbcUrl())
            dataSource.setUser(container.getUsername())
            dataSource.setPassword(container.getPassword())
        }

        @Override
        Connection getConnection() throws SQLException {
            return dataSource.getConnection()
        }

        @Override
        Connection getConnection(String username, String password) throws SQLException {
            return dataSource.getConnection(username, password)
        }

        @Override
        PrintWriter getLogWriter() throws SQLException {
            return dataSource.getLogWriter()
        }

        @Override
        void setLogWriter(PrintWriter out) throws SQLException {
            dataSource.setLogWriter(out)
        }

        @Override
        void setLoginTimeout(int seconds) throws SQLException {
            dataSource.setLoginTimeout(seconds)
        }

        @Override
        int getLoginTimeout() throws SQLException {
            return dataSource.getLoginTimeout()
        }

        @Override
        Logger getParentLogger() {
            return dataSource.getParentLogger()
        }

        @Override
        <T> T unwrap(Class<T> iface) throws SQLException {
            return dataSource.unwrap(iface)
        }

        @Override
        boolean isWrapperFor(Class<?> iface) {
            return dataSource.isWrapperFor(iface)
        }

        @Override
        void close() {
            container.stop()
        }
    }
}
