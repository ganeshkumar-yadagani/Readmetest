import com.tmobile.deep.config.RabbitMQConfiguration;
import com.tmobile.deep.integration.RulesConfigIntegration;
import com.tmobile.deep.utils.MessagePropertiesInjector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
public class RabbitMQConfigurationTest {

    @InjectMocks
    private RabbitMQConfiguration rabbitMQConfiguration;

    @Mock
    private MessagePropertiesInjector messagePropertiesInjector;

    @Mock
    private RulesConfigIntegration rulesConfig;

    @Mock
    private RetryTemplate azureRetryTemplate;

    private static final String ENV_JSON = "{\"envName\":\"sandbox\",\"id\":10}";
    private static final String CLUSTER_JSON = "{\"bindingKey\":\"545\",\"cluster\":\"default\",\"type\":\"DEFAULT\",\"host\":\"10.5.204.41\",\"port\":\"5672\",\"virtualHost\":\"SND\",\"producerUserName\":\"deep3appsvod\",\"producerPassword\":\"deep3password\",\"consumerUserName\":\"Restricted\",\"publishEventName\":\"DEEPProductionEventsAndRules\",\"env\":7}";
    private static final String CLUSTER_JSON_SANDBOX = "{\"envName\":\"sandbox\"}";
    private static final String HOST = "10.5.204.41";
    private static final int PORT = 5672;
    private static final String VIRTUAL_HOST = "SND";
    private static final String RETRY_ROUTE_KEY = "retryRouteKey";
    private static final String CONFIRMATION_RESUBMISSION_ROUTE_KEY = "confirmationResubmissionRouteKey";
    private static final String RETRY_CONSUMER_NAME = "deeptransactionreplay_deep";

    @BeforeEach
    void init() throws IOException {
        when(rulesConfig.getCurrentEnvId()).thenReturn(1L);
        when(rulesConfig.getBaseUrl(1L)).thenReturn("http://localhost:8080");

        when(rulesConfig.exchange(any(), any(), any(), any()))
                .thenReturn(new ResponseEntity<>(ENV_JSON, HttpStatus.OK))
                .thenReturn(new ResponseEntity<>(CLUSTER_JSON_SANDBOX, HttpStatus.OK))
                .thenReturn(new ResponseEntity<>(CLUSTER_JSON, HttpStatus.OK));

        ReflectionTestUtils.setField(rabbitMQConfiguration, "retryRouteKey", RETRY_ROUTE_KEY);
        ReflectionTestUtils.setField(rabbitMQConfiguration, "confirmationResubmissionRouteKey", CONFIRMATION_RESUBMISSION_ROUTE_KEY);
        ReflectionTestUtils.setField(rabbitMQConfiguration, "retryConsumerName", RETRY_CONSUMER_NAME);
        ReflectionTestUtils.setField(rabbitMQConfiguration, "rulesConfig", rulesConfig);
        ReflectionTestUtils.setField(rabbitMQConfiguration, "messagePropertiesInjector", messagePropertiesInjector);
        ReflectionTestUtils.setField(rabbitMQConfiguration, "rabbitMQCertAuthEnabled", false);
        ReflectionTestUtils.setField(rabbitMQConfiguration, "rabbitMQAuthEnabled", false);
        ReflectionTestUtils.setField(rabbitMQConfiguration, "azureRetryTemplate", azureRetryTemplate);

        // prevent retry logic from triggering NPE
        when(azureRetryTemplate.execute(any(RetryCallback.class))).thenReturn(null);
    }

    @Test
    void test() throws IOException {
        String virtualHost = (String) ReflectionTestUtils.getField(rabbitMQConfiguration, "virtualHost");
        String host = (String) ReflectionTestUtils.getField(rabbitMQConfiguration, "host");
        int port = (int) ReflectionTestUtils.getField(rabbitMQConfiguration, "port");

        assertEquals(VIRTUAL_HOST, virtualHost);
        assertEquals(HOST, host);
        assertEquals(PORT, port);
    }

    @Test
    void rabbitTemplateResubmitIncomingEventQueue() {
        assertNotNull(rabbitMQConfiguration.rabbitTemplateResubmitIncomingEventQueue());
    }

    @Test
    void rabbitTemplateConfirmationResubmitIncomingEventsQueue() {
        assertNotNull(rabbitMQConfiguration.rabbitTemplateConfirmationResubmitIncomingEventQueue());
    }

    @Test
    void connectionFactory() {
        assertNotNull(rabbitMQConfiguration.connectionFactory());
    }

