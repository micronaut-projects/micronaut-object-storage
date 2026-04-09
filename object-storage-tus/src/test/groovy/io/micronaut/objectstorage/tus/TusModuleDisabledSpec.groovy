package io.micronaut.objectstorage.tus

import io.micronaut.context.ApplicationContext
import io.micronaut.objectstorage.local.LocalStorageConfiguration
import spock.lang.Specification

class TusModuleDisabledSpec extends Specification {

    void 'local tus backends stay disabled until the module is enabled'() {
        given:
        ApplicationContext context = ApplicationContext.run([
            (LocalStorageConfiguration.PREFIX + '.default.enabled'): 'true',
            (TusModuleConfiguration.PREFIX + '.enabled')         : 'false'
        ])

        expect:
        !context.containsBean(TusController)
        context.getBeansOfType(TusUploadBackend).isEmpty()

        cleanup:
        context.close()
    }
}
