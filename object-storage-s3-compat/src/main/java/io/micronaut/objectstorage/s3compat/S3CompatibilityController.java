/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.objectstorage.s3compat;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Head;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.objectstorage.request.ListObjectsRequest;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

import java.io.InputStream;
import java.io.StringReader;
import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.xml.sax.InputSource;

/**
 * Minimal path-style S3-compatible controller backed by configured object-storage beans.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.0.0
 */
@Controller
@ExecuteOn(TaskExecutors.BLOCKING)
public final class S3CompatibilityController {

    private static final DateTimeFormatter RFC_1123 = DateTimeFormatter.RFC_1123_DATE_TIME;

    private final S3CompatibilityBucketResolver bucketResolver;
    private final S3CompatibilityRequestValidator requestValidator;

    public S3CompatibilityController(S3CompatibilityBucketResolver bucketResolver,
                                     S3CompatibilityRequestValidator requestValidator) {
        this.bucketResolver = bucketResolver;
        this.requestValidator = requestValidator;
    }

    @Put(uri = "/{bucket}/{+key}", consumes = MediaType.ALL)
    public HttpResponse<?> putObject(@PathVariable String bucket,
                                     @PathVariable String key,
                                     @Nullable @QueryValue Integer partNumber,
                                     @Nullable @QueryValue String uploadId,
                                     HttpRequest<?> request,
                                     @Body InputStream body) {
        Optional<HttpResponse<String>> validation = requestValidator.validate(request);
        if (validation.isPresent()) {
            return validation.get();
        }
        var resolved = bucketResolver.resolve(bucket);
        if (resolved.isEmpty()) {
            return noSuchBucket(bucket);
        }
        if (uploadId != null || partNumber != null) {
            if (uploadId == null || partNumber == null) {
                return error(HttpStatus.BAD_REQUEST, "InvalidRequest", "Multipart uploads require both uploadId and partNumber", bucket, key);
            }
            if (!resolved.get().operations().supportsMultipart()) {
                return multipartNotImplemented(bucket, key);
            }
            Long contentLength = request.getHeaders().contentLength().isPresent()
                ? request.getHeaders().contentLength().getAsLong()
                : null;
            try {
                S3MultipartPart part = resolved.get().operations().uploadPart(key, uploadId, partNumber, body, contentLength);
                return HttpResponse.ok().header(HttpHeaders.ETAG, part.eTag());
            } catch (S3CompatibilityException e) {
                return error(e.getStatus(), e.getCode(), e.getMessage(), bucket, key);
            }
        }
        Long contentLength = request.getHeaders().contentLength().isPresent()
            ? request.getHeaders().contentLength().getAsLong()
            : null;
        var response = resolved.get().operations().putObject(
            key,
            body,
            contentLength,
            request.getContentType().map(MediaType::toString).orElse(null)
        );
        return HttpResponse.ok().header(HttpHeaders.ETAG, response.getETag());
    }

    @Get(uri = "/{bucket}/{+key}", headRoute = false)
    public HttpResponse<?> getObject(@PathVariable String bucket,
                                     @PathVariable String key,
                                     @Nullable @QueryValue("uploadId") String uploadId,
                                     @Nullable @QueryValue("part-number-marker") Integer partNumberMarker,
                                     @QueryValue(value = "max-parts", defaultValue = "1000") int maxParts,
                                     HttpRequest<?> request) {
        Optional<HttpResponse<String>> validation = requestValidator.validate(request);
        if (validation.isPresent()) {
            return validation.get();
        }
        var resolved = bucketResolver.resolve(bucket);
        if (resolved.isEmpty()) {
            return noSuchBucket(bucket);
        }
        if (uploadId != null) {
            if (!resolved.get().operations().supportsMultipart()) {
                return multipartNotImplemented(bucket, key);
            }
            try {
                S3MultipartListPartsResponse response = resolved.get().operations().listParts(key, uploadId, partNumberMarker, maxParts);
                return HttpResponse.ok(renderListParts(bucket, key, uploadId, partNumberMarker, maxParts, response))
                    .contentType(MediaType.APPLICATION_XML_TYPE);
            } catch (S3CompatibilityException e) {
                return error(e.getStatus(), e.getCode(), e.getMessage(), bucket, key);
            }
        }
        return resolved.get().operations().getObject(key)
            .<HttpResponse<?>>map(this::ok)
            .orElseGet(() -> noSuchKey(bucket, key));
    }

