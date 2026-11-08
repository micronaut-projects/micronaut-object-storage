plugins {
    groovy
    id("io.micronaut.build.internal.objectstorage-base")
    id("io.micronaut.minimal.library")
    id("io.micronaut.test-resources")
}

dependencies {
    testImplementation(projects.micronautObjectStorageDb)
    testImplementation(projects.micronautObjectStorageDbOracle)
    testImplementation(projects.micronautObjectStorageDbPostgres)
    testImplementation(projects.micronautObjectStorageTck)
    testImplementation(mnTest.micronaut.test.spock)
    testImplementation(mnSql.micronaut.jdbc.hikari)
    testImplementation(mnSql.postgresql)

    testRuntimeOnly(mnSql.ojdbc17)
    testRuntimeOnly(mnSql.mysql.connector.j)
    testRuntimeOnly(mnSql.mariadb.java.client)
    testRuntimeOnly(mnSql.mssql.jdbc)
    testRuntimeOnly(mnLogging.logback.classic)
    testRuntimeOnly(mn.snakeyaml)

    testResourcesService(mnTestResources.micronaut.test.resources.jdbc.postgresql)
    testResourcesService(mnTestResources.micronaut.test.resources.jdbc.oracle.free)
    testResourcesService(mnTestResources.micronaut.test.resources.jdbc.mysql)
    testResourcesService(mnTestResources.micronaut.test.resources.jdbc.mariadb)
    testResourcesService(mnTestResources.micronaut.test.resources.jdbc.mssql)
    testResourcesService(mnSql.postgresql)
    testResourcesService(mnSql.ojdbc17)
    testResourcesService(mnSql.mysql.connector.j)
    testResourcesService(mnSql.mariadb.java.client)
    testResourcesService(mnSql.mssql.jdbc)
}

micronaut {
    version = libs.versions.micronaut.platform.get()
    coreVersion.set(libs.versions.micronaut.asProvider())
    testRuntime("spock2")
    testResources {
        version = libs.versions.micronaut.test.resources.get()
        // Oracle and SQL Server containers take minutes to start on a cold runner
        clientTimeout = 900
    }
}

// One test task per database. Each runs the whole suite with the matching application-<database>.yml, and
// Micronaut Test Resources starts the database in a container.
val databases = listOf("postgres", "oracle", "mysql", "mariadb", "sqlserver")
val databaseTests = databases.map { database ->
    tasks.register<Test>("test" + database.replaceFirstChar { it.uppercase() }) {
        description = "Runs the database storage suite on $database."
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        testClassesDirs = sourceSets.test.get().output.classesDirs
        classpath = sourceSets.test.get().runtimeClasspath
        useJUnitPlatform()
        systemProperty("micronaut.environments", database)
        shouldRunAfter(tasks.named("test"))
    }
}

tasks.named<Test>("test") {
    // the suite needs a database: it only runs through the per-database tasks
    enabled = false
}

tasks.named("check") {
    dependsOn(databaseTests)
}
