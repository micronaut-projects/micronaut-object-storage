plugins {
    io.micronaut.build.internal.bom
}
micronautBuild {
    tasks.named("checkVersionCatalogCompatibility") { onlyIf { false } }
}
