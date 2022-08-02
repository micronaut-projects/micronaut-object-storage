plugins {
    groovy
    io.micronaut.build.internal.common
}

dependencies {
    annotationProcessor(mn.micronaut.inject.groovy)

    api(projects.objectStorageCore)

    implementation(platform(mn.micronaut.bom))
    implementation(mn.micronaut.inject.groovy)
    implementation(mn.micronaut.runtime)
    implementation(mn.spock)
    implementation(libs.groovy.test)
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
    }
}
