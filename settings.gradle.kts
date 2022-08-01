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

include("object-storage")
include("object-storage-bom")
include("object-storage-tck")

include("azure-object-storage")
include("google-cloud-object-storage")
include("aws-object-storage")
include("oracle-cloud-object-storage")

configure<MicronautBuildSettingsExtension> {
    importMicronautCatalog()
}
