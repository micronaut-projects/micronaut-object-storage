package example

import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.env.Environment
import io.micronaut.objectstorage.aws.AwsS3Operations
import io.micronaut.objectstorage.metadata.ObjectMetadataReconciliationState
import io.micronaut.objectstorage.metadata.MetadataAwareObjectStorageOperations
import io.micronaut.objectstorage.metadata.ObjectStorageLifecycleHook
import io.micronaut.objectstorage.metadata.ObjectStorageMetadataOperations
import io.micronaut.objectstorage.metadata.ObjectStorageOperationContext
import io.micronaut.objectstorage.metadata.ObjectStorageOperationOutcome
import io.micronaut.objectstorage.metadata.ObjectMetadataSyncStatus
import io.micronaut.objectstorage.metadata.ObjectStorageOperationType
import io.micronaut.objectstorage.metadata.StorageContainerMetadata
import io.micronaut.objectstorage.metadata.StorageDescriptor
import io.micronaut.objectstorage.metadata.StorageObjectMetadata
import io.micronaut.objectstorage.metadata.StorageObjectMetadataQuery
import io.micronaut.objectstorage.metadata.TenantResolver
import io.micronaut.objectstorage.request.UploadRequest
import io.micronaut.objectstorage.response.UploadResponse
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.utility.DockerImageName
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.time.Instant
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

import static io.micronaut.objectstorage.test.ObjectStorageTestConstants.LOCAL_STACK_DOCKER_IMAGE

@MicronautTest(environments = [Environment.AMAZON_EC2, "metadata-profile"])
class MetadataProfileServiceSpec extends Specification implements TestPropertyProvider {

    public static final String BUCKET_NAME = "profile-pictures-bucket"
    public static final String USER_ID = "user123"
    public static final String OBJECT_KEY = USER_ID + "/logo.png"

