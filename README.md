package com.deep.token.core;

import com.deep.token.config.ApiTokenConfig;
import com.microsoft.aad.msal4j.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * TokenGeneratorUtil: Unified utility that supports both Basic Auth and Azure AD certificate-based token generation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenGeneratorUtil {

    private final ApiTokenConfig apiTokenConfig;
    private final RestTemplate restTokenTemplateBasic;
    private final RetryTemplate retryTemplate;

    /**
     * Public method to generate token using the configured authentication mode.
     *
     * @return AuthToken with access and optional ID token
     */
    public AuthToken generateAccessToken() {
        if (apiTokenConfig.isUseAzureAuth()) {
            log.info("[TokenGenerator] Azure AD certificate-based flow enabled");
            validateAzureConfig();
            return generateAzureAccessToken();
        } else {
            log.info("[TokenGenerator] Using Basic Auth flow by default");
            validateBasicAuthConfig();
            return generateBasicAccessToken();
        }
    }

    private void validateAzureConfig() {
        if (isEmpty(apiTokenConfig.getClientId()) ||
            isEmpty(apiTokenConfig.getTenantId()) ||
            isEmpty(apiTokenConfig.getScope()) ||
            isEmpty(apiTokenConfig.getCertPem()) ||
            isEmpty(apiTokenConfig.getKeyPem())) {
            throw new IllegalStateException("Azure AD config is missing required fields");
        }
    }

    private void validateBasicAuthConfig() {
        if (isEmpty(apiTokenConfig.getAuthorization()) ||
            isEmpty(apiTokenConfig.getTokenUrl())) {
            throw new IllegalStateException("Basic Auth config is missing required fields");
        }
    }

    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    private AuthToken generateBasicAccessToken() {
        String url = apiTokenConfig.getTokenUrl();
        HttpEntity<String> entity = buildBasicRequestEntity();

        try {
            ResponseEntity<Map> response = retryTemplate.execute(context -> {
                try {
                    return restTokenTemplateBasic.exchange(url, HttpMethod.POST, entity, Map.class);
                } catch (HttpStatusCodeException | ResourceAccessException ex) {
                    log.warn("Basic Auth retry {}/{} failed: {}", context.getRetryCount() + 1, apiTokenConfig.getRetry(), ex.getMessage());
                    throw ex;
                }
            });

            Map<String, Object> body = response.getBody();
            return new AuthToken((String) body.get("access_token"), (String) body.get("id_token"));

        } catch (Exception e) {
            log.error("Basic Auth token acquisition failed: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to acquire Basic Auth token", e);
        }
    }

    private HttpEntity<String> buildBasicRequestEntity() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.set("Authorization", apiTokenConfig.getAuthorization());
        return new HttpEntity<>("grant_type=client_credentials", headers);
    }

    private AuthToken generateAzureAccessToken() {
        int attempts = 0;
        while (attempts < apiTokenConfig.getRetry()) {
            try {
                ConfidentialClientApplication app = ConfidentialClientApplication.builder(
                        apiTokenConfig.getClientId(),
                        ClientCredentialFactory.createFromCertificate(loadCertificate(), loadPrivateKey())
                ).authority("https://login.microsoftonline.com/" + apiTokenConfig.getTenantId())
                 .build();

                ClientCredentialParameters params = ClientCredentialParameters.builder(
                        Collections.singleton(apiTokenConfig.getScope())
                ).build();

                CompletableFuture<IAuthenticationResult> future = app.acquireToken(params);
                IAuthenticationResult result = future.get();
                return new AuthToken(result.accessToken(), result.idToken());

            } catch (ExecutionException | InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Azure AD retry {}/{} interrupted: {}", attempts + 1, apiTokenConfig.getRetry(), e.getMessage());
                if (++attempts >= apiTokenConfig.getRetry()) throw new RuntimeException("Azure AD token retries exhausted", e);
            } catch (Exception e) {
                log.warn("Azure AD retry {}/{} failed: {}", attempts + 1, apiTokenConfig.getRetry(), e.getMessage());
                if (++attempts >= apiTokenConfig.getRetry()) throw new RuntimeException("Azure AD token retries exhausted", e);
                try {
                    Thread.sleep(apiTokenConfig.getRetryDelay());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Azure AD retry sleep interrupted", ie);
                }
            }
        }
        throw new RuntimeException("Unexpected error: retry loop exited");
    }

    private X509Certificate loadCertificate() throws Exception {
        String cleanPem = apiTokenConfig.getCertPem()
                .replaceAll("-----BEGIN CERTIFICATE-----", "")
                .replaceAll("-----END CERTIFICATE-----", "")
                .replaceAll("\\s+", "");
        byte[] certBytes = Base64.getDecoder().decode(cleanPem);
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        return (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(certBytes));
    }

    private PrivateKey loadPrivateKey() throws Exception {
        String cleanPem = apiTokenConfig.getKeyPem()
                .replaceAll("-----BEGIN (.*) PRIVATE KEY-----", "")
                .replaceAll("-----END (.*) PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] keyBytes = Base64.getDecoder().decode(cleanPem);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        return KeyFactory.getInstance("RSA").generatePrivate(keySpec);
    }
}
