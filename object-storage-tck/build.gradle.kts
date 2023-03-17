plugins {
    groovy
    io.micronaut.build.internal.common
}

dependencies {
    annotationProcessor(mn.micronaut.inject.groovy)

    api(projects.micronautObjectStorageCore)

    testImplementation(mnValidation.micronaut.validation.processor)
    implementation(mn.micronaut.inject.groovy)
    implementation(mn.micronaut.runtime)
    implementation(mnTest.micronaut.test.spock)
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
    }
}
