plugins {
    id("io.micronaut.build.internal.objectstorage-example")
    id ("java-library")
}

dependencies {
    annotationProcessor(mn.micronaut.http.validation)
    testRuntimeOnly(mnTest.junit.platform.suite)
}
