package io.micronaut.objectstorage.relational

import io.micronaut.context.annotation.Property
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Named
import spock.lang.Specification

@Property(name = "micronaut.object-storage.relational.analytics.enabled", value = "true")
@Property(name = "micronaut.object-storage.relational.analytics.datasource", value = "analytics")
@Property(name = "micronaut.object-storage.relational.analytics.table-name", value = "named_relational_objects")
@MicronautTest
class RelationalStorageNamedSpec extends Specification {

    @Inject
    @Named("analytics")
    ObjectStorageOperations<?, ?, ?> storageOperations

    void 'it can inject named relational storage operations'() {
        expect:
        storageOperations instanceof RelationalStorageOperations
    }
}
