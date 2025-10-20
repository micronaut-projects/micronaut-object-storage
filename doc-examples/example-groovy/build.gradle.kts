plugins {
    id("groovy")
    id("io.micronaut.build.internal.objectstorage-example")
    id ("java-library")
}
repositories {
    mavenCentral()
}
dependencies {
    testRuntimeOnly(mnTest.junit.platform.suite)
}
