plugins {
    io.micronaut.build.internal.`objectstorage-module`
}

dependencies {
    api(projects.objectStorageCore)
    api(mnGcp.micronaut.gcp.common)

    api(platform(libs.gcp.libraries.bom))
    api(libs.gcp.storage)

    implementation(platform(mnGcp.micronaut.gcp.bom))

    testImplementation(projects.objectStorageTck)
    testImplementation(libs.testcontainers.spock)
}
