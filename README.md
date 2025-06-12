# HandlerInfoRabbitConsumer

This is a Spring Boot-based message consumer application that listens to RabbitMQ queues. It supports OAuth2 and certificate-based authentication, dynamic RabbitMQ configuration, and Cassandra for persistence. Designed as part of a broader platform, it provides a secure and flexible foundation for processing inbound messages.

## 🚀 Features

- 🔄 Dynamic RabbitMQ consumer configuration
- 🔐 Secure authentication using OAuth2 (MSAL4J) and X.509 certificates
- 📦 Cassandra integration for storing metadata
- 🛡️ Spring Security configuration for endpoint protection
- ⚙️ Centralized listener logic for handling messages
- 🔧 Utility classes for tokens, logging, and formatting

---

## 🏗️ Project Structure

```
├── config
│   ├── CassandraConfig.java
│   ├── RabbitConsumerConfiguration.java
│   └── MapperBuilderConfiguration.java
│
├── consumer
│   └── HandlerInfoListener.java
│
├── security
│   └── WebSecurityConfiguration.java
│
├── util
│   ├── Util.java
│   ├── MsalUtils.java
│   └── DeepCredentialsRefreshProvider.java
│
└── README.md
```

---

## ⚙️ Component Summary

### `CassandraConfig.java`
Configures Cassandra keyspace, contact points, and session factory for data persistence.

### `RabbitConsumerConfiguration.java`
Initializes queues, exchanges, bindings, and the listener container with support for different auth modes and multi-environment setups.

### `WebSecurityConfiguration.java`
Secures HTTP endpoints; allows unauthenticated access to health and actuator endpoints.

### `HandlerInfoListener.java`
Consumes and processes RabbitMQ messages. Handles failures with structured logging and optional retry logic.

---

## 🔐 Authentication Mechanisms

### OAuth2 (Azure AD)
- Uses `MsalUtils` and `DeepCredentialsRefreshProvider` to fetch and refresh access tokens via MSAL4J.

### Certificate-Based
- SSL context is configured dynamically to support secure RabbitMQ connections using X.509 certs.

---

## 🧰 Utility Classes

- **`Util.java`** – Date formatting helper.
- **`MsalUtils.java`** – Token acquisition using MSAL4J.
- **`DeepCredentialsRefreshProvider.java`** – Auto-refreshing access token provider for RabbitMQ.
- **`ExceptionUtils.printStackTrace(e, limit: 0)`** – Custom stack trace formatter used in message listeners.

---

## ✅ Health Check

The following endpoint is used for readiness/liveness probes:

```
GET /actuator/health
```

---

## 💬 How to Test

- Deploy RabbitMQ with appropriate queues/exchanges.
- Post messages to the queue and observe logs from `HandlerInfoListener`.
- Verify tokens or cert-based authentication via logs.

---

## 👤 Maintained By

T-Mobile Deep Messaging Platform Team

---

## 📄 License

Internal use only. Unauthorized redistribution is prohibited.
