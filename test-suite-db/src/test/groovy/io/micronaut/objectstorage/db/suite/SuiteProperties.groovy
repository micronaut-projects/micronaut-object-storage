package io.micronaut.objectstorage.db.suite

import io.micronaut.objectstorage.db.DbStorageConfiguration

/**
 * Storage properties of a suite spec. The datasource comes from application-&lt;database&gt;.yml; each spec gets its own
 * storage, so its own bucket in the shared tables.
 */
class SuiteProperties {

    static Map<String, String> storage(String name, Map<String, String> storage = [:]) {
        Map<String, String> properties = [("${DbStorageConfiguration.PREFIX}.${name}.schema-generate".toString()): 'CREATE']
        storage.each { k, v -> properties.put("${DbStorageConfiguration.PREFIX}.${name}.${k}".toString(), v) }
        properties
    }
}
