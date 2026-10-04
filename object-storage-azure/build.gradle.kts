plugins {
    io.micronaut.build.internal.`objectstorage-module`
}

dependencies {
    api(projects.micronautObjectStorageCore)
    api(mnAzure.micronaut.azure.sdk)
    api(libs.azure.storage.blob)

    constraints {
        api("com.fasterxml.jackson.core:jackson-core:2.22.3") {
            because("Require a version without the known Jackson vulnerabilities brought by the Azure SDK")
        }
        api("com.fasterxml.jackson.core:jackson-databind:2.22.3") {
            because("Require a version without the known Jackson vulnerabilities brought by the Azure SDK")
        }
    }
    implementation(platform(mnAzure.micronaut.azure.bom))

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
