/*
 * SonarQube
 * Copyright (C) 2009-2024 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.server.rule.ws;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.config.Configuration;

import javax.annotation.Nullable;

import static java.nio.charset.StandardCharsets.UTF_8;

public class AIRuleGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(AIRuleGenerator.class);

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
    private static final long JWT_EXPIRY_SECONDS = 300;

    private static final String PROP_AI_SERVICE_URL = "ai.service.url";
    private static final String ENV_AI_SERVICE_URL = "AI_SERVICE_URL";
    private static final String PROP_GENERATE_XPATH_ENDPOINT = "ai.service.generate.xpath.endpoint";
    private static final String ENV_GENERATE_XPATH_ENDPOINT = "AI_SERVICE_GENERATE_XPATH_ENDPOINT";
    private static final String PROP_JWT_PRIVATE_KEY = "codescan.jwt.private.key";
    private static final String ENV_JWT_PRIVATE_KEY = "CODESCAN_JWT_PRIVATE_KEY";

    private static final String PROMPT_RESOURCE = "sfmeta-system-prompt.txt";
    private static final String SF_METADATA_SYSTEM_PROMPT = loadSystemPrompt();

    private final HttpClient httpClient;
    private final Gson gson;
    private final String aiServiceUrl;
    private final String generateXpathEndpoint;
    private final PrivateKey jwtPrivateKey;

    public AIRuleGenerator(Configuration config) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        this.gson = new Gson();
        this.aiServiceUrl = resolveConfig(config, PROP_AI_SERVICE_URL, ENV_AI_SERVICE_URL);
        this.generateXpathEndpoint = resolveConfig(config, PROP_GENERATE_XPATH_ENDPOINT, ENV_GENERATE_XPATH_ENDPOINT);
        this.jwtPrivateKey = loadJwtPrivateKey(config);
    }

    public String generateXPath(String description, String organizationUuid, String userUuid) {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("organizationUuid", organizationUuid);
        requestBody.addProperty("userUuid", userUuid);
        requestBody.addProperty("chatProvider", "CLAUDE");
        requestBody.addProperty("ruleDescription", description);
        requestBody.addProperty("systemPrompt", SF_METADATA_SYSTEM_PROMPT);

        URI serviceUri = URI.create(aiServiceUrl).resolve(generateXpathEndpoint);
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(serviceUri)
                .header("Content-Type", "application/json")
                .timeout(REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)));

        String jwtToken = generateJwt(userUuid);
        if (jwtToken != null) {
            requestBuilder.header("Authorization", "Bearer " + jwtToken);
        }

        LOG.info("Calling AI service at: {} for org: {}", serviceUri, organizationUuid);

        try {
            HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            return parseResponse(response);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to connect to AI service: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AI service request interrupted", e);
        }
    }

    private String parseResponse(HttpResponse<String> response) {
        if (response.statusCode() == 401) {
            throw new IllegalStateException("AI service authentication failed (401). Verify codescan.jwt.private.key is configured correctly.");
        }

        String body = response.body();
        if (response.statusCode() != 200) {
            LOG.error("AI service returned status: {} body: {}", response.statusCode(), body);
            throw new IllegalStateException("AI service returned status: " + response.statusCode());
        }

        if (body == null) {
            throw new IllegalStateException("AI service returned empty response");
        }

        JsonObject responseJson = gson.fromJson(body, JsonObject.class);
        if (responseJson == null || !responseJson.has("response")) {
            throw new IllegalStateException("AI service response missing 'response' field");
        }
        String aiResponse = responseJson.get("response").getAsString().trim();
        LOG.debug("AI service response received, length: {}", aiResponse.length());
        return aiResponse;
    }

    @Nullable
    private String generateJwt(String subject) {
        if (jwtPrivateKey == null) {
            LOG.warn("No JWT private key configured (codescan.jwt.private.key). Sending request without auth.");
            return null;
        }

        try {
            String header = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(UTF_8));

            long now = Instant.now().getEpochSecond();
            JsonObject payload = new JsonObject();
            payload.addProperty("sub", subject);
            payload.addProperty("iat", now);
            payload.addProperty("exp", now + JWT_EXPIRY_SECONDS);
            payload.add("roles", gson.toJsonTree(new String[]{"ROLE_USER"}));
            String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(gson.toJson(payload).getBytes(UTF_8));

            String dataToSign = header + "." + encodedPayload;
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(jwtPrivateKey);
            signature.update(dataToSign.getBytes(UTF_8));
            String encodedSignature = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(signature.sign());

            return dataToSign + "." + encodedSignature;
        } catch (GeneralSecurityException e) {
            LOG.error("Failed to generate JWT token", e);
            return null;
        }
    }

    @Nullable
    private static PrivateKey loadJwtPrivateKey(Configuration config) {
        String pemValue = config.get(PROP_JWT_PRIVATE_KEY)
                .or(() -> java.util.Optional.ofNullable(System.getenv(ENV_JWT_PRIVATE_KEY)))
                .orElse("");
        if (pemValue.isEmpty()) {
            return null;
        }

        byte[] keyBytes = null;
        try {
            String pem = resolvePemContent(pemValue);
            String keyBase64 = pem
                    .replace("\\n", "\n")
                    .replaceAll("-----\\w+ PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            keyBytes = Base64.getDecoder().decode(keyBase64);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        } catch (GeneralSecurityException | IOException | InvalidPathException e) {
            LOG.error("Failed to load JWT private key", e);
            return null;
        } finally {
            if (keyBytes != null) {
                Arrays.fill(keyBytes, (byte) 0);
            }
        }
    }

    private static String resolvePemContent(String value) throws IOException {
        if (value.contains("BEGIN") || (!value.startsWith("/") && !value.startsWith("~"))) {
            return value;
        }
        String resolved = value.startsWith("~")
                ? System.getProperty("user.home") + value.substring(1)
                : value;
        Path keyPath = Paths.get(resolved).normalize().toAbsolutePath();
        if (!Files.isRegularFile(keyPath)) {
            throw new IOException("JWT private key file does not exist or is not a regular file: " + keyPath);
        }
        if (!Files.isReadable(keyPath)) {
            throw new IOException("JWT private key file is not readable: " + keyPath);
        }
        LOG.debug("Loading JWT private key from file");
        return Files.readString(keyPath);
    }

    private static String resolveConfig(Configuration config, String property, String envVar) {
        return config.get(property)
                .or(() -> java.util.Optional.ofNullable(System.getenv(envVar)))
                .filter(v -> !v.isBlank())
                .orElseThrow(() -> new IllegalStateException(
                        "Property '" + property + "' or environment variable '" + envVar + "' is required"));
    }

    private static String loadSystemPrompt() {
        try (InputStream is = AIRuleGenerator.class.getResourceAsStream(PROMPT_RESOURCE)) {
            if (is == null) {
                throw new IllegalStateException("System prompt resource not found: " + PROMPT_RESOURCE);
            }
            return new String(is.readAllBytes(), UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load system prompt resource: " + PROMPT_RESOURCE, e);
        }
    }
}
