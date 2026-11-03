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
package io.micronaut.objectstorage.supabase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Starts a real Supabase Storage API server backed by PostgreSQL with Testcontainers and exposes the Micronaut
 * configuration needed to talk to it.
 *
 * <p>The server runs in single-tenant mode with the file backend and verifies bearer tokens with a fixed HS256
 * secret, so the tests mint their own service-role and anon tokens. Image transformation is disabled.</p>
 */
public final class SupabaseStorageContainer {
    private static final Logger LOG = LoggerFactory.getLogger(SupabaseStorageContainer.class);

    public static final String STORAGE_IMAGE = "supabase/storage-api:v1.83.5";
    private static final String POSTGRES_IMAGE = "postgres:17-alpine";
    private static final int STORAGE_PORT = 5000;
    private static final int POSTGRES_PORT = 5432;
    private static final String POSTGRES_ALIAS = "postgres";
    // Non-secret fixtures: these only exist inside throwaway test containers.
    @SuppressWarnings("java:S2068")
    private static final String POSTGRES_PASSWORD = "postgres";
    @SuppressWarnings("java:S2068")
    private static final String JWT_SECRET = "object-storage-supabase-test-jwt-secret-with-32-chars";
    private static final String SERVICE_ROLE = "service_role";
    private static final String ANON_ROLE = "anon";

    private static final Network NETWORK = Network.newNetwork();

    private static final GenericContainer<?> POSTGRES = new GenericContainer<>(POSTGRES_IMAGE)
        .withNetwork(NETWORK)
        .withNetworkAliases(POSTGRES_ALIAS)
        .withEnv(Map.of(
            "POSTGRES_USER", "postgres",
            "POSTGRES_PASSWORD", POSTGRES_PASSWORD,
            "POSTGRES_DB", "postgres"))
        .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 2)
            .withStartupTimeout(Duration.ofMinutes(2)));

    private static final GenericContainer<?> STORAGE = new GenericContainer<>(STORAGE_IMAGE)
        .dependsOn(POSTGRES)
        .withNetwork(NETWORK)
        .withExposedPorts(STORAGE_PORT)
        .withEnv(storageEnvironment())
        .withLogConsumer(new Slf4jLogConsumer(LOG).withPrefix("SUPABASE_STORAGE"))
        .waitingFor(Wait.forLogMessage(".*Started Successfully.*\\n", 1)
            .withStartupTimeout(Duration.ofMinutes(3)));

    private SupabaseStorageContainer() {
    }

    private static Map<String, String> storageEnvironment() {
        Map<String, String> env = new HashMap<>();
        env.put("SERVER_PORT", String.valueOf(STORAGE_PORT));
        env.put("AUTH_JWT_SECRET", JWT_SECRET);
        env.put("AUTH_JWT_ALGORITHM", "HS256");
        // Single tenant mode: migrations run as the superuser and create the anon, authenticated and
        // service_role roles when they are missing.
        env.put("DATABASE_URL", "postgres://postgres:" + POSTGRES_PASSWORD + "@" + POSTGRES_ALIAS + ":" + POSTGRES_PORT + "/postgres");
        env.put("DB_INSTALL_ROLES", "true");
        env.put("TENANT_ID", "object-storage-single-tenant");
        env.put("STORAGE_BACKEND", "file");
        env.put("STORAGE_FILE_BACKEND_PATH", "/tmp/storage");
        env.put("IMAGE_TRANSFORMATION_ENABLED", "false");
        env.put("UPLOAD_FILE_SIZE_LIMIT", "52428800");
        env.put("UPLOAD_SIGNED_URL_EXPIRATION_TIME", "120");
        env.put("TUS_URL_PATH", "/upload/resumable");
        env.put("LOG_LEVEL", "info");
        return env;
    }

    public static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static synchronized String baseUrl() {
        if (!STORAGE.isRunning()) {
            STORAGE.start();
        }
        return "http://" + STORAGE.getHost() + ":" + STORAGE.getMappedPort(STORAGE_PORT);
    }

    /**
     * Configuration addressing the Storage container directly: the {@code supabase} HTTP service points at the
     * container, whose API lives at its root (no {@code /storage/v1} gateway prefix), the publishable key is a
     * legacy anon JWT and the secret key a legacy service-role JWT.
     *
     * @param bucket Bucket of the {@code default} object storage
     */
    public static Map<String, String> properties(String bucket) {
        Map<String, String> properties = new HashMap<>();
        properties.put("micronaut.http.services.supabase.url", baseUrl());
        properties.put("supabase.storage-path", "");
        properties.put("supabase.publishable-key", anonJwt());
        properties.put("supabase.secret-key", serviceRoleJwt());
        properties.put(SupabaseStorageConfiguration.PREFIX + ".default.bucket", bucket);
        return properties;
    }

    public static String serviceRoleJwt() {
        return roleJwt(SERVICE_ROLE);
    }

    public static String anonJwt() {
        return roleJwt(ANON_ROLE);
    }

    private static String roleJwt(String role) {
        long now = Instant.now().getEpochSecond();
        String header = base64Url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = base64Url(("{\"role\":\"" + role + "\",\"iss\":\"supabase\",\"iat\":" + now
            + ",\"exp\":" + (now + 3600) + "}").getBytes(StandardCharsets.UTF_8));
        String signingInput = header + "." + payload;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(JWT_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return signingInput + "." + base64Url(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
