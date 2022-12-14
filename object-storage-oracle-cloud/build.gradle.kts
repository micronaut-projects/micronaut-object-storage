plugins {
    io.micronaut.build.internal.`objectstorage-module`
}

dependencies {
    api(projects.objectStorageCore)
    api(mnOraclecloud.micronaut.oraclecloud.sdk)
    api(libs.oci.sdk.objectstorage)

    implementation(platform(mnOraclecloud.micronaut.oraclecloud.bom))

    testImplementation(projects.objectStorageTck)
    testImplementation(libs.testcontainers.spock)
}
