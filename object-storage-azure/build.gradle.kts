plugins {
    io.micronaut.build.internal.`objectstorage-module`
}

dependencies {
    api(projects.objectStorageCore)
    api(mnAzure.micronaut.azure.sdk)
    api(libs.azure.storage.blob)

    implementation(platform(mnAzure.micronaut.azure.bom))

    testImplementation(projects.objectStorageTck)
    testImplementation(libs.testcontainers.spock)
}
