plugins {
    io.micronaut.build.internal.`objectstorage-module`
}

dependencies {
    api(projects.micronautObjectStorageCore)
    api(mnAws.micronaut.aws.sdk.v2)
    api(libs.amazon.awssdk.s3)

    implementation(platform(mnAws.micronaut.aws.bom))
    annotationProcessor(mnValidation.micronaut.validation.processor)
    implementation(mnValidation.micronaut.validation)

    testImplementation(projects.micronautObjectStorageTck)
    testImplementation(libs.testcontainers.spock)
    testImplementation(libs.testcontainers.localstack)

    testImplementation(libs.amazon.awssdk.v1) {
        because("it is required by testcontainers-localstack")
    }

}
