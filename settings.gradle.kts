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

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven {
            url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
        }
    }
}

//TODO remove when https://github.com/micronaut-projects/micronaut-aws/pull/1420 is released
includeBuild("../micronaut-aws") {
    dependencySubstitution {
        substitute(module("io.micronaut.aws:micronaut-aws-bom")).using(project(":aws-bom"))
        substitute(module("io.micronaut.aws:micronaut-aws-sdk-v2")).using(project(":aws-sdk-v2"))
    }
}
