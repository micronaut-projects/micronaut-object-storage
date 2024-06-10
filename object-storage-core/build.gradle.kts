plugins {
    io.micronaut.build.internal.`objectstorage-module`
}

dependencies {
    annotationProcessor(mn.micronaut.inject.java)

    compileOnly(mn.micronaut.http)

    implementation(mn.micronaut.inject)
    implementation(mn.micronaut.context)
    implementation(mn.micronaut.http.server)

    testImplementation(projects.micronautObjectStorageTck)
    testImplementation(mn.micronaut.http.server.netty)
    
    testImplementation(mn.reactor)

}
