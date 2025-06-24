package com.tmobile.deep.tokengenerator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.aad.msal4j.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TokenGeneratorUtil covering Basic Auth, Azure AD, retry, and expiration behavior.
 */
class TokenGeneratorUtilTest {

    private ApiTokenConfig config;
    private ApiTokenConfig.AzureConfig azureConfig;
    private RestTemplate restTemplate;
    private RetryTemplate basicRetryTemplate;
    private RetryTemplate azureRetryTemplate;
    private ConfidentialClientApplication msalApp;
    private ObjectMapper objectMapper;
    private TokenGeneratorUtil tokenGeneratorUtil;

    @BeforeEach
    void setup() {
        config = new ApiTokenConfig();
        azureConfig = new ApiTokenConfig.AzureConfig();
        config.setAzure(azureConfig);
        config.setRetry(3);
        config.setRetryDelay(100L);
        config.setTokenUrl("https://token.example.com");
        config.setAuthorization("Basic abc");
        config.setRequestBody("{}");

        restTemplate = mock(RestTemplate.class);
        basicRetryTemplate = new RetryTemplate();
        azureRetryTemplate = new RetryTemplate();
        msalApp = mock(ConfidentialClientApplication.class);
        objectMapper = new ObjectMapper();

        tokenGeneratorUtil = new TokenGeneratorUtil(config, restTemplate, basicRetryTemplate, msalApp, objectMapper);
    }

    /**
     * Verifies successful Basic Auth token generation using mocked RestTemplate response.
     */
    @Test
    void shouldReturnBasicTokenSuccessfully() throws Exception {
        azureConfig.setCertAuth(false);

        String json = "{\"access_token\":\"abc123\",\"id_token\":\"idXYZ\"}";
        ResponseEntity<String> response = new ResponseEntity<>(json, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenReturn(response);

        AuthToken token = tokenGeneratorUtil.generateAccessToken();

        assertEquals("abc123", token.getAccessToken());
        assertEquals("idXYZ", token.getIdToken());
    }

    /**
     * Simulates a retry scenario when a 5xx error occurs during Basic Auth token request.
     * Verifies that retry succeeds and returns a valid token.
     */
    @Test
    void shouldRetryAndSucceedOnServerError() {
        azureConfig.setCertAuth(false);

        String json = "{\"access_token\":\"abc123\",\"id_token\":\"idXYZ\"}";
        HttpServerErrorException exception = new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR);
        ResponseEntity<String> response = new ResponseEntity<>(json, HttpStatus.OK);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenThrow(exception)
                .thenReturn(response);

        AuthToken token = tokenGeneratorUtil.generateAccessToken();
        assertNotNull(token);
        assertEquals("abc123", token.getAccessToken());
    }

    /**
     * Verifies successful Azure AD token generation using certificate-based credentials.
     */
    @Test
    void shouldReturnAzureTokenSuccessfully() throws Exception {
        azureConfig.setCertAuth(true);
        azureConfig.setClientId("client");
        azureConfig.setTenantId("tenant");
        azureConfig.setCertPem("cert");
        azureConfig.setKeyPem("key");

        IAuthenticationResult mockResult = mock(IAuthenticationResult.class);
        when(mockResult.accessToken()).thenReturn("azure-token");
        when(mockResult.idToken()).thenReturn("azure-id");
        when(mockResult.expiresOnDate()).thenReturn(java.util.Date.from(OffsetDateTime.now().plusMinutes(15).toInstant()));
        when(msalApp.acquireToken(any())).thenReturn(CompletableFuture.completedFuture(mockResult));

        tokenGeneratorUtil = new TokenGeneratorUtil(config, restTemplate, azureRetryTemplate, msalApp, objectMapper);
        AuthToken token = tokenGeneratorUtil.generateAccessToken();

        assertEquals("azure-token", token.getAccessToken());
        assertEquals("azure-id", token.getIdToken());
        assertFalse(token.isTokenExpired());
    }

    /**
     * Simulates a retry scenario for Azure token generation when a 5xx server error occurs.
     */
    @Test
    void shouldRetryAzureTokenOnServerError() {
        azureConfig.setCertAuth(true);
        azureConfig.setClientId("client");
        azureConfig.setTenantId("tenant");
        azureConfig.setCertPem("cert");
        azureConfig.setKeyPem("key");

        MsalServiceException ex = new MsalServiceException("server error", "server_error", 500, null);
        CompletableFuture<IAuthenticationResult> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(ex);

        when(msalApp.acquireToken(any()))
                .thenReturn(failedFuture)
                .thenReturn(CompletableFuture.completedFuture(mock(IAuthenticationResult.class)));

        tokenGeneratorUtil = new TokenGeneratorUtil(config, restTemplate, azureRetryTemplate, msalApp, objectMapper);
        assertThrows(RuntimeException.class, tokenGeneratorUtil::generateAccessToken);
    }

    /**
     * Validates token expiration check returns true for already expired token.
     */
    @Test
    void shouldDetectTokenIsExpired() {
        AuthToken token = new AuthToken();
        token.setExpirationDate(OffsetDateTime.now().minusMinutes(1));
        assertTrue(token.isTokenExpired());
    }

    /**
     * Validates token is considered expired if it is within 2-minute buffer.
     */
    @Test
    void shouldDetectTokenIsNearExpiry() {
        AuthToken token = new AuthToken();
        token.setExpirationDate(OffsetDateTime.now().plusMinutes(1));
        assertTrue(token.isTokenExpired());
    }

    /**
     * Validates token is not expired when expiry is safely in the future.
     */
    @Test
    void shouldDetectTokenIsValid() {
        AuthToken token = new AuthToken();
        token.setExpirationDate(OffsetDateTime.now().plusMinutes(5));
        assertFalse(token.isTokenExpired());
    }
}
