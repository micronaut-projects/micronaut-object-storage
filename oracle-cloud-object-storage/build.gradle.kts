plugins {
    id("io.micronaut.build.internal.objectstorage-module")
}

dependencies {
    api(projects.objectStorage)

    implementation(platform(mn.micronaut.oraclecloud.bom))
    api(mn.micronaut.oraclecloud.sdk)

    api(libs.oci.sdk.objectstorage)

    testImplementation(projects.objectStorageTck)
}
