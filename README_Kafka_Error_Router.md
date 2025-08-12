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
