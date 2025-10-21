plugins {
    id("groovy")
    id("io.micronaut.build.internal.objectstorage-example")
}
repositories {
    mavenCentral()
}
dependencies {
    testRuntimeOnly(mnTest.junit.platform.suite)
}