    @Test
    void getObjectMapper() {
        assertNotNull(rabbitMQConfiguration.getObjectMapper());
    }

    @Test
    void getMessageConverter() {
        assertNotNull(rabbitMQConfiguration.getMessageConverter());
    }

    @Test
    void getRetryRouteKey() {
        assertEquals(RETRY_ROUTE_KEY, rabbitMQConfiguration.getRetryRouteKey());
    }

    @Test
    void getConfirmationResubmissionRouteKey() {
        assertEquals(CONFIRMATION_RESUBMISSION_ROUTE_KEY, rabbitMQConfiguration.getConfirmationResubmissionRouteKey());
    }
}




import com.tmob.deep.config.RabbitMQConfiguration;
import com.tmob.deep.integration.RulesConfigIntegration;
import com.tmob.deep.utils.MessagePropertiesInjector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
public class RabbitMQConfigurationTest {

    @InjectMocks
    private RabbitMQConfiguration rabbitMQConfiguration;

    @Mock
    private MessagePropertiesInjector messagePropertiesInjector;

    @Mock
    private RulesConfigIntegration rulesConfig;

    @Mock
    private RetryTemplate azureRetryTemplate;

    private static final String HOST = "10.5.204.41";
    private static final String VIRTUAL_HOST = "SND";
    private static final int PORT = 5672;
    private static final String RETRY_ROUTE_KEY = "retryRouteKey";
    private static final String CONFIRMATION_RESUBMISSION_ROUTE_KEY = "confirmationResubmissionRouteKey";

    @BeforeEach
    public void setup() throws Exception {
        MockitoAnnotations.openMocks(this);

        // Mock RulesConfigIntegration
        when(rulesConfig.getCurrentEnvId()).thenReturn(1L);
        when(rulesConfig.getBaseUrl(1L)).thenReturn("http://localhost:8080");

        // Inject private fields
        ReflectionTestUtils.setField(rabbitMQConfiguration, "host", HOST);
        ReflectionTestUtils.setField(rabbitMQConfiguration, "virtualHost", VIRTUAL_HOST);
        ReflectionTestUtils.setField(rabbitMQConfiguration, "port", PORT);
        ReflectionTestUtils.setField(rabbitMQConfiguration, "retryRouteKey", RETRY_ROUTE_KEY);
        ReflectionTestUtils.setField(rabbitMQConfiguration, "confirmationResubmissionRouteKey", CONFIRMATION_RESUBMISSION_ROUTE_KEY);
        ReflectionTestUtils.setField(rabbitMQConfiguration, "rulesConfig", rulesConfig);
        ReflectionTestUtils.setField(rabbitMQConfiguration, "azureRetryTemplate", azureRetryTemplate);
        ReflectionTestUtils.setField(rabbitMQConfiguration, "messagePropertiesInjector", messagePropertiesInjector);

        // Stub retryTemplate.execute
        when(azureRetryTemplate.execute(any(RetryCallback.class)))
                .thenReturn(null); // token will be null; this avoids real MSAL token logic
    }

    @Test
    public void testConnectionFactory() {
        ConnectionFactory factory = rabbitMQConfiguration.connectionFactory();
        assertNotNull(factory);
    }

    @Test
    public void testGetObjectMapper() {
        assertNotNull(rabbitMQConfiguration.getObjectMapper());
    }

    @Test
    public void testGetMessageConverter() {
        assertNotNull(rabbitMQConfiguration.getMessageConverter());
    }

    @Test
    public void testGetRetryRouteKey() {
        assertEquals(RETRY_ROUTE_KEY, rabbitMQConfiguration.getRetryRouteKey());
    }

    @Test
    public void testGetConfirmationResubmissionRouteKey() {
        assertEquals(CONFIRMATION_RESUBMISSION_ROUTE_KEY, rabbitMQConfiguration.getConfirmationResubmissionRouteKey());
    }

    @Test
    public void testRabbitTemplateResubmitIncomingEventQueue() {
        RabbitTemplate template = rabbitMQConfiguration.rabbitTemplateResubmitIncomingEventQueue();
        assertNotNull(template);
    }

    @Test
    public void testRabbitTemplateConfirmationResubmissionIncomingEventQueue() {
        RabbitTemplate template = rabbitMQConfiguration.rabbitTemplateConfirmationResubmitIncomingEventQueue();
        assertNotNull(template);
    }
}



package com.tmobile.deep.config;

