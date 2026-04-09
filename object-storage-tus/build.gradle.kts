plugins {
    io.micronaut.build.internal.`objectstorage-module`
}

dependencies {
    api(projects.micronautObjectStorageCore)

    implementation(projects.micronautObjectStorageLocal)
    implementation(mn.micronaut.http.server)

    testImplementation(projects.micronautObjectStorageLocal)
}

micronautBuild {
    binaryCompatibility {
        enabledAfter("3.0.0")
    }
}
