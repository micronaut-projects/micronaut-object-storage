plugins {
    io.micronaut.build.internal.`objectstorage-module`
}

dependencies {
    api(projects.objectStorageCore)

    implementation(platform(mn.micronaut.azure.bom))

    api(mn.micronaut.azure.sdk)
    api(libs.azure.storage.blob)

    testImplementation(projects.objectStorageTck)
}
