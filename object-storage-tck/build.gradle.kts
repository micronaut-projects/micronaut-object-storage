plugins {
    groovy
    io.micronaut.build.internal.common
}

dependencies {
    annotationProcessor(mn.micronaut.inject.groovy)

    api(projects.objectStorageCore)

    implementation(mn.micronaut.inject.groovy)
    implementation(mn.micronaut.runtime)
    implementation(mnTest.micronaut.test.spock)
}

dependencies {
    modules {
        module("org.codehaus.groovy:groovy") {
            replacedBy("org.apache.groovy:groovy", "google-collections is now part of Guava")
        }
    }
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
    }
}
