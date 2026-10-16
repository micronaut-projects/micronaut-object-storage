plugins {
    io.micronaut.build.internal.`objectstorage-module`
}

dependencies {
    api(projects.micronautObjectStorageCore)
    api(mnOraclecloud.micronaut.oraclecloud.sdk) {
        exclude(group = "org.codehaus.groovy", module = "groovy")
    }
    api(mnOraclecloud.micronaut.oraclecloud.bmc.objectstorage)

    implementation(platform(mnOraclecloud.micronaut.oraclecloud.bom))
    constraints {
        api(libs.jackson.core) {
            because("Require a version without the known Jackson vulnerabilities brought by the Oracle Cloud SDK")
        }
        api(libs.jackson.databind) {
            because("Require a version without the known Jackson vulnerabilities brought by the Oracle Cloud SDK")
        }
    }

    testImplementation(mnValidation.micronaut.validation)
    testImplementation(mnValidation.micronaut.validation.processor)
    testImplementation(projects.micronautObjectStorageTck)
    testImplementation(mnTestResources.testcontainers.core)
}
micronautBuild {
    binaryCompatibility {
        enabledAfter("3.0.0")
    }
}