    @Head(uri = "/{bucket}/{+key}")
    public HttpResponse<?> headObject(@PathVariable String bucket,
                                      @PathVariable String key,
                                      HttpRequest<?> request) {
        Optional<HttpResponse<String>> validation = requestValidator.validate(request);
        if (validation.isPresent()) {
            return validation.get();
        }
        var resolved = bucketResolver.resolve(bucket);
        if (resolved.isEmpty()) {
            return noSuchBucket(bucket);
        }
        return resolved.get().operations().getObject(key)
            .<HttpResponse<?>>map(this::head)
            .orElseGet(() -> noSuchKey(bucket, key));
    }

    @Delete(uri = "/{bucket}/{+key}")
    public HttpResponse<?> deleteObject(@PathVariable String bucket,
                                        @PathVariable String key,
                                        @Nullable @QueryValue String uploadId,
                                        HttpRequest<?> request) {
        Optional<HttpResponse<String>> validation = requestValidator.validate(request);
        if (validation.isPresent()) {
            return validation.get();
        }
        var resolved = bucketResolver.resolve(bucket);
        if (resolved.isEmpty()) {
            return noSuchBucket(bucket);
        }
        if (uploadId != null) {
            if (!resolved.get().operations().supportsMultipart()) {
                return multipartNotImplemented(bucket, key);
            }
            try {
                resolved.get().operations().abortMultipartUpload(key, uploadId);
                return HttpResponse.noContent();
            } catch (S3CompatibilityException e) {
                return error(e.getStatus(), e.getCode(), e.getMessage(), bucket, key);
            }
        }
        resolved.get().operations().deleteObject(key);
        return HttpResponse.noContent();
    }

