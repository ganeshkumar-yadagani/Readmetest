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
