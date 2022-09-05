plugins {
    io.micronaut.build.internal.`objectstorage-module`
}

dependencies {
    annotationProcessor(mn.micronaut.inject.java)

    compileOnly(mn.micronaut.http)

    implementation(mn.micronaut.inject.java)
    implementation(mn.micronaut.runtime)

    testImplementation(projects.objectStorageTck)
}
