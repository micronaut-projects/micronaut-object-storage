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
    constraints {
        // "com.google.cloud:google-cloud-storage 26.80.0 requests jackson-core 2.18.1, which is affected by GHSA-72hv-8253-57qq"
        api("com.fasterxml.jackson.core:jackson-core:2.22.1") {
            because("Require a non-vulnerable jackson-core version instead of the transitive 2.18.1 from google-cloud-storage")
        }
    }
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
