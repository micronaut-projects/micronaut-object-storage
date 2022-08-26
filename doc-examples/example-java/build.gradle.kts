plugins {
    io.micronaut.build.internal.`objectstorage-example`
}
dependencies {
    annotationProcessor(mn.micronaut.http.validation)

    implementation(mn.micronaut.http.client)
    implementation(mn.micronaut.jackson.databind)
    implementation(mn.micronaut.validation)
    implementation(mn.jakarta.annotation.api)

    implementation(projects.objectStorageAws)
    implementation(projects.objectStorageAzure)
    implementation(projects.objectStorageGcp)
    implementation(projects.objectStorageOracleCloud)

    runtimeOnly(mn.logback)

    testAnnotationProcessor(platform(mn.micronaut.bom))
}
