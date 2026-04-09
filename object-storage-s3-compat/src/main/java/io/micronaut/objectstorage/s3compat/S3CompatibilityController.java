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
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Head;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.objectstorage.request.ListObjectsRequest;

import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;

/**
 * Minimal path-style S3-compatible controller backed by configured object-storage beans.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.0.0
 */
@Controller
public final class S3CompatibilityController {

    private static final DateTimeFormatter RFC_1123 = DateTimeFormatter.RFC_1123_DATE_TIME;

    private final S3CompatibilityBucketResolver bucketResolver;

    public S3CompatibilityController(S3CompatibilityBucketResolver bucketResolver) {
        this.bucketResolver = bucketResolver;
    }

    @Put(uri = "/{bucket}/{+key}")
    public HttpResponse<?> putObject(@PathVariable String bucket,
                                     @PathVariable String key,
                                     HttpRequest<?> request,
                                     @Body InputStream body) {
        var resolved = bucketResolver.resolve(bucket);
        if (resolved.isEmpty()) {
            return noSuchBucket(bucket);
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

    @Get(uri = "/{bucket}/{+key}")
    public HttpResponse<?> getObject(@PathVariable String bucket,
                                     @PathVariable String key) {
        var resolved = bucketResolver.resolve(bucket);
        if (resolved.isEmpty()) {
            return noSuchBucket(bucket);
        }
        return resolved.get().operations().getObject(key)
            .<HttpResponse<?>>map(this::ok)
            .orElseGet(() -> noSuchKey(bucket, key));
    }

    @Head(uri = "/{bucket}/{+key}")
    public HttpResponse<?> headObject(@PathVariable String bucket,
                                      @PathVariable String key) {
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
                                        @PathVariable String key) {
        var resolved = bucketResolver.resolve(bucket);
        if (resolved.isEmpty()) {
            return noSuchBucket(bucket);
        }
        resolved.get().operations().deleteObject(key);
        return HttpResponse.noContent();
    }

    @Get(uri = "/{bucket}", produces = MediaType.APPLICATION_XML)
    public HttpResponse<String> listObjectsV2(@PathVariable String bucket,
                                              @QueryValue("list-type") int listType,
                                              @Nullable @QueryValue String prefix,
                                              @Nullable @QueryValue("continuation-token") String continuationToken,
                                              @QueryValue(value = "max-keys", defaultValue = "1000") int maxKeys) {
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
            io.micronaut.http.HttpStatus.NOT_FOUND,
            "NoSuchBucket",
            "The specified bucket does not exist",
            bucket,
            null
        );
    }

    private HttpResponse<String> noSuchKey(String bucket, String key) {
        return error(
            io.micronaut.http.HttpStatus.NOT_FOUND,
            "NoSuchKey",
            "The specified key does not exist",
            bucket,
            key
        );
    }

    private HttpResponse<String> error(io.micronaut.http.HttpStatus status,
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
