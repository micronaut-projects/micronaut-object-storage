plugins {
    id("io.micronaut.build.internal.objectstorage-base")
    id("io.micronaut.build.internal.bom")
}

micronautBuild {
    //TODO remove when initial release is done
    binaryCompatibility.enabled.set(false)
}

//TODO remove when micronaut-build 5.3.15 is out
tasks.checkVersionCatalogCompatibility.configure { enabled = false }
