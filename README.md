package com.tmobile.deep.tokengenerator;

import javax.net.ssl.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

public class SslContextBuilderUtil {

    public static SSLContext buildSslContext(String certPem, String keyPem) throws Exception {
        X509Certificate certificate = parseCertificate(certPem);
        PrivateKey privateKey = parsePrivateKey(keyPem);

        // Create a KeyStore containing our cert and private key
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        keyStore.setKeyEntry("client", privateKey, "changeit".toCharArray(), new Certificate[]{certificate});

        // Initialize KeyManagerFactory with the keystore
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, "changeit".toCharArray());

        // Use default trust managers (Java's truststore)
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init((KeyStore) null);

        // Initialize SSLContext
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());

        return sslContext;
    }

    private static X509Certificate parseCertificate(String certPem) throws CertificateException {
        String cleanPem = certPem.replaceAll("-----BEGIN CERTIFICATE-----", "")
                                 .replaceAll("-----END CERTIFICATE-----", "")
                                 .replaceAll("\\s+", "");
        byte[] certBytes = Base64.getDecoder().decode(cleanPem);
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(certBytes));
    }

    private static PrivateKey parsePrivateKey(String keyPem) throws Exception {
        String cleanPem = keyPem.replaceAll("-----BEGIN (.*) PRIVATE KEY-----", "")
                                .replaceAll("-----END (.*) PRIVATE KEY-----", "")
                                .replaceAll("\\s+", "");
        byte[] keyBytes = Base64.getDecoder().decode(cleanPem);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePrivate(keySpec);
    }
} 
package com.tmobile.deep.tokengenerator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import java.time.Duration;
import java.util.Collections;

@Configuration
public class AppConfig {

    @Autowired
    private ApiTokenConfig config;

    @Bean("restTemplateBasic")
    public RestTemplate restTemplateBasic() {
        return new RestTemplateBuilder()
                .setConnectTimeout(Duration.ofMillis(config.getTimeOut()))
                .setReadTimeout(Duration.ofMillis(config.getTimeOut()))
                .messageConverters(new MappingJackson2HttpMessageConverter(Collections.singletonList(MediaType.APPLICATION_JSON)))
                .build();
    }

    @Bean("restTemplateCert")
    public RestTemplate restTemplateCert() throws Exception {
        SSLContext sslContext = SslContextBuilderUtil.buildSslContext(config.getCertPem(), config.getKeyPem());
        return new RestTemplateBuilder()
                .requestFactory(() -> {
                    try {
                        return new HttpComponentsClientHttpRequestFactoryWithSSL(sslContext);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to initialize custom SSL context", e);
                    }
                })
                .setConnectTimeout(Duration.ofMillis(config.getTimeOut()))
                .setReadTimeout(Duration.ofMillis(config.getTimeOut()))
                .messageConverters(new MappingJackson2HttpMessageConverter(Collections.singletonList(MediaType.APPLICATION_JSON)))
                .build();
    }

    @Bean("retryTemplate")
    public RetryTemplate retryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();
        FixedBackOffPolicy backOffPolicy = new FixedBackOffPolicy();
        backOffPolicy.setBackOffPeriod(config.getRetryDelay());
        retryTemplate.setBackOffPolicy(backOffPolicy);
        retryTemplate.setRetryPolicy(new HttpStatusRetryPolicy(config));
        return retryTemplate;
    }
} 
package com.tmobile.deep.tokengenerator;

import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContexts;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

import javax.net.ssl.SSLContext;

public class HttpComponentsClientHttpRequestFactoryWithSSL extends HttpComponentsClientHttpRequestFactory {

    public HttpComponentsClientHttpRequestFactoryWithSSL(SSLContext sslContext) {
        super(HttpClients.custom()
                .setSSLSocketFactory(new SSLConnectionSocketFactory(sslContext))
                .build());
    }
} 
package com.tmobile.deep.tokengenerator;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.Map;

@Slf4j
@Component
public class TokenGeneratorUtil {

    @Autowired
    private ApiTokenConfig config;

    @Autowired
    @Qualifier("restTemplateBasic")
    private RestTemplate restTemplateBasic;

    @Autowired
    @Qualifier("restTemplateCert")
    private RestTemplate restTemplateCert;

    @Autowired
    private RetryTemplate retryTemplate;

    public AuthToken generateAccessToken() {
        String endpoint = config.getHostname() + config.getBasepath();
        HttpEntity<String> request = createRequest();

        RestTemplate selectedTemplate = config.isUseCertificateAuth() ? restTemplateCert : restTemplateBasic;

        ResponseEntity<String> response = retryTemplate.execute(context -> {
            try {
                return selectedTemplate.exchange(endpoint, HttpMethod.POST, request, String.class);
            } catch (HttpStatusCodeException | ResourceAccessException ex) {
                log.warn("Retry attempt {} failed: {}", context.getRetryCount(), ex.getMessage());
                throw ex;
            }
        });

        return parseResponse(response);
    }

    private HttpEntity<String> createRequest() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (!config.isUseCertificateAuth()) {
            headers.set("Authorization", config.getAuthorization());
        }
        return new HttpEntity<>("{}", headers); // sending an empty JSON body
    }

    private AuthToken parseResponse(ResponseEntity<String> response) {
        AuthToken token = new AuthToken();
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> body = mapper.readValue(response.getBody(), Map.class);
            token.setAccessToken((String) body.get("access_token"));
            token.setIdToken((String) body.get("id_token"));
        } catch (Exception e) {
            log.error("Error parsing token response", e);
        }
        return token;
    }
} 
package com.tmobile.deep.tokengenerator;

import javax.net.ssl.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

public class SslContextBuilderUtil {

    public static SSLContext buildSslContext(String certPem, String keyPem) throws Exception {
        return buildSslContext(certPem, keyPem, null, null);
    }

    public static SSLContext buildSslContext(String certPem, String keyPem, String trustStoreBase64, String trustStorePassword) throws Exception {
        X509Certificate certificate = parseCertificate(certPem);
        PrivateKey privateKey = parsePrivateKey(keyPem);

        // Create a KeyStore containing our cert and private key
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        keyStore.setKeyEntry("client", privateKey, "changeit".toCharArray(), new Certificate[]{certificate});

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, "changeit".toCharArray());

        // Trust store configuration
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        if (trustStoreBase64 != null && trustStorePassword != null) {
            byte[] trustBytes = Base64.getDecoder().decode(trustStoreBase64);
            KeyStore trustStore = KeyStore.getInstance("JKS"); // or PKCS12 if needed
            trustStore.load(new ByteArrayInputStream(trustBytes), trustStorePassword.toCharArray());
            tmf.init(trustStore);
        } else {
            tmf.init((KeyStore) null); // Default system trust store
        }

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());

        return sslContext;
    }

    private static X509Certificate parseCertificate(String certPem) throws CertificateException {
        String cleanPem = certPem.replaceAll("-----BEGIN CERTIFICATE-----", "")
                                 .replaceAll("-----END CERTIFICATE-----", "")
                                 .replaceAll("\\s+", "");
        byte[] certBytes = Base64.getDecoder().decode(cleanPem);
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(certBytes));
    }

    private static PrivateKey parsePrivateKey(String keyPem) throws Exception {
        String cleanPem = keyPem.replaceAll("-----BEGIN (.*) PRIVATE KEY-----", "")
                                .replaceAll("-----END (.*) PRIVATE KEY-----", "")
                                .replaceAll("\\s+", "");
        byte[] keyBytes = Base64.getDecoder().decode(cleanPem);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePrivate(keySpec);
    }
} 
