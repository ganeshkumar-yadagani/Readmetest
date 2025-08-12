package com.tmobile.deepio.azurestorage.auth;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import com.microsoft.aad.msal4j.*;
import reactor.core.publisher.Mono;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

public final class AzureTokenProvider implements TokenCredential {

  private final String tenantId;
  private final String clientId;
  private final String clientSecret;        // nullable if cert mode
  private final boolean enableCertAuth;
  private final String publicCertPem;       // nullable if secret mode
  private final String privateKeyPem;       // nullable if secret mode
  private final String authorityHost;       // e.g. https://login.microsoftonline.com

  private final ReentrantLock tokenLock = new ReentrantLock();
  private volatile AccessToken cachedToken;

  public AzureTokenProvider(String tenantId,
                            String clientId,
                            String clientSecret,
                            boolean enableCertAuth,
                            String publicCertPem,
                            String privateKeyPem,
                            String authorityHost) {
    this.tenantId = tenantId;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.enableCertAuth = enableCertAuth;
    this.publicCertPem = publicCertPem;
    this.privateKeyPem = privateKeyPem;
    this.authorityHost = (authorityHost == null || authorityHost.isBlank())
        ? "https://login.microsoftonline.com"
        : authorityHost;
  }

  @Override
  public Mono<AccessToken> getToken(TokenRequestContext requestContext) {
    return Mono.defer(() -> {
      AccessToken t = cachedToken;
      if (t != null && !isExpiring(t)) return Mono.just(t);

      tokenLock.lock();
      try {
        t = cachedToken;
        if (t != null && !isExpiring(t)) return Mono.just(t);

        Set<String> scopes = (requestContext.getScopes() == null || requestContext.getScopes().isEmpty())
            ? Collections.singleton("https://storage.azure.com/.default")
            : new HashSet<>(requestContext.getScopes());

        ConfidentialClientApplication app = buildMsalApp();
        IAuthenticationResult res = app.acquireToken(
            ClientCredentialParameters.builder(scopes).build()
        ).join();

        OffsetDateTime exp = OffsetDateTime.ofInstant(res.expiresOnDate().toInstant(), ZoneOffset.UTC);
        cachedToken = new AccessToken(res.accessToken(), exp);
        return Mono.just(cachedToken);
      } finally {
        tokenLock.unlock();
      }
    });
  }

  private ConfidentialClientApplication buildMsalApp() {
    IClientCredential credential;
    if (enableCertAuth) {
      PrivateKey key = PemUtil.readPkcs8PrivateKey(privateKeyPem);
      X509Certificate cert = PemUtil.readX509Certificate(publicCertPem);
      credential = ClientCredentialFactory.createFromCertificate(key, cert);
    } else {
      credential = ClientCredentialFactory.createFromSecret(clientSecret);
    }
    try {
      return ConfidentialClientApplication.builder(clientId, credential)
          .authority(authorityHost + "/" + tenantId)
          .build();
    } catch (Exception e) {
      throw new RuntimeException("Failed to build MSAL app", e);
    }
  }

  private static boolean isExpiring(AccessToken token) {
    return token.getExpiresAt().minusMinutes(5)
        .isBefore(OffsetDateTime.now(ZoneOffset.UTC));
  }

  /** Inline‑PEM helpers – keep or swap to your existing MsaUtils equivalents. */
  static final class PemUtil {
    static PrivateKey readPkcs8PrivateKey(String pem) {
      return AzurePem.parsePrivateKeyPkcs8(pem); // or your MsaUtils.getPrivateKeyFromPem
    }
    static X509Certificate readX509Certificate(String pem) {
      return AzurePem.parseX509Cert(pem);        // or your MsaUtils.getX509CertificateFromPem
    }
  }
}



package com.tmobile.deep.large.util;

import com.azure.core.credential.TokenCredential;
import com.azure.identity.ClientCertificateCredentialBuilder;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

public final class AzureCredentialUtil {

    private AzureCredentialUtil() {} // prevent instantiation

    public static TokenCredential buildInlinePemCredential(
            String tenantId, String clientId, String certPem, String keyPem) {
        PrivateKey key = readPkcs8PrivateKeyPem(keyPem);
        X509Certificate cert = readX509CertificatePem(certPem);
        return new ClientCertificateCredentialBuilder()
                .tenantId(tenantId)
                .clientId(clientId)
                .clientCertificate(key, cert)
                .build();
    }

    private static PrivateKey readPkcs8PrivateKeyPem(String pem) {
        String normalized = stripPem(pem, "-----BEGIN PRIVATE KEY-----", "-----END PRIVATE KEY-----");
        try {
            byte[] der = Base64.getMimeDecoder().decode(normalized.getBytes(StandardCharsets.US_ASCII));
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("Invalid PKCS#8 private key", e);
        }
    }

    private static X509Certificate readX509CertificatePem(String pem) {
        try {
            return (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(pem.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception direct) {
            try {
                String normalized = stripPem(pem, "-----BEGIN CERTIFICATE-----", "-----END CERTIFICATE-----");
                byte[] der = Base64.getMimeDecoder().decode(normalized.getBytes(StandardCharsets.US_ASCII));
                return (X509Certificate) CertificateFactory.getInstance("X.509")
                        .generateCertificate(new ByteArrayInputStream(der));
            } catch (Exception e) {
                throw new IllegalStateException("Invalid X.509 certificate", e);
            }
        }
    }

    private static String stripPem(String pem, String begin, String end) {
        String t = pem.replace("\r", "").trim();
        int i = t.indexOf(begin), j = t.indexOf(end);
        if (i >= 0 && j > i) t = t.substring(i + begin.length(), j);
        return t.replace("\n", "").replace(" ", "");
    }
}


+ private final java.util.concurrent.ConcurrentMap<String, BlobContainerClient> containers = new java.util.concurrent.ConcurrentHashMap<>();
+
+ private BlobContainerClient getContainer(String name) {
+     return containers.computeIfAbsent(name, n -> {
+         BlobContainerClient c = blobServiceClient.getBlobContainerClient(n);
+         if ("DEVELOPMENT".equalsIgnoreCase(environment)) c.createIfNotExists();
+         return c;
+     });
+ }
