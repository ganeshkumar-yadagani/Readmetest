# Large File Publisher Library

The **Large File Publisher Library** is a reusable Java/Spring Boot component for securely publishing large files (events) to Azure Blob Storage and notifying downstream consumers via event publishing endpoints (e.g., Kafka or REST APIs).

It supports multiple environments, secure token generation, and dynamic configuration for various event types.

---

## ✨ Features

- **Azure Blob Storage Integration**  
  - Upload large files securely using `azure-storage-blob`
  - Automatic container creation in development environments  
- **Secure Authentication**  
  - Azure AD Client Secret / Certificate-based authentication  
  - MSAL4J-based token generation with caching
- **Dynamic Event Publishing**  
  - Publish event metadata to downstream REST endpoints
  - Retry logic with configurable backoff
- **Customizable Headers**  
  - Add transaction IDs, skip-security flags, and file-transfer IDs dynamically
- **Environment-Aware**  
  - Separate handling for `development`, `production`, etc.
- **JSON Processing**  
  - Jackson-based serialization/deserialization
  - Support for custom date/time conversion

---

## 🏗 Architecture Overview

[Application using the Library]
        |
        | -> Large File Publisher Library
        |       - Event Creation
        |       - Token Generation
        |       - Azure Blob Upload
        |       - Event Publish to Endpoint
        |
        +--> Azure Blob Storage
        |
        +--> REST Endpoint (Consumer / Event Router / DLQ)

**Main Components**:
- **EventStorage** – Handles Azure Blob upload logic
- **PublisherService** – Publishes events to REST endpoints
- **RecordUtil** – JSON serialization/deserialization utilities
- **TokenGenerator** – Retrieves and caches Azure AD tokens

---

## 🛠 Tech Stack

| Layer           | Technology |
|-----------------|------------|
| Core            | Java 17, Spring Boot |
| Cloud Storage   | Azure Blob Storage SDK |
| Auth            | MSAL4J (Azure), Basic Token Auth |
| JSON            | Jackson (`ObjectMapper`) |
| Utilities       | Apache Commons, Guava |
| Build           | Maven |

---

## 📦 Maven Dependencies (Key)

```xml
<dependency>
    <groupId>com.azure</groupId>
    <artifactId>azure-storage-blob</artifactId>
    <version>12.25.4</version>
</dependency>
<dependency>
    <groupId>com.azure</groupId>
    <artifactId>azure-identity</artifactId>
    <version>1.12.4</version>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter</artifactId>
</dependency>
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>
```

---

## 🚀 Usage Example

```java
EventStorage storage = new EventStorage(...);
String blobUrl = storage.tryStreamToAzure(filePath, eventName, eventProducerId);

PublisherService publisher = new PublisherService(...);
publisher.publishEventToDeepIO(headers, eventJson, eventName, envName);
```

---

## 🔄 Retry Logic

- Built-in Spring `@Retryable` support for publishing events
- Configurable:
  - `maxAttempts`
  - `backoff` delay

---

## 📋 Prerequisites

- Java 17+
- Maven 3.8+
- Azure Blob Storage account
- Azure AD App registration for authentication

---

## ⚙️ Build & Install

```bash
# Build and install to local Maven repository
mvn clean install
```

---

## 📜 License

Proprietary – internal use only.
