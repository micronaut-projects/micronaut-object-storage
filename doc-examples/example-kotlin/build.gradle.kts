plugins {
    id("org.jetbrains.kotlin.jvm")
    id("com.google.devtools.ksp")
    id("io.micronaut.build.internal.objectstorage-example")
}

dependencies {
    ksp(mn.micronaut.http.validation)
}