import com.microsoft.aad.msal4j.MsalServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.policy.ExceptionClassifierRetryPolicy;
import org.springframework.retry.policy.NeverRetryPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;

import java.util.HashMap;
import java.util.Map;

/**
 * Retry policy for Azure AD certificate-based token generation.
 * Retries are allowed only when MsalServiceException indicates a 5xx error.
 */
public class AzureRetryPolicy extends ExceptionClassifierRetryPolicy {

    private static final Logger LOGGER = LoggerFactory.getLogger(AzureRetryPolicy.class);
    private static final long serialVersionUID = 1L;

    public AzureRetryPolicy(int maxAttempts) {
        // Define which exceptions are retryable
        Map<Class<? extends Throwable>, Boolean> retryableExceptions = new HashMap<>();
        retryableExceptions.put(MsalServiceException.class, true); // explicitly allow MSAL exception

        // Create base retry policy
        SimpleRetryPolicy basePolicy = new SimpleRetryPolicy(maxAttempts, retryableExceptions);

        // Delegate retry classification to our custom logic
        this.setExceptionClassifier(classifiable -> getRetryPolicy(basePolicy, classifiable));
    }

    /**
     * Determine whether to retry based on unwrapped exception and MSAL status code.
     */
    private RetryPolicy getRetryPolicy(SimpleRetryPolicy basePolicy, Throwable throwable) {
        Throwable root = unwrap(throwable);

        LOGGER.warn("🛠 AzureRetryPolicy evaluating exception: {}", root.getClass().getName());

        if (root instanceof MsalServiceException) {
            MsalServiceException ex = (MsalServiceException) root;
            LOGGER.warn("🌐 MSAL errorCode={}, statusCode={}", ex.errorCode(), ex.statusCode());

            int statusCode = ex.statusCode();
            if (statusCode >= 500 && statusCode < 600) {
                LOGGER.info("✅ Retrying due to 5xx status code: {}", statusCode);
                return basePolicy;
            } else {
                LOGGER.info("🚫 Not retrying: status code = {}", statusCode);
            }
        } else {
            LOGGER.info("🚫 Not retrying: exception is not MsalServiceException");
        }

        return new NeverRetryPolicy();
    }

    /**
     * Unwraps nested exceptions (e.g., ExecutionException, CompletionException).
     */
    private Throwable unwrap(Throwable t) {
        while (t.getCause() != null && t != t.getCause()) {
            t = t.getCause();
        }
        return t;
    }
}



@Bean
public RetryTemplate retryTemplate() {
    RetryTemplate template = new RetryTemplate();

    FixedBackOffPolicy backOff = new FixedBackOffPolicy();
    backOff.setBackOffPeriod(1000); // 1 second

    RetryPolicy retryPolicy = new RetryPolicy() {
        @Override
        public boolean canRetry(RetryContext context) {
            Throwable cause = unwrap(context.getLastThrowable());
            if (cause instanceof MsalServiceException) {
                int statusCode = ((MsalServiceException) cause).statusCode();
                return statusCode >= 500 && statusCode < 600;
            }
            return false;
        }

        private Throwable unwrap(Throwable t) {
            while (t.getCause() != null && t != t.getCause()) {
                t = t.getCause();
            }
            return t;
        }

        @Override public void close(RetryContext context) {}
        @Override public void registerThrowable(RetryContext context, Throwable throwable) {}
        @Override public RetryContext open(RetryContext parent) { return new DefaultRetryContext(); }
    };

    template.setBackOffPolicy(backOff);
    template.setRetryPolicy(retryPolicy);
    return template;
}


import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.tcp.TcpClient;

import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient webClient() {
        TcpClient tcpClient = TcpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
            .doOnConnected(conn ->
                conn.addHandlerLast(new ReadTimeoutHandler(5, TimeUnit.SECONDS))
                    .addHandlerLast(new WriteTimeoutHandler(5, TimeUnit.SECONDS)));

        return WebClient.builder()
                .clientConnector(new reactor.netty.http.client.HttpClientConnector(HttpClient.from(tcpClient)))
                .build();
    }
}



import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

@Service
public class ExternalApiClient {

    private static final Logger logger = LoggerFactory.getLogger(ExternalApiClient.class);
    private final WebClient webClient;

    public ExternalApiClient(WebClient webClient) {
        this.webClient = webClient;
    }

