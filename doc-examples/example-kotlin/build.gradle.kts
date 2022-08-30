plugins {
    org.jetbrains.kotlin.jvm
    org.jetbrains.kotlin.kapt
    org.jetbrains.kotlin.plugin.allopen
    io.micronaut.build.internal.`objectstorage-example`
}

dependencies {
    implementation(projects.objectStorageAws)

    testImplementation(libs.testcontainers.spock)
    testImplementation(libs.testcontainers.localstack)
    testImplementation(libs.amazon.awssdk.v1) {
        because("it is required by testcontainers-localstack")
    }
}
