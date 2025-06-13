package com.tmobile.deep.utils;

import com.azure.core.credential.AccessToken;
import com.microsoft.aad.msal4j.*;

import java.time.*;
import java.util.concurrent.*;

public class DeepCredentialsRefreshProvider extends RefreshProtectedCredentialsProvider<AccessToken> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeepCredentialsRefreshProvider.class);

    // MSAL Confidential Client and OAuth2 parameters
    private final ConfidentialClientApplication cca;
    private final ClientCredentialParameters clientCredentialParameters;

    // Shared cached token - must be volatile for thread visibility
    private static volatile AccessToken cachedToken;

    public DeepCredentialsRefreshProvider(ConfidentialClientApplication cca,
                                          ClientCredentialParameters clientCredentialParameters) {
        this.cca = cca;
        this.clientCredentialParameters = clientCredentialParameters;
    }

    /**
     * Retrieve an Azure AD access token using client credentials.
     * Caches the token and refreshes it only if it's close to expiring.
     * Thread-safe: only one thread will fetch a new token at a time.
     */
    @Override
    protected AccessToken retrieveToken() {
        OffsetDateTime now = OffsetDateTime.now();
        IAuthenticationResult result = null;

        // ✅ Fast path: return cached token if it’s still valid for 5+ minutes
        if (cachedToken != null && !isTokenExpiring(cachedToken, now)) {
            return cachedToken;
        }

        // 🔒 Lock to ensure only one thread refreshes the token at a time
        synchronized (DeepCredentialsRefreshProvider.class) {
            // 🔁 Double check inside synchronized block (avoids race conditions)
            if (cachedToken == null || isTokenExpiring(cachedToken, now)) {
                try {
                    LOGGER.info("Acquiring new Azure AD token using certificate...");
                    result = cca.acquireToken(clientCredentialParameters).get();

                    // ✅ Successfully acquired token — convert MSAL expiry to OffsetDateTime
                    cachedToken = new AccessToken(
                        result.accessToken(),
                        result.expiresOnDate().toInstant().atZone(ZoneId.systemDefault()).toOffsetDateTime()
                    );
                } catch (Exception e) {
                    if (result != null) {
                        // 🛑 If MSAL succeeded but parsing failed, fallback to 60-min expiry
                        cachedToken = new AccessToken(
                            result.accessToken(),
                            OffsetDateTime.now().plusMinutes(60)
                        );
                    } else {
                        // ❌ Complete failure — throw an exception
                        throw new RuntimeException("Failed to acquire access token from MSAL", e);
                    }
                }
            }
        }

        return cachedToken;
    }

    /**
     * Checks whether the given token is close to expiry (within 5 minutes).
     */
    private boolean isTokenExpiring(AccessToken token, OffsetDateTime now) {
        return token.getExpiresAt().isBefore(now.plusMinutes(5));
    }

    // These overrides are required by the parent class but not used in this context
    @Override
    protected String usernameFromToken(AccessToken token) {
        return "";
    }

    @Override
    public String getUsername() {
        return "";
    }

    @Override
    protected String passwordFromToken(AccessToken token) {
        return token.getToken();
    }

    @Override
    protected Duration timeBeforeExpiration(AccessToken token) {
        return Duration.between(OffsetDateTime.now(), token.getExpiresAt());
    }
}
