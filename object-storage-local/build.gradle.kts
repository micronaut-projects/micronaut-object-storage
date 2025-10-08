plugins {
    io.micronaut.build.internal.`objectstorage-module`
}

dependencies {
    api(projects.micronautObjectStorageCore)
    api(mn.micronaut.http.server)

    testImplementation(mnValidation.micronaut.validation)
    testImplementation(mnValidation.micronaut.validation.processor)
    testImplementation(projects.micronautObjectStorageTck)
    testImplementation(projects.micronautObjectStorageAws)
    testImplementation(mn.micronaut.http.server.netty)
}

micronautBuild {
    // New module
    binaryCompatibility {
        enabled.set(false)
    }
}
