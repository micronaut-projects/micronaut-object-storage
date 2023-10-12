package io.micronaut.objectstorage.local

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

class LocalStoragePathTraversalSpec extends Specification {
    def 'path traversal'() {
        given:
        Path tmp = Files.createTempDirectory("micronaut-object-storage")
        Path secret = tmp.resolve("secret")
        Files.writeString(secret, "bar")
        Path bucket = tmp.resolve("foo")
        Files.createDirectory(bucket)
        Path pub = bucket.resolve("public")
        Files.writeString(pub, "baz")

        ApplicationContext ctx = ApplicationContext.run(["micronaut.object-storage.local.a.path": bucket.toString()])

        when:
        def publicEntry = ctx.getBean(LocalStorageOperations).retrieve("public")
        then:
        publicEntry.isPresent()
        new String(publicEntry.get().inputStream.readAllBytes(), StandardCharsets.UTF_8) == "baz"

        when:
        ctx.getBean(LocalStorageOperations).retrieve("../secret")
        then:
        thrown IllegalArgumentException

        cleanup:
        ctx.close()
        Files.walkFileTree(tmp, new SimpleFileVisitor<Path>() {
            @Override
            FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file)
                return FileVisitResult.CONTINUE
            }

            @Override
            FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir)
                return FileVisitResult.CONTINUE
            }
        })
    }
}
