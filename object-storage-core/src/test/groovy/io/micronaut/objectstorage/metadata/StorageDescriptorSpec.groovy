package io.micronaut.objectstorage.metadata

import spock.lang.Specification

class StorageDescriptorSpec extends Specification {

    void "it keeps provider container identity deterministic"() {
        when:
        StorageDescriptor descriptor = new StorageDescriptor("pictures", "aws-s3", "profiles", "team-a", "bucket-one")

        then:
        descriptor.storageName == "pictures"
        descriptor.providerId == "aws-s3"
        descriptor.logicalContainer == "profiles"
        descriptor.providerNamespace.present
        descriptor.providerNamespace.get() == "team-a"
        descriptor.providerContainer == "bucket-one"
        descriptor.providerContainerIdentity == "team-a/bucket-one"
    }

    void "it supports provider container identity without namespace"() {
        when:
        StorageDescriptor descriptor = new StorageDescriptor("logos", "local", "assets", null, "fs-root")

        then:
        descriptor.providerNamespace.empty
        descriptor.providerContainerIdentity == "fs-root"
    }
}
