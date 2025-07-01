# Kafka Error Router

The Kafka Error Router is a fault-tolerant, time-aware message retry engine designed to handle transient and recoverable failures in event-driven architectures.

Its core purpose is to:

Consume failed events from Kafka topics

Evaluate retry eligibility based on event age and retry policy

Reprocess messages after a configured delay (e.g., 15, 30, or 60 minutes)

Improve resilience and reliability of downstream systems by reducing message loss

Securely fetch Kafka configurations at runtime via token-based API calls (OAuth2)

---

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
