plugins {
    io.micronaut.build.internal.`objectstorage-module`
}

dependencies {
    api(projects.objectStorageCore)
    api(mn.micronaut.azure.sdk)
    api(libs.azure.storage.blob)

    implementation(platform(mn.micronaut.azure.bom)) {
        version {
            require("3.4.0")
        }
    }

    testImplementation(projects.objectStorageTck)
    testImplementation(libs.testcontainers.spock)
}
