package io.micronaut.objectstorage.local

import io.micronaut.context.ApplicationContext
import io.micronaut.core.io.ResourceLoader
import io.micronaut.core.io.ResourceResolver
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.request.UploadRequest
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

import static io.micronaut.objectstorage.local.LocalStorageConfiguration.PREFIX

class LocalStorageResourceLoaderSpec extends Specification {

    @TempDir
    Path tempDir

    void 'it exposes named storage resource loaders through the application context'() {
        given:
        ApplicationContext context = ApplicationContext.run(
            [
                (PREFIX + '.public-images.path'): tempDir.toString()
            ]
        )
        def operations = context.getBean(ObjectStorageOperations, Qualifiers.byName('public-images'))
        operations.upload(UploadRequest.fromBytes('hello-loader'.bytes, 'avatars/logo.txt'))
        def resolver = new ResourceResolver(context.getBeansOfType(ResourceLoader).toList())

        expect:
        resolver.getResourceAsStream('public-images://avatars/logo.txt').get().text == 'hello-loader'
        resolver.getLoaderForBasePath('public-images://avatars/').get().getResourceAsStream('logo.txt').get().text == 'hello-loader'

        cleanup:
        context.close()
    }

    void 'it rejects rebased relative lookups that escape the configured base path'() {
        given:
        ApplicationContext context = ApplicationContext.run(
            [
                (PREFIX + '.public-images.path'): tempDir.toString()
            ]
        )
        def operations = context.getBean(ObjectStorageOperations, Qualifiers.byName('public-images'))
        operations.upload(UploadRequest.fromBytes('hello-loader'.bytes, 'avatars/logo.txt'))
        operations.upload(UploadRequest.fromBytes('top-secret'.bytes, 'secret.txt'))
        def rebased = new ResourceResolver(context.getBeansOfType(ResourceLoader).toList())
            .getLoaderForBasePath('public-images://avatars/')
            .get()

        expect:
        rebased.getResourceAsStream('nested/../logo.txt').get().text == 'hello-loader'
        rebased.supportsPrefix('../secret.txt')

        when:
        rebased.getResourceAsStream('../secret.txt')

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('escapes the configured base path')

        cleanup:
        context.close()
    }
}
