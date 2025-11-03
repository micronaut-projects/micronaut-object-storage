plugins {
    id("org.jetbrains.kotlin.jvm")
    id("com.google.devtools.ksp")
    id("io.micronaut.build.internal.objectstorage-example")
    id("io.micronaut.build.internal.kotlin-base")
}

dependencies {
    ksp(mn.micronaut.http.validation)
}
