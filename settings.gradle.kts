import io.micronaut.build.MicronautBuildSettingsExtension

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("io.micronaut.build.shared.settings") version "5.3.14"
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

configure<MicronautBuildSettingsExtension> {
    importMicronautCatalog()
}
