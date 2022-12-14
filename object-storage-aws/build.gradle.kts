plugins {
    io.micronaut.build.internal.`objectstorage-module`
}

dependencies {
    api(projects.objectStorageCore)
    api(mnAws.micronaut.aws.sdk.v2)
    api(libs.amazon.awssdk.s3)

    implementation(platform(mnAws.micronaut.aws.bom))
    annotationProcessor(mn.micronaut.validation)
    implementation(mn.micronaut.validation)

    testImplementation(projects.objectStorageTck)
    testImplementation(libs.testcontainers.spock)
    testImplementation(libs.testcontainers.localstack)

    testImplementation(libs.amazon.awssdk.v1) {
        because("it is required by testcontainers-localstack")
    }

}
