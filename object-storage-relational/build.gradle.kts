plugins {
    io.micronaut.build.internal.`objectstorage-module`
}

dependencies {
    api(projects.micronautObjectStorageCore)

    testImplementation(libs.h2)
    testImplementation(libs.postgresql)
    testImplementation(mnTest.micronaut.test.spock)
    testRuntimeOnly(mnTestResources.micronaut.test.resources.jdbc.postgresql)
    testImplementation(mnTestResources.testcontainers.jdbc)
    testImplementation(mnTestResources.testcontainers.postgres)
    testImplementation(projects.micronautObjectStorageTck)
}

micronautBuild {
    binaryCompatibility {
        enabledAfter("3.0.0")
    }
}
