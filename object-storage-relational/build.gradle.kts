plugins {
    io.micronaut.build.internal.`objectstorage-module`
}

dependencies {
    api(projects.micronautObjectStorageCore)

    testImplementation(libs.h2)
    testImplementation(mnTest.micronaut.test.spock)
    testImplementation(projects.micronautObjectStorageTck)
}

micronautBuild {
    binaryCompatibility {
        enabledAfter("3.0.0")
    }
}
