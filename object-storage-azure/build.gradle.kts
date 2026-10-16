plugins {
    io.micronaut.build.internal.`objectstorage-module`
}

dependencies {
    api(projects.micronautObjectStorageCore)
    api(mnAzure.micronaut.azure.sdk)
    api(libs.azure.storage.blob)

    implementation(platform(mnAzure.micronaut.azure.bom))
    constraints {
        api(libs.jackson.core) {
            because("Require a version without the known Jackson vulnerabilities brought by the Azure SDK")
        }
        api(libs.jackson.databind) {
            because("Require a version without the known Jackson vulnerabilities brought by the Azure SDK")
        }
    }

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
