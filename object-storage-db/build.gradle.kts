plugins {
    io.micronaut.build.internal.`objectstorage-module`
}

dependencies {
    api(projects.micronautObjectStorageCore)
    api(mnData.micronaut.data.jdbc)
    api(mnSql.micronaut.jdbc)

    compileOnly(mn.micronaut.management)
    compileOnly(mnFlyway.flyway)

    testImplementation(projects.micronautObjectStorageTck)
    testImplementation(mnSql.micronaut.jdbc.hikari)
    testImplementation(mn.micronaut.management)
    testImplementation(mnFlyway.flyway)
    testImplementation(mnLiquibase.liquibase)
    testRuntimeOnly(mnSql.h2)
}

micronautBuild {
    binaryCompatibility {
        enabledAfter("3.2.0")
    }
}

// The Flyway migrations are the shipped schema scripts under their Flyway names, so the two never drift apart.
// A future schema change adds V2 migrations next to an updated schema.sql.
val flywayMigrations by tasks.registering(Sync::class) {
    from("src/main/resources/META-INF/micronaut-object-storage-db") {
        include("*/schema.sql")
        eachFile {
            path = "db/micronaut-object-storage-db/${file.parentFile.name}/V1__micronaut_object_storage_db.sql"
        }
    }
    includeEmptyDirs = false
    into(layout.buildDirectory.dir("generated/flyway"))
}

sourceSets.main {
    resources.srcDir(flywayMigrations)
}
