package com.deep.token.core;

import com.deep.token.config.ApiTokenConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * TokenGeneratorUtil selects the appropriate token strategy based on config.
 * It delegates to either Azure AD certificate-based token generation or Basic Auth.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenGeneratorUtil {

    private final ApiTokenConfig apiTokenConfig;
    private final AzureTokenClient azureTokenClient;
    private final BasicTokenClient basicTokenClient;

    /**
     * Entry point method for token generation.
     * Delegates to Azure or Basic token generator.
     *
     * @return AuthToken containing access and optional ID token
     */
    public AuthToken generateAccessToken() {
        log.info("TokenGeneratorUtil: Generating token using {} flow",
                apiTokenConfig.isUseAzureAuth() ? "Azure AD" : "Basic Auth");

        if (apiTokenConfig.isUseAzureAuth()) {
            return new AuthToken(azureTokenClient.generateToken());
        } else {
            return new AuthToken(basicTokenClient.generateToken());
        }
    }
}
package com.deep.token.core;

import com.deep.token.config.ApiTokenConfig;
import com.microsoft.aad.msal4j.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.Base64;

/**
 * AzureTokenClient handles Azure AD certificate-based token acquisition.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AzureTokenClient {

    private final ApiTokenConfig apiTokenConfig;

    public AuthToken generateToken() {
        try {
            ConfidentialClientApplication app = ConfidentialClientApplication.builder(
                    apiTokenConfig.getClientId(),
                    ClientCredentialFactory.createFromCertificate(
                            loadCertificate(),
                            loadPrivateKey())
            ).authority("https://login.microsoftonline.com/" + apiTokenConfig.getTenantId())
             .build();

            ClientCredentialParameters parameters = ClientCredentialParameters.builder(
                    Collections.singleton(apiTokenConfig.getScope())
            ).build();

            CompletableFuture<IAuthenticationResult> future = app.acquireToken(parameters);
            IAuthenticationResult result = future.get();

            return new AuthToken(result.accessToken(), result.idToken());

        } catch (ExecutionException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Azure AD token retrieval interrupted: {}", e.getMessage(), e);
            throw new RuntimeException("Azure AD token acquisition interrupted", e);

        } catch (Exception e) {
            log.error("Azure AD token acquisition failed: {}", e.getMessage(), e);
            throw new RuntimeException("Azure AD token acquisition failed", e);
        }
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
package com.deep.token.core;

import com.deep.token.config.ApiTokenConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.Map;

/**
 * BasicTokenClient handles Basic Auth token generation using RestTemplate.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BasicTokenClient {

    private final RestTemplate restTokenTemplateBasic;
    private final RetryTemplate retryTemplate;
    private final ApiTokenConfig apiTokenConfig;

    public AuthToken generateToken() {
        String url = apiTokenConfig.getTokenUrl();
        HttpEntity<String> request = buildRequestEntity();

        try {
            ResponseEntity<Map> response = retryTemplate.execute(context -> {
                try {
                    return restTokenTemplateBasic.exchange(url, HttpMethod.POST, request, Map.class);
                } catch (HttpStatusCodeException | ResourceAccessException ex) {
                    log.warn("Retry attempt {}/{} failed for Basic Auth token call: {}", context.getRetryCount() + 1, apiTokenConfig.getRetry(), ex.getMessage());
                    throw ex;
                }
            });

            Map<String, Object> body = response.getBody();
            return new AuthToken((String) body.get("access_token"), (String) body.get("id_token"));

        } catch (Exception e) {
            log.error("Failed to retrieve Basic Auth token: {}", e.getMessage(), e);
            throw new RuntimeException("Basic Auth token generation failed", e);
        }
    }

    private HttpEntity<String> buildRequestEntity() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.set("Authorization", apiTokenConfig.getAuthorization());
        return new HttpEntity<>("grant_type=client_credentials", headers);
    }
}
