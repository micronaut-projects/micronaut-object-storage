plugins {
    io.micronaut.build.internal.`objectstorage-module`
}

dependencies {
    api(projects.objectStorage)

    implementation(platform(mn.micronaut.gcp.bom))
    implementation(platform(libs.gcp.libraries.bom))

    api(mn.micronaut.gcp.common)
    api(libs.gcp.storage)

    testImplementation(projects.objectStorageTck)
}
