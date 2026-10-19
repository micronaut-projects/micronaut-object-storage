plugins {
    io.micronaut.build.internal.`objectstorage-module`
}

repositories {
    mavenLocal {
        mavenContent {
            snapshotsOnly()
        }
    }
}

dependencies {
    api(projects.micronautObjectStorageCore)
    api(libs.micronaut.supabase)

    implementation(mn.micronaut.json.core)
    implementation(mn.reactor)
    implementation(platform(mnSerde.micronaut.serde.bom))
    runtimeOnly(mnSerde.micronaut.serde.jackson)

    annotationProcessor(mnValidation.micronaut.validation.processor)
    implementation(mnValidation.micronaut.validation)

    testAnnotationProcessor(mn.micronaut.inject.java)
    testImplementation(mnValidation.micronaut.validation.processor)
    testImplementation(projects.micronautObjectStorageTck)
    testImplementation(mn.micronaut.http.client)
    testImplementation(mnTestResources.testcontainers.core)
    testImplementation(mnTest.micronaut.test.junit5)
    testImplementation(mnTest.junit.jupiter.params)
    testImplementation(mnTest.mockito.core)
    testRuntimeOnly(mnTest.junit.jupiter.engine)
}

micronautBuild {
    // New module: there is no previously published release to compare against.
    binaryCompatibility {
        enabled.set(false)
    }
}
