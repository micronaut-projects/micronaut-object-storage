plugins {
    `groovy-gradle-plugin`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation(libs.gradle.micronaut)
    implementation(libs.gradle.kotlin)
    implementation(libs.gradle.kotlin.allopen)
}
