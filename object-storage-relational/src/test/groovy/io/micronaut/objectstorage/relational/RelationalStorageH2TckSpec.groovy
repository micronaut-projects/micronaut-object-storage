package io.micronaut.objectstorage.relational

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider

@MicronautTest
class RelationalStorageH2TckSpec extends AbstractRelationalStorageOperationsSpec implements TestPropertyProvider {

    @Override
    Map<String, String> getProperties() {
        [
            (RelationalStorageConfiguration.PREFIX + '.default.enabled'): 'true',
            (RelationalStorageConfiguration.PREFIX + '.default.table-name'): 'relational_storage_h2_tck_objects',
        ]
    }
}
