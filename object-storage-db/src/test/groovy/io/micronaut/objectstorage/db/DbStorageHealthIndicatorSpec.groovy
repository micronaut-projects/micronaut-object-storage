package io.micronaut.objectstorage.db

import io.micronaut.context.ApplicationContext
import io.micronaut.health.HealthStatus
import io.micronaut.management.health.indicator.HealthResult
import io.micronaut.objectstorage.db.health.DbStorageHealthIndicator
import reactor.core.publisher.Mono
import spock.lang.Specification

class DbStorageHealthIndicatorSpec extends Specification {

    void 'the indicator is UP when every storage has its tables'() {
        given:
        def ctx = ApplicationContext.run(DbTestProperties.h2([:], 'files') + ['endpoints.health.enabled': 'true'])

        when:
        HealthResult result = Mono.from(ctx.getBean(DbStorageHealthIndicator).result).block()

        then: 'the tables are created on first use, which the health check triggers'
        result.status == HealthStatus.UP
        result.details == [files: 'UP']

        cleanup:
        ctx.close()
    }

    void 'the indicator is DOWN when a mapped table is missing'() {
        given:
        def ctx = ApplicationContext.run(DbTestProperties.h2([
            'table.name'           : 'NO_SUCH_TABLE',
            'table.columns.key'    : 'K',
            'table.columns.content': 'C',
        ], 'files') + ['endpoints.health.enabled': 'true'])

        when:
        HealthResult result = Mono.from(ctx.getBean(DbStorageHealthIndicator).result).block()

        then:
        result.status == HealthStatus.DOWN
        result.details.files.toString().contains('NO_SUCH_TABLE')

        cleanup:
        ctx.close()
    }

    void 'the indicator can be disabled'() {
        given:
        def ctx = ApplicationContext.run(DbTestProperties.h2([:], 'files') + [
            'endpoints.health.enabled'                  : 'true',
            (DbStorageHealthIndicator.ENABLED): 'false',
        ])

        expect:
        !ctx.containsBean(DbStorageHealthIndicator)

        cleanup:
        ctx.close()
    }
}
