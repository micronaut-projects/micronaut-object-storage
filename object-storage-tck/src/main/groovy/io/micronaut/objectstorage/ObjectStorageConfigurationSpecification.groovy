/*
 * Copyright 2017-2023 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.objectstorage

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Specification

abstract class ObjectStorageConfigurationSpecification<O extends ObjectStorageOperations<?, ?, ?>> extends Specification {

    void "it can be disabled"() {
        given:
        String configProp = getConfigPrefix() + ".default.enabled"
        def ctx = ApplicationContext.run([(configProp): false])

        expect:
        !ctx.containsBean(getObjectStorage())

        cleanup:
        ctx.close()
    }

    void "it can be disabled with multiple configurations"() {
        given:
        String defaultProp = getConfigPrefix() + ".default.enabled"
        String otherProp = getConfigPrefix() + ".other.enabled"
        def ctx = ApplicationContext.run([
                (defaultProp): false,
                (otherProp): true
        ], Environment.TEST)

        expect:
        !ctx.containsBean(getObjectStorage(), Qualifiers.byName("default"))
        ctx.containsBean(getObjectStorage(), Qualifiers.byName("other"))

        cleanup:
        ctx.close()
    }

    void "the whole module can be disabled"() {
        given:
        String moduleProp = getConfigPrefix() + ".enabled"
        String configProp = getConfigPrefix() + ".default.enabled"
        def ctx = ApplicationContext.run([
                (configProp): true,
                (moduleProp): false
        ])

        expect:
        !ctx.containsBean(getObjectStorage())

        cleanup:
        ctx.close()
    }

    abstract String getConfigPrefix()

    abstract Class<O> getObjectStorage()
}
