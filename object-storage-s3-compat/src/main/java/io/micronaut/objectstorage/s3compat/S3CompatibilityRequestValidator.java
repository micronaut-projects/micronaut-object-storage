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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import jakarta.inject.Singleton;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Validates incoming requests for the S3-compatible HTTP surface.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.0.0
 */
@Singleton
@Internal
public final class S3CompatibilityRequestValidator {

    private static final String ALGORITHM = "AWS4-HMAC-SHA256";
    private static final String SERVICE = "s3";
    private static final String TERMINAL = "aws4_request";
    private static final String HMAC_SHA_256 = "HmacSHA256";
    private static final String UNSIGNED_PAYLOAD = "UNSIGNED-PAYLOAD";
    private static final String EMPTY_PAYLOAD_SHA_256 = "e3b0c44298fc1c149afbf4c8996fb924"
        + "27ae41e4649b934ca495991b7852b855";
    private static final DateTimeFormatter AMZ_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssX");

    private final S3CompatibilityModuleConfiguration configuration;

    public S3CompatibilityRequestValidator(S3CompatibilityModuleConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * @param request Incoming HTTP request.
     * @return An error response if the request is invalid for the configured auth mode.
     */
    public Optional<HttpResponse<String>> validate(HttpRequest<?> request) {
        return validate(request, EMPTY_PAYLOAD_SHA_256);
    }

    /**
     * @param request Incoming HTTP request.
     * @param actualPayloadHash The SHA-256 of the received request payload.
     * @return An error response if the request is invalid for the configured auth mode.
     */
    public Optional<HttpResponse<String>> validate(HttpRequest<?> request, @Nullable String actualPayloadHash) {
        if (configuration.getAuthMode() == S3CompatibilityAuthMode.NONE) {
            return Optional.empty();
        }
        if (configuration.getAccessKeyId().isEmpty() || configuration.getSecretAccessKey().isEmpty()) {
            return Optional.of(error(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "InternalError",
                "SigV4 is enabled but access credentials are not configured",
                null
            ));
        }
        return validateSigV4(request, configuration.getAccessKeyId().get(), configuration.getSecretAccessKey().get(), actualPayloadHash);
    }

    private Optional<HttpResponse<String>> validateSigV4(HttpRequest<?> request,
                                                         String accessKeyId,
                                                         String secretAccessKey,
                                                         @Nullable String actualPayloadHash) {
        String authorization = request.getHeaders().get(HttpHeaders.AUTHORIZATION);
        if (authorization == null || authorization.isBlank()) {
            return Optional.of(error(HttpStatus.FORBIDDEN, "AccessDenied", "Missing Authorization header", request));
        }
        if (!authorization.startsWith(ALGORITHM + " ")) {
            return Optional.of(error(HttpStatus.FORBIDDEN, "InvalidRequest", "Unsupported Authorization algorithm", request));
        }
        String signedAt = request.getHeaders().get("x-amz-date");
        if (signedAt == null || signedAt.isBlank()) {
            return Optional.of(error(HttpStatus.FORBIDDEN, "AccessDenied", "Missing x-amz-date header", request));
        }
        if (!isValidAmzDate(signedAt)) {
            return Optional.of(error(HttpStatus.FORBIDDEN, "AccessDenied", "Malformed x-amz-date header", request));
        }
        String payloadHash = request.getHeaders().get("x-amz-content-sha256");
        if (payloadHash == null || payloadHash.isBlank()) {
            return Optional.of(error(HttpStatus.FORBIDDEN, "AccessDenied", "Missing x-amz-content-sha256 header", request));
        }

        Map<String, String> authorizationAttributes = parseAuthorizationAttributes(authorization.substring(ALGORITHM.length()).trim());
        String credential = authorizationAttributes.get("Credential");
        String signedHeaders = authorizationAttributes.get("SignedHeaders");
        String providedSignature = authorizationAttributes.get("Signature");
        if (credential == null || signedHeaders == null || providedSignature == null) {
            return Optional.of(error(HttpStatus.FORBIDDEN, "InvalidRequest", "Malformed Authorization header", request));
        }

        String[] credentialScope = credential.split("/");
        if (credentialScope.length != 5) {
            return Optional.of(error(HttpStatus.FORBIDDEN, "AuthorizationHeaderMalformed", "Malformed Credential scope", request));
        }
        if (!accessKeyId.equals(credentialScope[0])) {
            return Optional.of(error(HttpStatus.FORBIDDEN, "InvalidAccessKeyId", "The AWS Access Key Id you provided does not exist", request));
        }
        if (!credentialScope[1].equals(signedAt.substring(0, 8))
            || !configuration.getRegion().equals(credentialScope[2])
            || !SERVICE.equals(credentialScope[3])
            || !TERMINAL.equals(credentialScope[4])) {
            return Optional.of(error(HttpStatus.FORBIDDEN, "AuthorizationHeaderMalformed", "Credential scope does not match the configured S3-compatible service", request));
        }

        if (!UNSIGNED_PAYLOAD.equals(payloadHash)
            && actualPayloadHash != null
            && !MessageDigest.isEqual(
            actualPayloadHash.getBytes(StandardCharsets.US_ASCII),
            payloadHash.getBytes(StandardCharsets.US_ASCII)
        )) {
            return Optional.of(error(HttpStatus.FORBIDDEN, "SignatureDoesNotMatch", "The provided x-amz-content-sha256 does not match the received payload", request));
        }

        String canonicalRequest;
        String stringToSign;
        try {
            canonicalRequest = request.getMethodName() + '\n'
                + canonicalUri(request.getUri()) + '\n'
                + canonicalQueryString(request.getUri()) + '\n'
                + canonicalHeaders(request, signedHeaders) + '\n'
                + signedHeaders + '\n'
                + payloadHash;
            stringToSign = ALGORITHM + '\n'
                + signedAt + '\n'
                + credentialScope[1] + '/' + credentialScope[2] + '/' + credentialScope[3] + '/' + credentialScope[4] + '\n'
                + sha256Hex(canonicalRequest);
        } catch (IllegalArgumentException e) {
            return Optional.of(error(HttpStatus.BAD_REQUEST, "InvalidRequest", e.getMessage(), request));
        }
        String expectedSignature = toHex(
            hmacSha256(
                signingKey(secretAccessKey, credentialScope[1], credentialScope[2], credentialScope[3]),
                stringToSign
            )
        );
        if (!MessageDigest.isEqual(expectedSignature.getBytes(StandardCharsets.US_ASCII), providedSignature.getBytes(StandardCharsets.US_ASCII))) {
            return Optional.of(error(HttpStatus.FORBIDDEN, "SignatureDoesNotMatch", "The request signature we calculated does not match the signature you provided", request));
        }
        return Optional.empty();
    }

    private static boolean isValidAmzDate(String signedAt) {
        try {
            Instant.from(AMZ_DATE_FORMATTER.parse(signedAt));
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private static Map<String, String> parseAuthorizationAttributes(String authorization) {
        Map<String, String> attributes = new LinkedHashMap<>();
        for (String token : authorization.split(",\\s*")) {
            String[] pair = token.split("=", 2);
            if (pair.length == 2) {
                attributes.put(pair[0], pair[1]);
            }
        }
        return attributes;
    }

    private static String canonicalUri(URI uri) {
        String rawPath = uri.getRawPath();
        if (rawPath == null || rawPath.isEmpty()) {
            return "/";
        }
        return rawPath;
    }

    private static String canonicalQueryString(URI uri) {
        String rawQuery = uri.getRawQuery();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return "";
        }
        List<String[]> pairs = new ArrayList<>();
        for (String token : rawQuery.split("&", -1)) {
            String[] pair = token.split("=", 2);
            String name = pair.length > 0 ? percentEncode(percentDecode(pair[0])) : "";
            String value = pair.length > 1 ? percentEncode(percentDecode(pair[1])) : "";
            pairs.add(new String[] {name, value});
        }
        pairs.sort(Comparator.<String[], String>comparing(parts -> parts[0]).thenComparing(parts -> parts[1]));
        StringBuilder result = new StringBuilder(rawQuery.length());
        for (int i = 0; i < pairs.size(); i++) {
            if (i > 0) {
                result.append('&');
            }
            result.append(pairs.get(i)[0]).append('=').append(pairs.get(i)[1]);
        }
        return result.toString();
    }

    private static String canonicalHeaders(HttpRequest<?> request, String signedHeaders) {
        StringBuilder canonical = new StringBuilder();
        for (String headerName : signedHeaders.split(";")) {
            List<String> values = request.getHeaders().getAll(headerName);
            String normalizedValue = values.stream()
                .map(S3CompatibilityRequestValidator::normalizeHeaderValue)
                .reduce((left, right) -> left + ',' + right)
                .orElse("");
            canonical.append(headerName.toLowerCase(Locale.ROOT))
                .append(':')
                .append(normalizedValue)
                .append('\n');
        }
        return canonical.toString();
    }

    private static String normalizeHeaderValue(String value) {
        return value.trim().replaceAll("\\s+", " ");
    }

    private static byte[] signingKey(String secretAccessKey, String date, String region, String service) {
        byte[] kDate = hmacSha256(("AWS4" + secretAccessKey).getBytes(StandardCharsets.UTF_8), date);
        byte[] kRegion = hmacSha256(kDate, region);
        byte[] kService = hmacSha256(kRegion, service);
        return hmacSha256(kService, TERMINAL);
    }

    private static byte[] hmacSha256(byte[] key, String message) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA_256);
            mac.init(new SecretKeySpec(key, HMAC_SHA_256));
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to compute HMAC-SHA256", e);
        }
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return toHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte current : bytes) {
            builder.append(String.format(Locale.ROOT, "%02x", current));
        }
        return builder.toString();
    }

    private static String percentDecode(String value) {
        byte[] bytes = new byte[value.length()];
        int length = 0;
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current == '%' && i + 2 < value.length()) {
                try {
                    bytes[length++] = (byte) Integer.parseInt(value.substring(i + 1, i + 3), 16);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Malformed query parameter encoding", e);
                }
                i += 2;
            } else {
                bytes[length++] = (byte) current;
            }
        }
        return new String(Arrays.copyOf(bytes, length), StandardCharsets.UTF_8);
    }

    private static String percentEncode(String value) {
        StringBuilder encoded = new StringBuilder(value.length());
        for (byte current : value.getBytes(StandardCharsets.UTF_8)) {
            if ((current >= 'a' && current <= 'z')
                || (current >= 'A' && current <= 'Z')
                || (current >= '0' && current <= '9')
                || current == '-'
                || current == '_'
                || current == '.'
                || current == '~') {
                encoded.append((char) current);
            } else {
                encoded.append('%').append(String.format(Locale.ROOT, "%02X", current));
            }
        }
        return encoded.toString();
    }

    private static HttpResponse<String> error(HttpStatus status,
                                              String code,
                                              String message,
                                              @Nullable HttpRequest<?> request) {
        StringBuilder xml = new StringBuilder(192);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        xml.append("<Error>");
        xml.append("<Code>").append(escapeXml(code)).append("</Code>");
        xml.append("<Message>").append(escapeXml(message)).append("</Message>");
        if (request != null) {
            xml.append("<Resource>").append(escapeXml(request.getUri().getRawPath())).append("</Resource>");
        }
        xml.append("</Error>");
        return HttpResponse.status(status)
            .contentType(MediaType.APPLICATION_XML_TYPE)
            .body(xml.toString());
    }

    private static String escapeXml(String value) {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;");
    }
}
