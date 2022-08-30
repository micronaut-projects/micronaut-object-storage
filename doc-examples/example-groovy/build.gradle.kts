plugins {
    io.micronaut.build.internal.`objectstorage-example`
    groovy
}

dependencies {
    implementation(projects.objectStorageAws)

    testImplementation(libs.testcontainers.spock)
    testImplementation(libs.testcontainers.localstack)
    testImplementation(libs.amazon.awssdk.v1) {
        because("it is required by testcontainers-localstack")
    }
}
