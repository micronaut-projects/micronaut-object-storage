import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("com.google.devtools.ksp")
    id("io.micronaut.build.internal.objectstorage-example")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    this.compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
}

dependencies {
    ksp(mn.micronaut.http.validation)
}