    public Mono<String> callExternalApi(String url) {
        return webClient.get()
                .uri(url)
                .retrieve()
                .onStatus(HttpStatus::isError, clientResponse -> {
                    logger.error("Received error status: {}", clientResponse.statusCode());
                    return clientResponse.createException().flatMap(Mono::error);
                })
                .bodyToMono(String.class)
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                        .maxBackoff(Duration.ofSeconds(5))
                        .filter(ex -> {
                            logger.warn("Retrying due to: {}", ex.getMessage());
                            return ex instanceof WebClientResponseException;
                        })
                        .onRetryExhaustedThrow((retrySpec, signal) ->
                                new RuntimeException("Retries exhausted: " + signal.failure().getMessage())))
                .onErrorResume(ex -> {
                    logger.error("All retries failed. Falling back: {}", ex.getMessage());
                    return getFallbackResponse();
                });
    }

    private Mono<String> getFallbackResponse() {
        // You can call another service or return a default value here
        return Mono.just("Fallback response: service temporarily unavailable");
    }
}


import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final ExternalApiClient externalApiClient;

    public ApiController(ExternalApiClient externalApiClient) {
        this.externalApiClient = externalApiClient;
    }

    @GetMapping(value = "/data", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<String> getData() {
        String url = "https://jsonplaceholder.typicode.com/posts/1";
        return externalApiClient.callExternalApi(url);
    }
}

------------------------
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpServerErrorException;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Configuration
public class AppConfig {

    @Bean
    public RestTemplate restTemplate() {
        // Timeout and connection pooling
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(5000)
                .setSocketTimeout(5000)
                .build();

        CloseableHttpClient httpClient = HttpClientBuilder.create()
                .setDefaultRequestConfig(requestConfig)
                .setMaxConnTotal(200)
                .setMaxConnPerRoute(50)
                .build();

        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);

        RestTemplate restTemplate = new RestTemplate(factory);

        // JSON + TEXT response support
        MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter();
        jsonConverter.setSupportedMediaTypes(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN));
        restTemplate.getMessageConverters().add(0, jsonConverter);

        // Optional: ignore 4xx errors from throwing exceptions
        restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) throws IOException {
                // Retry only for 5xx errors
                return response.getStatusCode().series() == HttpStatus.Series.SERVER_ERROR;
            }
        });

        return restTemplate;
    }

    @Bean
    public RetryTemplate retryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();

        // Retry on network or server exceptions
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(3, Map.of(
                ResourceAccessException.class, true,
                HttpServerErrorException.class, true
        ));

        FixedBackOffPolicy backOffPolicy = new FixedBackOffPolicy();
        backOffPolicy.setBackOffPeriod(1000); // 1 second

        retryTemplate.setRetryPolicy(retryPolicy);
        retryTemplate.setBackOffPolicy(backOffPolicy);

        return retryTemplate;
    }
}
-------
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.ExceptionClassifierRetryPolicy;
import org.springframework.retry.policy.NeverRetryPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Configuration
public class RestClientConfig {

    @Bean
    public RestTemplate restTemplate() {
        // Timeout configuration
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(5000);

        RestTemplate restTemplate = new RestTemplate(factory);

        // Support JSON and plain text
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setSupportedMediaTypes(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN));
        restTemplate.getMessageConverters().add(0, converter);

        return restTemplate;
    }

    @Bean
    public RetryTemplate retryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();

        // Retry on 5xx or network-related exceptions
        ExceptionClassifierRetryPolicy policy = new ExceptionClassifierRetryPolicy();
        SimpleRetryPolicy simpleRetry = new SimpleRetryPolicy(3); // 3 attempts

        policy.setExceptionClassifier(throwable -> {
            if (throwable instanceof ResourceAccessException) {
                return simpleRetry;
            }
            if (throwable instanceof HttpStatusCodeException statusEx) {
                if (statusEx.getStatusCode().is5xxServerError()) {
                    return simpleRetry;
                }
                return new NeverRetryPolicy(); // no retry for 4xx
            }
            return new NeverRetryPolicy(); // default
        });

        // Fixed backoff (1s)
        FixedBackOffPolicy backOffPolicy = new FixedBackOffPolicy();
        backOffPolicy.setBackOffPeriod(1000);

        retryTemplate.setRetryPolicy(policy);
        retryTemplate.setBackOffPolicy(backOffPolicy);
        return retryTemplate;
    }
}


## 🧭 Project Modules

This project runs as three separate services with identical logic but different retry durations:

