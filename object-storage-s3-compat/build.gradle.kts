plugins {
    io.micronaut.build.internal.`objectstorage-module`
}

dependencies {
    annotationProcessor(mn.micronaut.inject.java)
    annotationProcessor(mnValidation.micronaut.validation.processor)

    api(projects.micronautObjectStorageCore)

    implementation(mn.micronaut.jackson.databind)
    implementation(mn.micronaut.http.server)
    implementation(mnValidation.micronaut.validation)
    implementation(projects.micronautObjectStorageLocal)

    testImplementation(mn.micronaut.http.client)
    testImplementation(mn.micronaut.http.server.netty)
    testImplementation(mnValidation.micronaut.validation.processor)
}

micronautBuild {
    binaryCompatibility {
        enabledAfter("3.0.0")
    }
}
