plugins {
    groovy
    io.micronaut.build.internal.common
}

dependencies {
    annotationProcessor(mn.micronaut.inject.groovy)

    api(projects.micronautObjectStorageCore)
    api(mn.micronaut.http)
    api(mn.reactor)

    testImplementation(mnValidation.micronaut.validation.processor)
    implementation(mn.micronaut.inject.groovy)
    implementation(mn.micronaut.context)
    implementation(mnTest.micronaut.test.spock)
    testRuntimeOnly(mn.micronaut.jackson.databind)
}

repositories {
    mavenCentral()
}
