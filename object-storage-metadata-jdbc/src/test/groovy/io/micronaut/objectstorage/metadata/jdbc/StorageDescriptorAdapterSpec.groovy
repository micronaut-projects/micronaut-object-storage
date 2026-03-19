package io.micronaut.objectstorage.metadata.jdbc

import io.micronaut.objectstorage.configuration.AbstractObjectStorageConfiguration
import io.micronaut.objectstorage.metadata.StorageDescriptor
import io.micronaut.objectstorage.metadata.StorageDescriptorResolver
import spock.lang.Specification

class StorageDescriptorAdapterSpec extends Specification {

    void 'provider descriptor adapters expose logical container identity consistently'() {
        expect:
        new AwsTestResolver(new AwsTestConfiguration('pictures', 'aws-bucket')).resolve().with {
            storageName == 'pictures'
            providerId == 'aws'
            logicalContainer == 'aws-bucket'
            providerNamespace.empty
            providerContainer == 'aws-bucket'
        }

        new AzureTestResolver(new AzureTestConfiguration('logos', 'azure-container')).resolve().with {
            storageName == 'logos'
            providerId == 'azure'
            logicalContainer == 'azure-container'
            providerNamespace.empty
            providerContainer == 'azure-container'
        }

        new GcpTestResolver(new GcpTestConfiguration('reports', 'gcp-bucket')).resolve().with {
            storageName == 'reports'
            providerId == 'gcp'
            logicalContainer == 'gcp-bucket'
            providerNamespace.empty
            providerContainer == 'gcp-bucket'
        }

        new OciTestResolver(new OciTestConfiguration('archive', 'oci-bucket', 'oci-namespace')).resolve().with {
            storageName == 'archive'
            providerId == 'oracle-cloud'
            logicalContainer == 'oci-bucket'
            providerNamespace.get() == 'oci-namespace'
            providerContainer == 'oci-bucket'
            providerContainerIdentity == 'oci-namespace/oci-bucket'
        }

        new LocalTestResolver(new LocalTestConfiguration('default', 'build/storage/default')).resolve().with {
            storageName == 'default'
            providerId == 'local'
            logicalContainer == 'default'
            providerNamespace.present
            providerContainer == 'default'
        }
    }

    private static final class AwsTestResolver implements StorageDescriptorResolver {
        private final StorageDescriptor descriptor
        AwsTestResolver(AwsTestConfiguration configuration) {
            descriptor = new StorageDescriptor(configuration.name, 'aws', configuration.bucket, null, configuration.bucket)
        }
        @Override
        StorageDescriptor resolve() { descriptor }
    }

    private static final class AzureTestResolver implements StorageDescriptorResolver {
        private final StorageDescriptor descriptor
        AzureTestResolver(AzureTestConfiguration configuration) {
            descriptor = new StorageDescriptor(configuration.name, 'azure', configuration.container, null, configuration.container)
        }
        @Override
        StorageDescriptor resolve() { descriptor }
    }

    private static final class GcpTestResolver implements StorageDescriptorResolver {
        private final StorageDescriptor descriptor
        GcpTestResolver(GcpTestConfiguration configuration) {
            descriptor = new StorageDescriptor(configuration.name, 'gcp', configuration.bucket, null, configuration.bucket)
        }
        @Override
        StorageDescriptor resolve() { descriptor }
    }

    private static final class OciTestResolver implements StorageDescriptorResolver {
        private final StorageDescriptor descriptor
        OciTestResolver(OciTestConfiguration configuration) {
            descriptor = new StorageDescriptor(configuration.name, 'oracle-cloud', configuration.bucket, configuration.namespace, configuration.bucket)
        }
        @Override
        StorageDescriptor resolve() { descriptor }
    }

    private static final class LocalTestResolver implements StorageDescriptorResolver {
        private final StorageDescriptor descriptor
        LocalTestResolver(LocalTestConfiguration configuration) {
            descriptor = new StorageDescriptor(configuration.name, 'local', configuration.name, configuration.path, configuration.name)
        }
        @Override
        StorageDescriptor resolve() { descriptor }
    }

    private static final class AwsTestConfiguration extends AbstractObjectStorageConfiguration {
        final String bucket
        AwsTestConfiguration(String name, String bucket) { super(name); this.bucket = bucket }
        @Override boolean isEnabled() { true }
    }

    private static final class AzureTestConfiguration extends AbstractObjectStorageConfiguration {
        final String container
        AzureTestConfiguration(String name, String container) { super(name); this.container = container }
        @Override boolean isEnabled() { true }
    }

    private static final class GcpTestConfiguration extends AbstractObjectStorageConfiguration {
        final String bucket
        GcpTestConfiguration(String name, String bucket) { super(name); this.bucket = bucket }
        @Override boolean isEnabled() { true }
    }

    private static final class OciTestConfiguration extends AbstractObjectStorageConfiguration {
        final String bucket
        final String namespace
        OciTestConfiguration(String name, String bucket, String namespace) { super(name); this.bucket = bucket; this.namespace = namespace }
        @Override boolean isEnabled() { true }
    }

    private static final class LocalTestConfiguration extends AbstractObjectStorageConfiguration {
        final String path
        LocalTestConfiguration(String name, String path) { super(name); this.path = path }
        @Override boolean isEnabled() { true }
    }
}
