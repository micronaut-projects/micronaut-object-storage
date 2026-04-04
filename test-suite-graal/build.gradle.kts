plugins {
    id("io.micronaut.build.internal.objectstorage-base")
    id("io.micronaut.minimal.library")
    id("io.micronaut.graalvm")
}

dependencies {
    testAnnotationProcessor(mn.micronaut.inject.java)

    testImplementation(projects.micronautObjectStorageCore)
    testImplementation(projects.micronautObjectStorageLocal)
    testImplementation(mnTest.micronaut.test.junit5)

    testRuntimeOnly(mnLogging.logback.classic)
    testRuntimeOnly(mnTest.junit.jupiter.engine)
    testRuntimeOnly(mnTest.junit.platform.launcher)
}

micronaut {
    version = libs.versions.micronaut.platform.get()
    coreVersion.set(libs.versions.micronaut.asProvider())
    testRuntime("junit5")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
