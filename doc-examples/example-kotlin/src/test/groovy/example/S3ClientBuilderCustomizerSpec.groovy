package example

import io.micronaut.context.ApplicationContext
import software.amazon.awssdk.core.client.config.SdkClientConfiguration
import software.amazon.awssdk.core.client.config.SdkClientOption
import software.amazon.awssdk.services.s3.S3Client
import spock.lang.Specification

import java.time.Duration
import java.time.temporal.ChronoUnit

class S3ClientBuilderCustomizerSpec extends Specification {

    void "it can customize S3 client"() {
        given:
        ApplicationContext ctx = ApplicationContext.run("aws.region": "us-east-1")

        when:
        S3Client client = ctx.getBean(S3Client)
        SdkClientConfiguration configuration = client.clientConfiguration

        then:
        configuration.option(SdkClientOption.API_CALL_TIMEOUT) == Duration.of(60, ChronoUnit.SECONDS)

        cleanup:
        ctx.stop()
    }
}
