plugins {
    groovy
    io.micronaut.build.internal.`objectstorage-module`
}

dependencies {
    implementation(platform(mn.micronaut.bom))

    annotationProcessor(mn.micronaut.inject.groovy)
    implementation(mn.micronaut.inject.groovy)
    implementation(mn.micronaut.runtime)

    api(projects.objectStorageCore)

    implementation(libs.groovy.test)
    implementation(libs.spock.core)
}
