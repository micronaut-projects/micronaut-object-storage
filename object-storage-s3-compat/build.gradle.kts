plugins {
    io.micronaut.build.internal.`objectstorage-module`
}

dependencies {
    annotationProcessor(mn.micronaut.inject.java)
    annotationProcessor(mnValidation.micronaut.validation.processor)

    api(projects.micronautObjectStorageCore)

    implementation(mn.micronaut.jackson.databind)
    implementation(mn.micronaut.http.server)
    implementation(mnValidation.micronaut.validation)
    implementation(projects.micronautObjectStorageAws)
    implementation(projects.micronautObjectStorageLocal)

    testImplementation(platform(mnAws.micronaut.aws.bom))
    testImplementation(mnAws.micronaut.aws.sdk.v2)
    testImplementation(libs.amazon.awssdk.s3)
    testImplementation(mn.micronaut.http.client)
    testImplementation(mn.micronaut.http.server.netty)
    testImplementation(mnValidation.micronaut.validation.processor)
    testImplementation(mnTestResources.testcontainers.localstack)
    testImplementation(project(":test-suite-utils"))

    testImplementation(libs.amazon.awssdk.v1) {
        because("it is required by testcontainers-localstack")
    }
}

micronautBuild {
    binaryCompatibility {
        enabledAfter("3.0.0")
    }
}
