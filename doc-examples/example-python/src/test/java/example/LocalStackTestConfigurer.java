package example;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.ApplicationContextConfigurer;
import io.micronaut.context.annotation.ContextConfigurer;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertySource;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;

import static io.micronaut.objectstorage.test.ObjectStorageTestConstants.LOCAL_STACK_DOCKER_IMAGE;

/**
 * Starts the shared LocalStack container and supplies its S3 endpoint and credentials to the Python
 * tests run with the {@code ec2} environment, like the {@code TestPropertyProvider} of the Java,
 * Kotlin and Groovy example tests does.
 * <p>
 * The configurer is written in Java because Micronaut Test calls {@code TestPropertyProvider.getProperties()}
 * before the application context, and with it the GraalPy runtime, exists, so a Python test class
 * cannot start the container and provide the properties itself. It uses the
 * {@link #configure(ApplicationContext)} callback because the {@link io.micronaut.context.ApplicationContextBuilder}
 * is configured before {@code @MicronautTest} selects the environments, so the {@code ec2} environment
 * can only be checked on the built context.
 */
@ContextConfigurer
public class LocalStackTestConfigurer implements ApplicationContextConfigurer {

    public static final String BUCKET_NAME = "profile-pictures-bucket";
    public static final String PICTURES_BUCKET_NAME = "pictures-bucket";

    private static LocalStackContainer localstack;

    @Override
    public void configure(ApplicationContext applicationContext) {
        Environment environment = applicationContext.getEnvironment();
        if (environment.getActiveNames().contains(Environment.AMAZON_EC2)) {
            LocalStackContainer container = localstack();
            environment.addPropertySource(PropertySource.of("localstack", Map.of(
                "aws.accessKeyId", container.getAccessKey(),
                "aws.secretKey", container.getSecretKey(),
                "aws.region", container.getRegion(),
                "aws.services.s3.endpoint-override", container.getEndpoint().toString()
            )));
        }
    }

    private static synchronized LocalStackContainer localstack() {
        if (localstack == null) {
            LocalStackContainer container = new LocalStackContainer(DockerImageName.parse(LOCAL_STACK_DOCKER_IMAGE))
                .withServices("s3");
            container.start();
            for (String bucket : List.of(BUCKET_NAME, PICTURES_BUCKET_NAME)) {
                try {
                    container.execInContainer("awslocal", "s3api", "create-bucket", "--bucket", bucket);
                } catch (Exception e) {
                    throw new IllegalStateException("Could not create the LocalStack bucket " + bucket, e);
                }
            }
            Runtime.getRuntime().addShutdownHook(new Thread(container::stop));
            localstack = container;
        }
        return localstack;
    }
}
