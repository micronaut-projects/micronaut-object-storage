plugins {
    io.micronaut.build.internal.`objectstorage-module`
}

dependencies {
    api(projects.micronautObjectStorageDb)
}

micronautBuild {
    binaryCompatibility {
        enabledAfter("3.2.0")
    }
}
