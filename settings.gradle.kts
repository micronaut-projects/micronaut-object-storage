import io.micronaut.build.MicronautBuildSettingsExtension

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("io.micronaut.build.shared.settings") version "6.4.4"
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "object-storage-parent"

include("object-storage-core")
include("object-storage-bom")
include("object-storage-tck")

include("object-storage-aws")
include("object-storage-azure")
include("object-storage-gcp")
include("object-storage-oracle-cloud")

include("doc-examples:example-java")
include("doc-examples:example-groovy")
include("doc-examples:example-kotlin")

configure<MicronautBuildSettingsExtension> {
    addSnapshotRepository()
    useStandardizedProjectNames.set(true)
    importMicronautCatalog()
    importMicronautCatalog("micronaut-aws")
    importMicronautCatalog("micronaut-azure")
    importMicronautCatalog("micronaut-gcp")
    importMicronautCatalog("micronaut-oracle-cloud")
    importMicronautCatalog("micronaut-validation")
}
