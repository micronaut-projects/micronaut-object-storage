package io.micronaut.objectstorage.s3compat

import io.micronaut.context.ApplicationContext
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.objectstorage.request.ListObjectsRequest
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(startApplication = false)
class LocalStorageS3CompatibleOperationsSpec extends Specification {

    @Inject
    ApplicationContext context

    void 'it strips the configured base path from listed keys and continuation tokens'() {
        given:
        ApplicationContext baseContext = ApplicationContext.run([
            'micronaut.object-storage.local.default.enabled'          : true,
            'micronaut.object-storage.s3-compat.assets.storage'       : 'default',
            'micronaut.object-storage.s3-compat.assets.base-path'     : 'exports/public',
            'micronaut.object-storage.s3-compat.assets.enabled'       : true,
        ])
        S3CompatibleOperations operations = baseContext.getBean(S3CompatibleOperations, Qualifiers.byName('assets'))

        when:
        operations.putObject('docs/a.txt', new ByteArrayInputStream('a'.bytes), 1L, 'text/plain')
        operations.putObject('docs/b.txt', new ByteArrayInputStream('b'.bytes), 1L, 'text/plain')
        def page = operations.listObjects(new ListObjectsRequest(1, 'docs/'))
        def nextPage = operations.listObjects(new ListObjectsRequest(5, 'docs/', page.getContinuationToken().orElse(null)))

        then:
        page.objects()*.key == ['docs/a.txt']
        page.getContinuationToken().orElse(null) == 'docs/a.txt'
        nextPage.objects()*.key == ['docs/b.txt']
        nextPage.getContinuationToken().empty

        cleanup:
        baseContext.close()
    }
}
