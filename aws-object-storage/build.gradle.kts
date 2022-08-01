plugins {
    id("io.micronaut.build.internal.objectstorage-module")
}

dependencies {
    api(projects.objectStorage)

    implementation(platform(mn.micronaut.aws.bom))

    api(mn.micronaut.aws.sdk.v2)
    api(libs.amazon.awssdk.s3)

    testImplementation(projects.objectStorageTck)
}
