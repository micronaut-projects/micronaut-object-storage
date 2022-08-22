plugins {
    io.micronaut.build.internal.`objectstorage-module`
}

dependencies {
    api(projects.objectStorageCore)
    api(mn.micronaut.oraclecloud.sdk)
    api(libs.oci.sdk.objectstorage)

    implementation(platform(mn.micronaut.oraclecloud.bom))

    testImplementation(projects.objectStorageTck)
    testImplementation(libs.testcontainers.spock)
}
