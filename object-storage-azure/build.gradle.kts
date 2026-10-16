plugins {
    io.micronaut.build.internal.`objectstorage-module`
}

dependencies {
    api(projects.micronautObjectStorageCore)
    api(mnAzure.micronaut.azure.sdk)
    api(libs.azure.storage.blob)

    implementation(platform(mnAzure.micronaut.azure.bom))
    // Keep transitive Jackson 2 and Netty components aligned on patched releases.
    api(platform(libs.jackson.bom))
    api(platform(libs.netty.bom))

    testImplementation(mnValidation.micronaut.validation)
    testImplementation(mnValidation.micronaut.validation.processor)
    testImplementation(projects.micronautObjectStorageTck)
    testImplementation(mnTestResources.testcontainers.core)
}
micronautBuild {
    binaryCompatibility {
        enabledAfter("3.0.0")
    }
}
