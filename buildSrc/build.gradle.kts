plugins {
    `groovy-gradle-plugin`
}

repositories {
    gradlePluginPortal()
    mavenCentral()

    // need to pull in micronaut-gradle-plugin:4.0.0-SNAPSHOT
    // to prevent leaking codehaus groovy from micronaut-test 3.x
    maven { setUrl("https://s01.oss.sonatype.org/content/repositories/snapshots/") }
}

dependencies {
    implementation(libs.gradle.micronaut)
    implementation(libs.gradle.kotlin)
    implementation(libs.gradle.kotlin.allopen)
}
