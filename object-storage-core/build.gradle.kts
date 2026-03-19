plugins {
    io.micronaut.build.internal.`objectstorage-module`
}

val objectStorageTckProject = project(":micronaut-object-storage-tck")

dependencies {
    annotationProcessor(mn.micronaut.inject.java)

    compileOnly(mn.micronaut.http)

    implementation(mn.micronaut.inject)
    implementation(mn.micronaut.context)
    implementation(mn.micronaut.http.server)

    testCompileOnly(files("${objectStorageTckProject.projectDir}/build/classes/groovy/main"))
    testCompileOnly(files("${objectStorageTckProject.projectDir}/build/resources/main"))
    testImplementation(projects.micronautObjectStorageTck)
    testImplementation(mn.micronaut.http.server.netty)

    testImplementation(mn.reactor)
}

tasks.named("compileTestGroovy") {
    dependsOn(":micronaut-object-storage-tck:compileGroovy")
}
