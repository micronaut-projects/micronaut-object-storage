plugins {
    io.micronaut.build.internal.`objectstorage-module`
}

dependencies {
    api(projects.micronautObjectStorageCore)

    testImplementation(mnValidation.micronaut.validation)
    testImplementation(mnValidation.micronaut.validation.processor)
    testImplementation(projects.micronautObjectStorageTck)
    testImplementation(projects.micronautObjectStorageAws)
}

micronautBuild {
    binaryCompatibility {
        enabledAfter("3.0.0")
    }
}
