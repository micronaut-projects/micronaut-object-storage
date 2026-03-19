package io.micronaut.objectstorage.metadata

import spock.lang.Specification

import java.time.Instant

class StorageObjectMetadataSpec extends Specification {

    private static final Instant NOW = Instant.parse("2026-03-17T10:15:30Z")

    void "it computes deterministic object-key hashing when missing"() {
        given:
        StorageDescriptor descriptor = new StorageDescriptor("pictures", "aws-s3", "profiles", "tenant-a", "bucket-a")
        ObjectMetadataSyncStatus syncStatus = new ObjectMetadataSyncStatus(ObjectMetadataReconciliationState.PENDING, null)

        when:
        StorageObjectMetadata first = new StorageObjectMetadata(
            "tenant-a",
            descriptor,
            "/2026/03/avatar.png",
            null,
            "image/png",
            42L,
            "etag-1",
            "v1",
            "sha256:abc",
            NOW,
            NOW,
            syncStatus
        )
        StorageObjectMetadata second = new StorageObjectMetadata(
            "tenant-a",
            descriptor,
            "/2026/03/avatar.png",
            "",
            "image/png",
            42L,
            "etag-1",
            "v1",
            "sha256:abc",
            NOW,
            NOW,
            syncStatus
        )

        then:
        first.objectKeyHash == second.objectKeyHash
        first.objectKeyHash == StorageObjectMetadata.deterministicObjectKeyHash("/2026/03/avatar.png")
        first.objectKeyHash != StorageObjectMetadata.deterministicObjectKeyHash("/2026/03/avatar-2.png")
    }

    void "it preserves explicit hash and exposes null-safe optionals"() {
        given:
        StorageDescriptor descriptor = new StorageDescriptor("logos", "local", "assets", null, "fs-root")
        ObjectMetadataSyncStatus syncStatus = new ObjectMetadataSyncStatus(ObjectMetadataReconciliationState.FAILED, "provider timeout")

        when:
        StorageObjectMetadata metadata = new StorageObjectMetadata(
            "tenant-b",
            descriptor,
            "/assets/logo.svg",
            "fixed-hash",
            null,
            null,
            null,
            null,
            null,
            NOW,
            NOW,
            syncStatus
        )

        then:
        metadata.tenantId == "tenant-b"
        metadata.storageName == "logos"
        metadata.providerId == "local"
        metadata.logicalContainer == "assets"
        metadata.providerNamespace.empty
        metadata.providerContainer == "fs-root"
        metadata.providerContainerIdentity == "fs-root"
        metadata.objectKey == "/assets/logo.svg"
        metadata.objectKeyHash == "fixed-hash"
        metadata.contentType.empty
        metadata.contentLength.empty
        metadata.etag.empty
        metadata.providerVersionId.empty
        metadata.checksum.empty
        metadata.reconciliationStatus == ObjectMetadataReconciliationState.FAILED
        metadata.lastErrorSummary.present
        metadata.lastErrorSummary.get() == "provider timeout"
    }

    void "it freezes v1 persisted field contract and state values"() {
        expect:
        StorageObjectMetadataField.persistedFields() == [
            "tenantId",
            "storageName",
            "providerId",
            "logicalContainer",
            "providerNamespace",
            "providerContainer",
            "providerContainerIdentity",
            "objectKey",
            "objectKeyHash",
            "contentType",
            "contentLength",
            "etag",
            "providerVersionId",
            "checksum",
            "createdAt",
            "updatedAt",
            "reconciliationStatus",
            "lastErrorSummary"
        ] as Set

        and:
        ObjectStorageOperationType.values()*.name() == ["UPLOAD", "COPY", "DELETE"]

        and:
        ObjectMetadataReconciliationState.values()*.name() == ["PENDING", "PROCESSING", "SUCCEEDED", "FAILED", "DEAD_LETTER"]
    }
}
