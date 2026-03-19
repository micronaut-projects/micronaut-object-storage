plugins {
    io.micronaut.build.internal.`objectstorage-module`
}

dependencies {
    api(projects.micronautObjectStorageCore)
    implementation(mnData.micronaut.data.jdbc)
    implementation(mnSql.micronaut.jdbc)

    testImplementation(projects.micronautObjectStorageLocal)
    testImplementation(mnValidation.micronaut.validation)
    testImplementation(mnValidation.micronaut.validation.processor)
    testImplementation(projects.micronautObjectStorageTck)
    testImplementation(mnTest.micronaut.test.spock)
    testImplementation(mnTestResources.testcontainers.core)
    testImplementation(libs.ojdbc11)
    testImplementation(libs.testcontainers.oracle.free)
    testImplementation(libs.postgresql)
    testImplementation(libs.testcontainers.postgresql)
}

micronautBuild {
    // New module
    binaryCompatibility {
        enabled.set(false)
    }
}
