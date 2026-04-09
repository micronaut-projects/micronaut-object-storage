package io.micronaut.objectstorage.relational

import io.micronaut.context.annotation.Factory
import jakarta.inject.Named
import jakarta.inject.Singleton
import org.h2.jdbcx.JdbcDataSource

import javax.sql.DataSource
import java.util.UUID

@Factory
class RelationalStorageTestDataSourceFactory {

    @Singleton
    @Named("default")
    DataSource defaultDataSource() {
        return createDataSource("default")
    }

    @Singleton
    @Named("other")
    DataSource otherDataSource() {
        return createDataSource("other")
    }

    @Singleton
    @Named("analytics")
    DataSource analyticsDataSource() {
        return createDataSource("analytics")
    }

    private static DataSource createDataSource(String name) {
        JdbcDataSource dataSource = new JdbcDataSource()
        dataSource.setURL("jdbc:h2:mem:${name}-${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false")
        dataSource.setUser("sa")
        dataSource.setPassword("")
        return dataSource
    }
}
