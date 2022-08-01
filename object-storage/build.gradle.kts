plugins {
    id("io.micronaut.build.internal.objectstorage-module")
}

dependencies {
    annotationProcessor(mn.micronaut.inject.java)
    implementation(mn.micronaut.inject.java)
    implementation(mn.micronaut.runtime)
}
