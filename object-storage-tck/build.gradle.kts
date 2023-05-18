plugins {
    groovy
    io.micronaut.build.internal.common
}

dependencies {
    annotationProcessor(mn.micronaut.inject.groovy)

    api(projects.micronautObjectStorageCore)
    api(mn.micronaut.http)

    testImplementation(mnValidation.micronaut.validation.processor)
    implementation(mn.micronaut.inject.groovy)
    implementation(mn.micronaut.context)
    implementation(mnTest.micronaut.test.spock)
}

repositories {
    mavenCentral()
}
