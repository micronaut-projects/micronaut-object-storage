plugins {
    io.micronaut.build.internal.`objectstorage-module`
}

dependencies {
    annotationProcessor(mn.micronaut.inject.java)

    compileOnly(mn.micronaut.http)
    api("io.micronaut:micronaut-core-reactive")

    implementation(mn.micronaut.inject)
    implementation(mn.micronaut.context)
    implementation(mn.micronaut.http.server)

    testImplementation(projects.micronautObjectStorageTck)
    testImplementation(mn.micronaut.http.server.netty)
    
    testImplementation(mn.reactor)

}
micronautBuild {
    binaryCompatibility {
        enabledAfter("3.0.0")
    }
}