    @Post(uri = "/{bucket}/{+key}", consumes = MediaType.ALL, produces = MediaType.APPLICATION_XML)
    public HttpResponse<String> postObject(@PathVariable String bucket,
                                           @PathVariable String key,
                                           @Nullable @QueryValue String uploadId,
                                           HttpRequest<?> request,
                                           @Nullable @Body String body) {
        Optional<HttpResponse<String>> validation = requestValidator.validate(request);
        if (validation.isPresent()) {
            return validation.get();
        }
        var resolved = bucketResolver.resolve(bucket);
        if (resolved.isEmpty()) {
            return noSuchBucket(bucket);
        }
        boolean initiateMultipart = hasQueryParameter(request, "uploads");
        if (!initiateMultipart && uploadId == null) {
            return error(HttpStatus.BAD_REQUEST, "InvalidRequest", "Unsupported POST request for this S3-compatible endpoint", bucket, key);
        }
        if (!resolved.get().operations().supportsMultipart()) {
            return multipartNotImplemented(bucket, key);
        }
        try {
            if (initiateMultipart) {
                S3MultipartUpload response = resolved.get().operations()
                    .createMultipartUpload(key, request.getContentType().map(MediaType::toString).orElse(null));
                return HttpResponse.ok(renderCreateMultipartUpload(bucket, key, response))
                    .contentType(MediaType.APPLICATION_XML_TYPE);
            }
            List<S3CompletedPart> completedParts = parseCompletedParts(body);
            if (completedParts.isEmpty()) {
                return error(HttpStatus.BAD_REQUEST, "MalformedXML", "The CompleteMultipartUpload request body must contain at least one part", bucket, key);
            }
            S3MultipartCompletedUpload response = resolved.get().operations().completeMultipartUpload(key, uploadId, completedParts);
            return HttpResponse.ok(renderCompleteMultipartUpload(bucket, key, response))
                .contentType(MediaType.APPLICATION_XML_TYPE);
        } catch (S3CompatibilityException e) {
            return error(e.getStatus(), e.getCode(), e.getMessage(), bucket, key);
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.BAD_REQUEST, "MalformedXML", e.getMessage(), bucket, key);
        }
    }

    @Get(uri = "/{bucket}", produces = MediaType.APPLICATION_XML, headRoute = false)
    public HttpResponse<String> listObjectsV2(@PathVariable String bucket,
                                              @QueryValue("list-type") int listType,
                                              @Nullable @QueryValue String prefix,
                                              @Nullable @QueryValue("continuation-token") String continuationToken,
                                              @QueryValue(value = "max-keys", defaultValue = "1000") int maxKeys,
                                              HttpRequest<?> request) {
        Optional<HttpResponse<String>> validation = requestValidator.validate(request);
        if (validation.isPresent()) {
            return validation.get();
        }
        if (listType != 2) {
            return error(
                io.micronaut.http.HttpStatus.BAD_REQUEST,
                "InvalidRequest",
                "This endpoint only supports list-type=2",
                bucket,
                null
            );
        }
        var resolved = bucketResolver.resolve(bucket);
        if (resolved.isEmpty()) {
            return noSuchBucket(bucket);
        }
        try {
            S3ListResponse response = resolved.get().operations()
                .listObjects(new ListObjectsRequest(maxKeys, prefix, continuationToken));
            return HttpResponse.ok(renderListObjectsV2(bucket, prefix, maxKeys, continuationToken, response))
                .contentType(MediaType.APPLICATION_XML_TYPE);
        } catch (IllegalArgumentException e) {
            return error(io.micronaut.http.HttpStatus.BAD_REQUEST, "InvalidArgument", e.getMessage(), bucket, null);
        }
    }

    private MutableHttpResponse<?> ok(S3Object object) {
        MutableHttpResponse<?> response = HttpResponse.ok(object.entry().toStreamedFile());
        return applyObjectHeaders(response, object);
    }

    private MutableHttpResponse<?> head(S3Object object) {
        return applyObjectHeaders(HttpResponse.ok(), object);
    }

    private MutableHttpResponse<?> applyObjectHeaders(MutableHttpResponse<?> response, S3Object object) {
        object.entry().getContentType().ifPresent(contentType -> response.header(HttpHeaders.CONTENT_TYPE, contentType));
        object.getSize().ifPresent(size -> response.header(HttpHeaders.CONTENT_LENGTH, Long.toString(size)));
        object.getLastModified().ifPresent(lastModified -> response.header(HttpHeaders.LAST_MODIFIED, RFC_1123.format(lastModified.atOffset(ZoneOffset.UTC))));
        return response;
    }

    private String renderListObjectsV2(String bucket,
                                       @Nullable String prefix,
                                       int maxKeys,
                                       @Nullable String continuationToken,
                                       S3ListResponse response) {
        StringBuilder xml = new StringBuilder(256);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        xml.append("<ListBucketResult xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">");
        xml.append("<Name>").append(escapeXml(bucket)).append("</Name>");
        xml.append("<Prefix>").append(escapeXml(prefix == null ? "" : prefix)).append("</Prefix>");
        xml.append("<MaxKeys>").append(maxKeys).append("</MaxKeys>");
        xml.append("<KeyCount>").append(response.objects().size()).append("</KeyCount>");
        xml.append("<IsTruncated>").append(response.getContinuationToken().isPresent()).append("</IsTruncated>");
        if (continuationToken != null && !continuationToken.isEmpty()) {
            xml.append("<ContinuationToken>").append(escapeXml(continuationToken)).append("</ContinuationToken>");
        }
        response.getContinuationToken().ifPresent(next -> xml.append("<NextContinuationToken>").append(escapeXml(next)).append("</NextContinuationToken>"));
        for (S3ObjectSummary object : response.objects()) {
            xml.append("<Contents>");
            xml.append("<Key>").append(escapeXml(object.key())).append("</Key>");
            object.getLastModified().ifPresent(lastModified -> xml.append("<LastModified>").append(lastModified).append("</LastModified>"));
            xml.append("<Size>").append(object.getSize().orElse(0L)).append("</Size>");
            xml.append("</Contents>");
        }
        xml.append("</ListBucketResult>");
        return xml.toString();
    }

    private String renderCreateMultipartUpload(String bucket, String key, S3MultipartUpload response) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<InitiateMultipartUploadResult xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">"
            + "<Bucket>" + escapeXml(bucket) + "</Bucket>"
            + "<Key>" + escapeXml(key) + "</Key>"
            + "<UploadId>" + escapeXml(response.uploadId()) + "</UploadId>"
            + "</InitiateMultipartUploadResult>";
    }

    private String renderCompleteMultipartUpload(String bucket, String key, S3MultipartCompletedUpload response) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<CompleteMultipartUploadResult xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">"
            + "<Location>" + escapeXml('/' + bucket + '/' + key) + "</Location>"
            + "<Bucket>" + escapeXml(bucket) + "</Bucket>"
            + "<Key>" + escapeXml(key) + "</Key>"
            + "<ETag>" + escapeXml(response.eTag()) + "</ETag>"
            + "</CompleteMultipartUploadResult>";
    }

    private String renderListParts(String bucket,
                                   String key,
                                   String uploadId,
                                   @Nullable Integer partNumberMarker,
                                   int maxParts,
                                   S3MultipartListPartsResponse response) {
        StringBuilder xml = new StringBuilder(256);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        xml.append("<ListPartsResult xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">");
        xml.append("<Bucket>").append(escapeXml(bucket)).append("</Bucket>");
        xml.append("<Key>").append(escapeXml(key)).append("</Key>");
        xml.append("<UploadId>").append(escapeXml(uploadId)).append("</UploadId>");
        xml.append("<PartNumberMarker>").append(partNumberMarker == null ? 0 : partNumberMarker).append("</PartNumberMarker>");
        xml.append("<MaxParts>").append(maxParts).append("</MaxParts>");
        xml.append("<IsTruncated>").append(response.truncated()).append("</IsTruncated>");
        response.getNextPartNumberMarker()
            .ifPresent(next -> xml.append("<NextPartNumberMarker>").append(next).append("</NextPartNumberMarker>"));
        for (S3MultipartPart part : response.parts()) {
            xml.append("<Part>");
            xml.append("<PartNumber>").append(part.partNumber()).append("</PartNumber>");
            xml.append("<ETag>").append(escapeXml(part.eTag())).append("</ETag>");
            part.getLastModified().ifPresent(lastModified -> xml.append("<LastModified>").append(lastModified).append("</LastModified>"));
            xml.append("<Size>").append(part.getSize().orElse(0L)).append("</Size>");
            xml.append("</Part>");
        }
        xml.append("</ListPartsResult>");
        return xml.toString();
    }

    private HttpResponse<String> multipartNotImplemented(String bucket, String key) {
        return error(
            HttpStatus.NOT_IMPLEMENTED,
            "NotImplemented",
            "Multipart uploads are only supported for S3-compatible buckets backed by AWS storage",
            bucket,
            key
        );
    }

    private static boolean hasQueryParameter(HttpRequest<?> request, String name) {
        String rawQuery = request.getUri().getRawQuery();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return false;
        }
        for (String token : rawQuery.split("&")) {
            String queryName = token;
            int equalsAt = token.indexOf('=');
            if (equalsAt >= 0) {
                queryName = token.substring(0, equalsAt);
            }
            if (name.equals(queryName)) {
                return true;
            }
        }
        return false;
    }

    private static List<S3CompletedPart> parseCompletedParts(String body) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setNamespaceAware(true);
            var builder = factory.newDocumentBuilder();
            var document = builder.parse(new InputSource(new StringReader(body)));
            var partNodes = document.getElementsByTagNameNS("*", "Part");
            List<S3CompletedPart> parts = new ArrayList<>(partNodes.getLength());
            for (int i = 0; i < partNodes.getLength(); i++) {
                var element = partNodes.item(i);
                String partNumber = childText(element, "PartNumber");
                String eTag = childText(element, "ETag");
                if (partNumber == null || eTag == null || partNumber.isBlank() || eTag.isBlank()) {
                    throw new IllegalArgumentException("Each multipart completion part must include PartNumber and ETag elements");
                }
                parts.add(new S3CompletedPart(Integer.parseInt(partNumber.trim()), eTag.trim()));
            }
            return parts;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Multipart completion PartNumber values must be integers", e);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to parse the CompleteMultipartUpload request body", e);
        }
    }

    @Nullable
    private static String childText(org.w3c.dom.Node node, String localName) {
        var children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            var child = children.item(i);
            if (localName.equals(child.getLocalName())) {
                return child.getTextContent();
            }
        }
        return null;
    }

    private static String escapeXml(String value) {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;");
    }

    private HttpResponse<String> noSuchBucket(String bucket) {
        return error(
            HttpStatus.NOT_FOUND,
            "NoSuchBucket",
            "The specified bucket does not exist",
            bucket,
            null
        );
    }

    private HttpResponse<String> noSuchKey(String bucket, String key) {
        return error(
            HttpStatus.NOT_FOUND,
            "NoSuchKey",
            "The specified key does not exist",
            bucket,
            key
        );
    }

    private HttpResponse<String> error(HttpStatus status,
                                       String code,
                                       String message,
                                       @Nullable String bucket,
                                       @Nullable String key) {
        StringBuilder xml = new StringBuilder(192);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        xml.append("<Error>");
        xml.append("<Code>").append(escapeXml(code)).append("</Code>");
        xml.append("<Message>").append(escapeXml(message)).append("</Message>");
        if (bucket != null && !bucket.isEmpty()) {
            xml.append("<BucketName>").append(escapeXml(bucket)).append("</BucketName>");
        }
        if (key != null && !key.isEmpty()) {
            xml.append("<Key>").append(escapeXml(key)).append("</Key>");
            xml.append("<Resource>").append(escapeXml('/' + bucket + '/' + key)).append("</Resource>");
        } else if (bucket != null && !bucket.isEmpty()) {
            xml.append("<Resource>").append(escapeXml('/' + bucket)).append("</Resource>");
        }
        xml.append("</Error>");
        return HttpResponse.status(status)
            .contentType(MediaType.APPLICATION_XML_TYPE)
            .body(xml.toString());
    }
}
