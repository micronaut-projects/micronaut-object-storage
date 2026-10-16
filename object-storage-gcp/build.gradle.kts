plugins {
    io.micronaut.build.internal.`objectstorage-module`
}

dependencies {
    api(projects.micronautObjectStorageCore)
    api(mnGcp.micronaut.gcp.common)

    api(platform(libs.gcp.libraries.bom))
    api(libs.gcp.storage) {
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-core")
    }
    // Keep the Google SDK on the patched Jackson 2.18 maintenance line.
    api(platform(libs.gcp.jackson.bom))
    api("com.fasterxml.jackson.core:jackson-core")
    implementation(platform(mnGcp.micronaut.gcp.bom))

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
