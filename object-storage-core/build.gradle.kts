plugins {
    io.micronaut.build.internal.`objectstorage-module`
}

dependencies {
    annotationProcessor(mn.micronaut.inject.java)

    compileOnly(mn.micronaut.http)

    implementation(mn.micronaut.inject.java)
    implementation(mn.micronaut.runtime)
    implementation(mn.micronaut.http.server)

    testImplementation(projects.micronautObjectStorageTck)
    testImplementation(mn.micronaut.http.server.netty)
}
