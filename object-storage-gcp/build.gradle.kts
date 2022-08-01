plugins {
    io.micronaut.build.internal.`objectstorage-module`
}

dependencies {
    api(projects.objectStorageCore)
    api(mn.micronaut.gcp.common)
    api(libs.gcp.storage)

    implementation(platform(mn.micronaut.gcp.bom))
    implementation(platform(libs.gcp.libraries.bom))

    testImplementation(projects.objectStorageTck)
}
