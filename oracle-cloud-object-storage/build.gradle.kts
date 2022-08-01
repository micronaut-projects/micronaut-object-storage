plugins {
    io.micronaut.build.internal.`objectstorage-module`
}

dependencies {
    api(projects.objectStorageCore)

    implementation(platform(mn.micronaut.oraclecloud.bom))
    api(mn.micronaut.oraclecloud.sdk)

    api(libs.oci.sdk.objectstorage)

    testImplementation(projects.objectStorageTck)
}
