package io.micronaut.objectstorage.db

/**
 * Properties of a fresh in-memory H2 database with the built-in tables created on first use.
 */
class DbTestProperties {

    static Map<String, String> h2(Map<String, String> storage = [:], String name = 'default') {
        Map<String, String> properties = [
            'datasources.default.url'              : "jdbc:h2:mem:${UUID.randomUUID()};DB_CLOSE_DELAY=-1".toString(),
            'datasources.default.driver-class-name': 'org.h2.Driver',
            'datasources.default.username'         : 'sa',
            'datasources.default.password'         : '',
            'datasources.default.dialect'          : 'H2',
            ("${DbStorageConfiguration.PREFIX}.${name}.schema-generate".toString()): 'CREATE',
        ]
        storage.each { k, v -> properties.put("${DbStorageConfiguration.PREFIX}.${name}.${k}".toString(), v) }
        properties
    }
}