| Module Name             | Retry Duration | Kafka Topic                             |
|-------------------------|----------------|------------------------------------------|
| `fifteen-min-error-router` | 15 minutes     | `deep.sandbox.consumer.fifteenmins.error` |
| `thirty-min-error-router`  | 30 minutes     | `deep.sandbox.consumer.thirtymins.error`  |
| `sixty-min-error-router`   | 60 minutes     | `deep.sandbox.consumer.sixtymins.error`   |

---

## 🚀 Features

- ⏱ Time-based message reprocessing logic (15, 30, 60 mins)
- 🔄 Kafka consumer with manual acknowledgment
- 🔐 OAuth2-secured dynamic Kafka config fetching
- 🧰 Utilities for header parsing, UUID-to-time conversion, retry count handling
- 📤 Auto-publish to retry topics or DLQ based on retry state
- 🛑 Controlled consumer pause/resume using `KafkaManager`

---

## 🏗 Architecture Overview

```
Kafka → Consumer (Router)
         ├─ Retry Time Passed → publish to target topic
         └─ Retry Time Not Passed → requeue to source topic after delay

Supporting Components:
- KafkaManager: pauses/resumes listener
- DateMapper: UUID/time conversion
- RecordPublisher: abstract Kafka producer
- TokenGeneratorUtil: generates secure bearer tokens
- ConsumerConfig / KafkaProducerConfig: fetch dynamic config
```

---
## 🧰 Tech Stack

| Layer         | Technology                                                              |
|---------------|-------------------------------------------------------------------------|
| Core          | Java 11, Spring Boot                                                    |
| Messaging     | Apache Kafka, Spring Kafka                                              |
| Retry Engine  | Custom Consumer Logic (Time-based Routing)                             |
| Config        | Spring Cloud Config, OAuth2 Token Auth, Azure Cert-Based Auth           |
| Token Support | MSAL4J (Azure), Apigee Basic Token Auth                                 |
| Security      | Spring Security RSA, OAuth2 Bearer Token                                |
| JSON          | Jackson (`ObjectMapper`)                                                |
| Custom Libs   | deep-common, deep-kafkaproducer-java                                    |
| Monitoring    | Codahale Metrics, Spring Boot Actuator                                  |
| Testing       | JUnit 5, Mockito                                                        |
| Coverage      | JaCoCo (`0.35` complexity threshold enforced)                           |
| Build Tool    | Maven                                                                   |
| Repository    | GitLab Maven Registry                                                   |

## ⚙ Configuration

Each module has its own `application.yml`:

```yaml
deep:
  kafka:
    clusterPassword: ${KAFKA_CLUSTERCONFIG_PASSWORD}
    sasl-jaas-config: org.apache.kafka.common.security.scram.ScramLoginModule required
      username="%s" password="%s";
    error:
      router:
        scheduler:
          delay: 2
        processing:
          duration: 15   # Use 30 or 60 for other modules
    consumer:
      applicationtype: KAFKA_CONSUMER
      autocommit: false
      clusterName: west2-4
      applicationid: deep.sandbox.fifteenmins.error.retry.router
      topic: deep.sandbox.consumer.fifteenmins.error
    producer:
      applicationtype: KAFKA_PRODUCER
      clusterName: west2-4
  send:
    event: true
```

---

## 🧪 Testing

Run unit tests and coverage check:

```bash
mvn clean verify
```

Test coverage is enforced via JaCoCo:

- Minimum complexity coverage: **35%**
- Edit threshold in `pom.xml` → `jacoco-maven-plugin` section if needed

---

## 🛠 Build Instructions

```bash
mvn clean install
```

To build a specific retry service (e.g., 30-min):

```bash
cd thirty-min-error-router
mvn clean package
```

---

## 📁 Project Structure

```
src/
├── main/
│   └── java/
│       └── com.tmobile.deep.error.router/
│           ├── Consumer.java
│           ├── ConsumerConfig.java
│           ├── KafkaProducerConfig.java
│           ├── KafkaManager.java
│           ├── RecordPublisher.java
│           ├── DateMapper.java
│           ├── RouterUtil.java
│           └── DeepRouterConstants.java
└── test/
    └── java/...
```

---

## 🔐 Security & Tokens

This project fetches dynamic Kafka configuration by making a secured REST call using OAuth2 tokens generated via `TokenGeneratorUtil`. It supports:

- Bearer token authorization
- X-Auth-Originator header
- Optional support for certificate-based auth

---

## 📜 License

This project is internal to T-Mobile and intended for deep platform integration. Please refer to internal documentation for deployment and maintenance.

---

## 🙋‍♂️ Maintainers

- Ganesh Kumar Yadagani
- DEEP Middleware Team
