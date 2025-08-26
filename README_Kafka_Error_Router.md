
# 🛡️ deepio-token-generator

A production-ready **Java Token Generator Library** for generating, caching, and refreshing **OAuth2 tokens**. Supports:

- 🔑 **Basic Auth (client_id + secret)**
- 🔐 **Azure AD Certificate-based authentication (client assertion)**
- 🔁 Built-in **retry logic** using Spring Retry
- 🔄 Smart **token caching** with auto-renew on expiry
- ✅ Compatible with Spring Boot apps and libraries

![Java](https://img.shields.io/badge/Java-8%2F11%2F17-blue.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.x%2F3.x-brightgreen)
![License](https://img.shields.io/badge/license-TMobile--Internal-orange)

---

## 🔧 Compatibility

| Component      | Version                     |
|----------------|-----------------------------|
| Java           | `8`, `11`, `17`             |
| Spring Boot    | `2.7.x` and `3.x`            |
| MSAL4J         | `1.13.8` or later            |
| Jackson        | `2.13+`                     |
| Spring Retry   | `1.3.1+`                    |

---

## ⚙️ Features

- 🔒 Secure token generation for **Azure AD OAuth2**
- ☁️ Supports **secret-based** and **certificate-based** flows
- ♻️ Token **caching and expiry handling** using `OffsetDateTime`
- 🔁 Built-in **retry template** support with configurable retry delay and count
- 🧾 Configurable via `application.yml` or environment variables
- 🧰 Designed to be imported as a shared library (JAR)

---

## 📦 How to Use

### 1. Add as a Dependency

If installed to your **local Maven repo**:

```xml
<dependency>
  <groupId>com.tmobile.deep</groupId>
  <artifactId>deepio-token-generator</artifactId>
  <version>1.0.0</version>
</dependency>
```

Or install manually:

```bash
mvn clean install
```

---

### 2. Configure in `application.yml`

```yaml
azure-token:
  enable-cert-auth: false   # true for cert-based auth
  authorization: Basic ${AZURE_TOKEN_AUTHORIZATION}
  auth-host: ${AZURE_AUTH_HOST}
  auth-path: ${AZURE_AUTH_PATH}
  client-id: ${AZURE_CLIENT_ID}
  tenant-id: ${AZURE_TENANT_ID}
  public-cert-pem: ${AZURE_PUBLIC_CERT}
  private-key-pem: ${AZURE_PRIVATE_KEY}
  retry: 3
  retry-delay: 2000
```

---

### 3. Autowire the Token Generator

```java
@Autowired
private TokenGeneratorUtil tokenGeneratorUtil;

String token = tokenGeneratorUtil.getAccessToken();
```

Or:

```java
AuthToken token = tokenGeneratorUtil.getCachedToken();
```

---

## 📂 Main Classes

| Class                  | Purpose |
|------------------------|---------|
| `TokenGeneratorUtil`   | Main service for fetching + caching tokens |
| `AuthToken`            | POJO holding access token + expiry info |
| `ApiTokenConfig`       | Loads YAML config properties |
| `AppConfig`            | Registers beans like `RestTemplate`, `RetryTemplate` |
| `HttpStatusRetryPolicy`| Custom retry policy for status codes |

---

## 🧪 Testing

Unit tests are available using JUnit5 and Mockito.

To run:

```bash
mvn test
```

Test token expiry logic:

```java
authToken.isTokenExpired(); // returns true/false
```

---

## 🔐 Certificate-Based Authentication

When `enable-cert-auth=true`, the library:

- Builds a JWT client assertion using MSAL4J
- Signs it using the private key from PEM
- Sends the assertion to Azure `/token` endpoint
- Extracts and caches the token response

---

## 🔁 Retry Support

Retries are applied automatically for:

- `5xx` responses
- Connection timeouts
- Azure AD intermittent failures

Configured via:

```yaml
retry: 3
retry-delay: 2000
```

---

## 📝 JavaDoc

All public classes and methods include JavaDoc comments. Example:

```java
/**
 * Returns the cached token or generates a new one if expired.
 */
public String getAccessToken();
```

---

## 🔒 Secrets Management

Use Kubernetes secrets or HashiCorp Vault to inject the following into the container:

- `AZURE_CLIENT_ID`
- `AZURE_TENANT_ID`
- `AZURE_TOKEN_AUTHORIZATION`
- `AZURE_PRIVATE_KEY`, `AZURE_PUBLIC_CERT`

---

## 📜 License

This project is internal and used within the **T-Mobile DEEP Platform**. Not for external distribution.

---

## 🙋 Contributing

1. Fork the repository
2. Create a feature branch
3. Ensure code is formatted and tested
4. Submit a merge request with context

---

## 📬 Contact

**Ganesh Kumar Yadagani**  
📧 ganeshkumar.yadagani@gmail.com  
☁️ Atlanta, GA (EST)

---

## 🧩 How It Works in Consumer Applications (Across Java & Spring Versions)

This token generator library is designed to be **plug-and-play** across a wide variety of Java and Spring Boot applications, regardless of their versions.

### ✅ Java Compatibility
- The compiled library is built with `Java 8` compatibility (`target=1.8`), making it **binary-compatible with Java 8, 11, and 17**.
- Whether your consumer application uses Java 8 for legacy systems or Java 17 for modern microservices, this library can be safely added as a Maven dependency.

### ✅ Spring Boot Compatibility
- **Spring Boot 2.7.x** (common in stable microservices) — fully supported.
- **Spring Boot 3.x** — fully compatible, tested with Jakarta namespace changes.
- Beans like `RestTemplate`, `RetryTemplate`, and `ObjectMapper` are **auto-configured via `@Configuration`**, avoiding any version-specific wiring issues.

### 🔄 How It Integrates with Consumers
1. **Consumer includes the JAR in its `pom.xml`** and pulls the library via Nexus, GitLab Package Registry, or local install.
2. **All YAML-based config remains within the consumer app**, giving full control of:
   - `client-id`, `client-secret`, `certificate`, `retry settings`, etc.
3. **No component scan is required**, since the library registers its beans explicitly via `@Configuration`.
4. The consumer simply autowires:
   ```java
   @Autowired
   private TokenGeneratorUtil tokenGeneratorUtil;
   ```
5. When `getAccessToken()` is called:
   - If a valid token is cached, it's returned instantly.
   - If not, the configured auth mode (basic or cert) is invoked to fetch a new token using `RestTemplate`.
   - If token generation fails due to a retryable condition (5xx, timeouts), `RetryTemplate` will attempt retries based on configured delay/count.

### 🔐 Secrets & Vault Integration
- Consumers can inject secrets via:
  - **Kubernetes Secrets + ConfigMap**
  - **Helm Values + HashiCorp Vault PEM mount**
  - **Environment Variables** like `AZURE_CLIENT_ID`, `AZURE_TOKEN_AUTHORIZATION`, etc.
- The library supports dynamic value resolution using `${...}` in Spring `application.yml`.

### 🧪 Test Strategy for Consumers
- Unit tests in the consumer app can mock `TokenGeneratorUtil` or stub `RestTemplate` to simulate downstream token responses.
- Integration tests can override YAML properties for mock tokens.

---
