plugins {
    io.micronaut.build.internal.`objectstorage-module`
}

dependencies {
    api(projects.objectStorageCore)
    api(mn.micronaut.aws.sdk.v2)
    api(libs.amazon.awssdk.s3)

    implementation(platform(mn.micronaut.aws.bom))

    testImplementation(projects.objectStorageTck)
}
