plugins {
    java
    application
}

dependencies {
    annotationProcessor(platform(libs.micronaut.platform))
    annotationProcessor("io.micronaut:micronaut-inject-java")
    annotationProcessor("io.micronaut.data:micronaut-data-processor")

    implementation(platform(libs.micronaut.platform))
    implementation(projects.micronautObjectStorageCore)
    implementation(projects.micronautObjectStorageLocal)
    implementation("io.micronaut.data:micronaut-data-jdbc")
    implementation("io.micronaut.sql:micronaut-jdbc-hikari")
    implementation("org.flywaydb:flyway-core")
    implementation("org.postgresql:postgresql")
    implementation("com.oracle.database.jdbc:ojdbc17")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.flywaydb:flyway-database-oracle")
    runtimeOnly("ch.qos.logback:logback-classic")
}

application {
    mainClass.set("spike.Bench")
    applicationDefaultJvmArgs = listOf("-Xmx256m")
}