    @Shared
    @AutoCleanup
    public LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse(LOCAL_STACK_DOCKER_IMAGE))
            .withServices(LocalStackContainer.Service.S3)

    @Inject
    MetadataProfileService service

    @Inject
    @Named("pictures")
    AwsS3Operations awsS3Operations

    @Inject
    @Named("pictures")
    MetadataAwareObjectStorageOperations metadataAwareStorage

    @Inject
    @Named("pictures")
    ObjectStorageMetadataOperations metadataOperations

    @Inject
    @Named("pictures")
    StorageDescriptor storageDescriptor

    @Inject
    @Named("pictures")
    InMemoryMetadataAwareObjectStorageOperations metadataWorkflow

    @Inject
    RecordingLifecycleHook hook

    @Override
    Map<String, String> getProperties() {
        localstack.start()
        localstack.execInContainer("awslocal", "s3api", "create-bucket", "--bucket", BUCKET_NAME)
        return [
                "aws.accessKeyId": localstack.getAccessKey(),
                "aws.secretKey": localstack.getSecretKey(),
                "aws.region": localstack.getRegion(),
                "aws.services.s3.endpoint-override": localstack.getEndpointOverride(LocalStackContainer.Service.S3),
                "micronaut.object-storage.aws.pictures.bucket": BUCKET_NAME,
                "micronaut.object-storage.default-storage": "pictures",
                "micronaut.object-storage.tenant-id": "tenant-a"
        ]
    }

    void "it exercises named metadata-aware upload workflow and metadata-backed prefix queries"() {
        expect:
        storageDescriptor.storageName == "pictures"
        storageDescriptor.providerContainer == BUCKET_NAME
        metadataAwareStorage.getMetadataOperations().is(metadataOperations)
        !service.containerMetadata().present
        service.listByPrefix(USER_ID).isEmpty()

        when:
        def uploadResult = service.uploadProfilePicture("tenant-a", USER_ID, "logo.png")

        then:
        uploadResult.key == OBJECT_KEY
        metadataWorkflow.uploadCount.get() == 1
        awsS3Operations.retrieve(OBJECT_KEY).present
        metadataOperations.findContainer(storageDescriptor).present
        metadataOperations.findObject(storageDescriptor, OBJECT_KEY).present
        metadataOperations.listObjects(StorageObjectMetadataQuery.all().withStorageName("pictures").withObjectKeyPrefix(USER_ID))*.objectKey == [OBJECT_KEY]
        metadataOperations.listObjects(StorageObjectMetadataQuery.all().withStorageName("logos").withObjectKeyPrefix(USER_ID)).isEmpty()
        service.listByPrefix(USER_ID) == [OBJECT_KEY]
        hook.events.size() == 2
        hook.events[0] == new Event("before", "pictures", OBJECT_KEY, false, null, false)
        hook.events[1] == new Event("after", "pictures", OBJECT_KEY, true, ObjectStorageOperationType.UPLOAD, true)
    }

    @Singleton
    @io.micronaut.context.annotation.Requires(env = "metadata-profile")
    @Replaces(ObjectStorageLifecycleHook)
    static class RecordingLifecycleHook implements ObjectStorageLifecycleHook {
        private final ObjectStorageMetadataOperations metadataOperations
        List<Event> events = new CopyOnWriteArrayList<>()

        RecordingLifecycleHook(@Named("pictures") ObjectStorageMetadataOperations metadataOperations) {
            this.metadataOperations = metadataOperations
        }

        @Override
        void before(ObjectStorageOperationContext context) {
            events.add(new Event(
                "before",
                context.storageDescriptor.storageName,
                context.objectKey,
                metadataOperations.findContainer(context.storageDescriptor).present,
                null,
                false
            ))
        }

        @Override
        void after(ObjectStorageOperationContext context, ObjectStorageOperationOutcome outcome) {
            events.add(new Event(
                "after",
                context.storageDescriptor.storageName,
                context.objectKey,
                metadataOperations.findContainer(context.storageDescriptor).present,
                outcome.operationType,
                outcome.reconciliationId.present
            ))
        }
    }

    @Singleton
    @io.micronaut.context.annotation.Requires(env = "metadata-profile")
    @Replaces(TenantResolver)
    static class FixedTenantResolver implements TenantResolver {
        @Override
        String resolveTenantId() {
            return "tenant-a"
        }
    }

    @Factory
    @io.micronaut.context.annotation.Requires(env = "metadata-profile")
    static class MetadataWorkflowTestFactory {
        @Singleton
        @Named("pictures")
        StorageDescriptor picturesStorageDescriptor() {
            new StorageDescriptor("pictures", "aws", BUCKET_NAME, null, BUCKET_NAME)
        }

        @Singleton
        @Named("pictures")
        InMemoryMetadataOperations picturesMetadataOperations(TenantResolver tenantResolver,
                                                              @Named("pictures") StorageDescriptor descriptor) {
            new InMemoryMetadataOperations(tenantResolver, descriptor)
        }

        @Singleton
        @Named("pictures")
        InMemoryMetadataAwareObjectStorageOperations picturesMetadataAwareOperations(@Named("pictures") AwsS3Operations delegate,
                                                                                      @Named("pictures") InMemoryMetadataOperations metadataOperations,
                                                                                      @Named("pictures") StorageDescriptor descriptor,
                                                                                      TenantResolver tenantResolver,
                                                                                      Collection<ObjectStorageLifecycleHook> lifecycleHooks) {
            new InMemoryMetadataAwareObjectStorageOperations(delegate, metadataOperations, descriptor, tenantResolver, lifecycleHooks)
        }
    }

    static class InMemoryMetadataOperations implements ObjectStorageMetadataOperations {
        private final TenantResolver tenantResolver
        private final StorageDescriptor descriptor
        private final Map<String, StorageContainerMetadata> containersByTenant = new ConcurrentHashMap<>()
        private final Map<String, Map<String, StorageObjectMetadata>> objectsByTenant = new ConcurrentHashMap<>()

        InMemoryMetadataOperations(TenantResolver tenantResolver, StorageDescriptor descriptor) {
            this.tenantResolver = tenantResolver
            this.descriptor = descriptor
        }

        void markUploadVisible(String tenantId, UploadRequest request, UploadResponse<?> response) {
            Instant now = Instant.now()
            containersByTenant.computeIfAbsent(tenantId) {
                new StorageContainerMetadata(tenantId, descriptor, now, now)
            }
            StorageObjectMetadata metadata = new StorageObjectMetadata(
                tenantId,
                descriptor,
                request.key,
                null,
                request.contentType.orElse(null),
                request.contentSize.orElse(null),
                response.ETag,
                null,
                null,
                now,
                now,
                new ObjectMetadataSyncStatus(ObjectMetadataReconciliationState.SUCCEEDED, null)
            )
            objectsByTenant.computeIfAbsent(tenantId) { new ConcurrentHashMap<>() }
                .put(request.key, metadata)
        }

        @Override
        TenantResolver getTenantResolver() {
            tenantResolver
        }

        @Override
        Optional<StorageContainerMetadata> findContainer(StorageDescriptor storageDescriptor) {
            if (storageDescriptor.storageName != descriptor.storageName || storageDescriptor.logicalContainer != descriptor.logicalContainer) {
                return Optional.empty()
            }
            Optional.ofNullable(containersByTenant.get(getActiveTenantId()))
        }

        @Override
        Optional<StorageObjectMetadata> findObject(StorageDescriptor storageDescriptor, String objectKey) {
            if (storageDescriptor.storageName != descriptor.storageName || storageDescriptor.logicalContainer != descriptor.logicalContainer) {
                return Optional.empty()
            }
            Optional.ofNullable(objectsByTenant.getOrDefault(getActiveTenantId(), [:]).get(objectKey))
        }

        @Override
        List<StorageObjectMetadata> listObjects(StorageObjectMetadataQuery query) {
            objectsByTenant.getOrDefault(getActiveTenantId(), [:])
                .values()
                .findAll { StorageObjectMetadata metadata ->
                    boolean storageMatches = query.storageName.map { it == metadata.storageName }.orElse(true)
                    boolean containerMatches = query.logicalContainer.map { it == metadata.logicalContainer }.orElse(true)
                    boolean prefixMatches = query.objectKeyPrefix.map { metadata.objectKey.startsWith(it) }.orElse(true)
                    storageMatches && containerMatches && prefixMatches
                }
                .sort { a, b -> a.objectKey <=> b.objectKey }
        }
    }

    static class InMemoryMetadataAwareObjectStorageOperations implements MetadataAwareObjectStorageOperations<Object, Object, Object> {
        private final AwsS3Operations delegate
        private final InMemoryMetadataOperations metadataOperations
        private final StorageDescriptor descriptor
        private final TenantResolver tenantResolver
        private final Collection<ObjectStorageLifecycleHook> lifecycleHooks
        final AtomicInteger uploadCount = new AtomicInteger()

        InMemoryMetadataAwareObjectStorageOperations(AwsS3Operations delegate,
                                                     InMemoryMetadataOperations metadataOperations,
                                                     StorageDescriptor descriptor,
                                                     TenantResolver tenantResolver,
                                                     Collection<ObjectStorageLifecycleHook> lifecycleHooks) {
            this.delegate = delegate
            this.metadataOperations = metadataOperations
            this.descriptor = descriptor
            this.tenantResolver = tenantResolver
            this.lifecycleHooks = lifecycleHooks
        }

        @Override
        ObjectStorageMetadataOperations getMetadataOperations() {
            metadataOperations
        }

        @Override
        Collection<ObjectStorageLifecycleHook> getLifecycleHooks() {
            lifecycleHooks
        }

        @Override
        UploadResponse<Object> upload(UploadRequest request) {
            String tenantId = tenantResolver.resolveTenantId()
            ObjectStorageOperationContext context = ObjectStorageOperationContext.forUpload(tenantId, descriptor, request)
            for (ObjectStorageLifecycleHook hook : getOrderedLifecycleHooks()) {
                hook.before(context)
            }
            try {
                UploadResponse<Object> response = delegate.upload(request)
                metadataOperations.markUploadVisible(tenantId, request, response)
                ObjectStorageOperationOutcome outcome = ObjectStorageOperationOutcome.uploadSuccess(context, response, "example-rec-${uploadCount.incrementAndGet()}")
                for (ObjectStorageLifecycleHook hook : getOrderedLifecycleHooks()) {
                    hook.after(context, outcome)
                }
                return response
            } catch (RuntimeException e) {
                for (ObjectStorageLifecycleHook hook : getOrderedLifecycleHooks()) {
                    hook.error(context, e)
                }
                throw e
            }
        }

        @Override
        UploadResponse<Object> upload(UploadRequest request, java.util.function.Consumer<Object> requestConsumer) {
            String tenantId = tenantResolver.resolveTenantId()
            ObjectStorageOperationContext context = ObjectStorageOperationContext.forUpload(tenantId, descriptor, request)
            for (ObjectStorageLifecycleHook hook : getOrderedLifecycleHooks()) {
                hook.before(context)
            }
            try {
                UploadResponse<Object> response = delegate.upload(request, requestConsumer)
                metadataOperations.markUploadVisible(tenantId, request, response)
                ObjectStorageOperationOutcome outcome = ObjectStorageOperationOutcome.uploadSuccess(context, response, "example-rec-${uploadCount.incrementAndGet()}")
                for (ObjectStorageLifecycleHook hook : getOrderedLifecycleHooks()) {
                    hook.after(context, outcome)
                }
                return response
            } catch (RuntimeException e) {
                for (ObjectStorageLifecycleHook hook : getOrderedLifecycleHooks()) {
                    hook.error(context, e)
                }
                throw e
            }
        }

        @Override
        Optional retrieve(String key) {
            delegate.retrieve(key)
        }

        @Override
        Object delete(String key) {
            delegate.delete(key)
        }
    }

    record Event(String phase,
                 String storageName,
                 String objectKey,
                 boolean containerMetadataPresent,
                 ObjectStorageOperationType operationType,
                 boolean reconciliationIdPresent) {
    }
}
