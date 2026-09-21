plugins {
    id("io.micronaut.build.internal.objectstorage-base")
    id("io.micronaut.build.internal.python")
}

dependencies {
    implementation(platform(mn.micronaut.core.bom))
    implementation(platform(mnAws.micronaut.aws.bom))
    implementation(platform(mnAzure.micronaut.azure.bom))
    implementation(platform(mnGcp.micronaut.gcp.bom))
    implementation(platform(mnOraclecloud.micronaut.oraclecloud.bom))
    implementation(mn.micronaut.inject.python)
    implementation(mn.micronaut.context.python)
    implementation(mn.micronaut.runtime)
    implementation(mn.micronaut.http.server.netty)
    implementation(mn.micronaut.http.client)
    implementation(mn.micronaut.jackson.databind)
    implementation(mnValidation.micronaut.validation)
    implementation(projects.micronautObjectStorageAws)
    implementation(projects.micronautObjectStorageAzure)
    implementation(projects.micronautObjectStorageGcp)
    implementation(projects.micronautObjectStorageOracleCloud)

    // The Java LocalStackTestConfigurer (@ContextConfigurer, src/test/java) is processed by javac
    testAnnotationProcessor(platform(mn.micronaut.core.bom))
    testAnnotationProcessor(mn.micronaut.inject.java)
    // Annotation processors of the Python sources MUST be testImplementation (not testAnnotationProcessor):
    // the Python compiler (micronaut-inject-python) takes the (jar-resolved) compile classpath as its annotation processor path.
    testImplementation(mnValidation.micronaut.validation.processor)
    testImplementation(mn.micronaut.inject.python.test)
    testImplementation(mnTest.micronaut.test.junit5)
    testImplementation(mnTestResources.testcontainers.localstack)
    testImplementation(libs.amazon.awssdk.v1) {
        because("it is required by testcontainers-localstack")
    }
    testImplementation(project(":test-suite-utils"))

    runtimeOnly(mnLogging.logback.classic)
    runtimeOnly(mn.snakeyaml)
    testRuntimeOnly(mnTest.junit.jupiter.engine)
    testRuntimeOnly(mnTest.junit.platform.launcher)
    testRuntimeOnly(libs.bytebuddy)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    systemProperty("micronaut.python.pool.enabled", "false")
}
