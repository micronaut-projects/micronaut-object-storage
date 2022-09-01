import org.sonarqube.gradle.SonarQubeExtension

plugins {
    io.micronaut.build.internal.docs
    io.micronaut.build.internal.`quality-reporting`
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
    }
}

if (System.getenv("SONAR_TOKEN") != null) {
    configure<SonarQubeExtension> {
        properties {
            property("sonar.exclusions", "**/example/**")
        }
    }
}
