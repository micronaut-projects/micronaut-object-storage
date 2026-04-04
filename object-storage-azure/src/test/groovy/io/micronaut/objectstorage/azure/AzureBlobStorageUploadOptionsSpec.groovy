package io.micronaut.objectstorage.azure

import com.azure.storage.blob.models.AccessTier
import com.azure.storage.blob.models.BlobHttpHeaders
import com.azure.storage.blob.models.BlobImmutabilityPolicy
import com.azure.storage.blob.models.BlobImmutabilityPolicyMode
import com.azure.storage.blob.models.BlobRequestConditions
import com.azure.storage.blob.options.BlobParallelUploadOptions
import com.azure.storage.blob.options.BlockBlobSimpleUploadOptions
import spock.lang.Specification

import java.time.OffsetDateTime

class AzureBlobStorageUploadOptionsSpec extends Specification {

    void "simple upload conversion preserves explicit azure upload options by value"() {
        given:
        def requestConditions = new BlobRequestConditions()
            .setIfMatch('"etag"')
            .setTagsConditions("\"type\"='invoice'")
        def headers = new BlobHttpHeaders().setContentType("application/pdf")
        def immutabilityPolicy = new BlobImmutabilityPolicy()
            .setExpiryTime(OffsetDateTime.parse("2026-04-02T00:00:00Z"))
            .setPolicyMode(BlobImmutabilityPolicyMode.UNLOCKED)
        def parallelUploadOptions = new BlobParallelUploadOptions(new ByteArrayInputStream("test".bytes))
            .setHeaders(headers)
            .setMetadata([tenant: "cliponaut"])
            .setTags([type: "invoice", issued: "2026-04-02"])
            .setTier(AccessTier.COOL)
            .setRequestConditions(requestConditions)
            .setImmutabilityPolicy(immutabilityPolicy)
            .setLegalHold(true)

        when:
        BlockBlobSimpleUploadOptions simpleUploadOptions = convertToSimpleUploadOptions(parallelUploadOptions, 4L)

        then:
        simpleUploadOptions.headers.contentType == "application/pdf"
        simpleUploadOptions.metadata == [tenant: "cliponaut"]
        simpleUploadOptions.tags == [type: "invoice", issued: "2026-04-02"]
        simpleUploadOptions.tier == AccessTier.COOL
        simpleUploadOptions.requestConditions.ifMatch == '"etag"'
        simpleUploadOptions.requestConditions.tagsConditions == "\"type\"='invoice'"
        simpleUploadOptions.immutabilityPolicy.expiryTime == OffsetDateTime.parse("2026-04-02T00:00:00Z")
        simpleUploadOptions.immutabilityPolicy.policyMode == BlobImmutabilityPolicyMode.UNLOCKED
        simpleUploadOptions.legalHold == true
    }

    void "simple upload conversion removes default wildcard while preserving explicit conditions"() {
        given:
        def parallelUploadOptions = new BlobParallelUploadOptions(new ByteArrayInputStream("test".bytes))
            .setRequestConditions(new BlobRequestConditions()
                .setIfNoneMatch("*")
                .setTagsConditions("\"type\"='invoice'"))

        when:
        BlockBlobSimpleUploadOptions simpleUploadOptions = convertToSimpleUploadOptions(parallelUploadOptions, 4L)

        then:
        simpleUploadOptions.requestConditions.ifNoneMatch == null
        simpleUploadOptions.requestConditions.tagsConditions == "\"type\"='invoice'"
    }

    void "simple upload conversion omits wildcard-only overwrite guard"() {
        given:
        def parallelUploadOptions = new BlobParallelUploadOptions(new ByteArrayInputStream("test".bytes))
            .setRequestConditions(new BlobRequestConditions().setIfNoneMatch("*"))

        when:
        BlockBlobSimpleUploadOptions simpleUploadOptions = convertToSimpleUploadOptions(parallelUploadOptions, 4L)

        then:
        simpleUploadOptions.requestConditions == null
    }

    private static BlockBlobSimpleUploadOptions convertToSimpleUploadOptions(BlobParallelUploadOptions options, long length) {
        def method = AzureBlobStorageOperations.getDeclaredMethod(
            "toBlockBlobSimpleUploadOptions",
            BlobParallelUploadOptions,
            InputStream,
            long
        )
        assert method.trySetAccessible()
        (BlockBlobSimpleUploadOptions) method.invoke(new AzureBlobStorageOperations(null, null), options, options.dataStream, length)
    }
}
