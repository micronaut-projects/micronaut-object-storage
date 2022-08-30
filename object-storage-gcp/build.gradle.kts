plugins {
    io.micronaut.build.internal.`objectstorage-module`
}

dependencies {
    api(projects.objectStorageCore)
    api(mn.micronaut.gcp.common)

    api(platform(libs.gcp.libraries.bom))
    api(libs.gcp.storage)

    implementation(platform(mn.micronaut.gcp.bom))

    testImplementation(projects.objectStorageTck)
    testImplementation(libs.testcontainers.spock)
}
