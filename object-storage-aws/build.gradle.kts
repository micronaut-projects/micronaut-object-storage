plugins {
    io.micronaut.build.internal.`objectstorage-module`
}

dependencies {
    api(projects.micronautObjectStorageCore)
    api(mnAws.micronaut.aws.sdk.v2)
    api(libs.amazon.awssdk.s3)

    implementation(platform(mnAws.micronaut.aws.bom))
    implementation(platform(libs.netty.bom))
    annotationProcessor(mnValidation.micronaut.validation.processor)
    implementation(mnValidation.micronaut.validation)

    testImplementation(mnValidation.micronaut.validation.processor)
    testImplementation(projects.micronautObjectStorageTck)
    testImplementation(mnTestResources.testcontainers.localstack)
    testImplementation(platform(libs.netty.bom))

    testImplementation(libs.amazon.awssdk.v1) {
        because("it is required by testcontainers-localstack")
    }
    testImplementation(project(":test-suite-utils"))

}
micronautBuild {
    binaryCompatibility {
        enabledAfter("3.0.0")
    }
}
