package io.micronaut.objectstorage.metadata

import io.micronaut.objectstorage.request.UploadRequest
import io.micronaut.objectstorage.response.UploadResponse
import spock.lang.Specification

class ObjectStorageLifecycleHookOrderingSpec extends Specification {

    void "ordered hooks execute deterministically for before after and error phases"() {
        given:
        StorageDescriptor descriptor = new StorageDescriptor("pictures", "aws-s3", "profiles", "ns-a", "bucket-a")
        List<String> events = []
        List<ObjectStorageLifecycleHook> ordered = ObjectStorageLifecycleHook.ordered([
            new RecordingHook("late", 100, events),
            new RecordingHook("early", -10, events),
            new RecordingHook("middle", 5, events)
        ])
        List<ObjectStorageOperationContext> contexts = [
            ObjectStorageOperationContext.forUpload("tenant-a", descriptor, UploadRequest.fromBytes("abc".bytes, "/profiles/avatar.png", "image/png")),
            ObjectStorageOperationContext.forCopy("tenant-a", descriptor, "/profiles/avatar.png", "/profiles/avatar-copy.png"),
            ObjectStorageOperationContext.forDelete("tenant-a", descriptor, "/profiles/avatar.png")
        ]

        when:
        contexts.each { context ->
            ObjectStorageOperationOutcome outcome = outcomeFor(context)
            ordered.each { it.before(context) }
            ordered.each { it.after(context, outcome) }
            ordered.each { it.error(context, new IllegalStateException("boom-${context.operationType}")) }
        }

        then:
        ordered*.name == ["early", "middle", "late"]

        and:
        events == contexts.collectMany { context ->
            ["before", "after", "error"].collectMany { phase ->
                ordered*.name.collect { name -> "$phase:${context.operationType}:$name" }
            }
        }
    }

    private static ObjectStorageOperationOutcome outcomeFor(ObjectStorageOperationContext context) {
        if (context.operationType == ObjectStorageOperationType.UPLOAD) {
            return ObjectStorageOperationOutcome.uploadSuccess(
                context,
                UploadResponse.of(context.objectKey, "etag-1", "native-upload"),
                "rec-upload"
            )
        }
        if (context.operationType == ObjectStorageOperationType.COPY) {
            return ObjectStorageOperationOutcome.copySuccess(context, "native-copy", "rec-copy")
        }
        return ObjectStorageOperationOutcome.deleteSuccess(context, "native-delete", "rec-delete")
    }

    private static final class RecordingHook implements ObjectStorageLifecycleHook {
        final String name
        final int order
        final List<String> events

        RecordingHook(String name, int order, List<String> events) {
            this.name = name
            this.order = order
            this.events = events
        }

        @Override
        int getOrder() {
            return order
        }

        @Override
        void before(ObjectStorageOperationContext context) {
            events << "before:${context.operationType}:$name"
        }

        @Override
        void after(ObjectStorageOperationContext context, ObjectStorageOperationOutcome outcome) {
            events << "after:${context.operationType}:$name"
        }

        @Override
        void error(ObjectStorageOperationContext context, Throwable throwable) {
            events << "error:${context.operationType}:$name"
        }
    }
}
