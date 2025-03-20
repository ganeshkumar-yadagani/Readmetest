
# Sample Processor Service

A Spring Boot microservice that processes incoming JSON event payloads, applies conditional validation, and returns structured error responses using centralized exception handling.

---

## 🛠️ Tech Stack

- **Java 17**
- **Spring Boot 3.1.3**
- **Spring Web** (RESTful APIs)
- **Spring Boot Actuator** (health and metrics endpoints)
- **Apache Commons Lang3**
- **Lombok** (for boilerplate reduction)
- **Docker** (for containerization)
- **Helm & Kubernetes** (for deployment)
- **GitLab CI/CD** (for automated builds & deployments)

---

## 📦 Project Structure

```
sample-processor/
├── helm-charts/
│   └── dio_settings.xml
├── src/
│   ├── main/
│   │   ├── java/com/tmobile/deepio/sampleprocessor/
│   │   │   ├── SampleProcessorApplication.java
│   │   │   ├── MainController.java
│   │   │   ├── GlobalExceptionHandler.java
│   │   │   └── ErrorResponse.java
│   │   └── resources/
│   │       └── application.yaml
│   └── test/java/com/tmobile/deepio/sampleprocessor/
│       └── SampleProcessorApplicationTests.java
├── .gitignore
├── .gitlab-ci.yml
├── Dockerfile
├── CODEOWNERS
├── files_blacklist
├── pom.xml
└── README.md
```

---

## 🚀 Getting Started

### ✅ Prerequisites

- Java 17+
- Maven 3.8+
- Docker (for containerization)
- GitLab Runner (for CI/CD)
- Kubernetes cluster + Helm (for deployment)

---

### 🔧 Build & Run (Locally)

#### Using Maven:
```bash
# Build the project
mvn clean install

# Run the application
mvn spring-boot:run
```

#### Or use the JAR:
```bash
java -jar target/sample-processor-0.1.0-SNAPSHOT.jar
```

---

## 🌐 REST API Endpoint

### POST `/deep/v1/events/{event-type}`

#### 🔸 Path Variable:
- `event-type` – Type of event being processed

#### 🔸 Request Body:
```json
{
  "sampleKey": "sampleValue"
}
```

#### 🔸 Behavior Configuration:
Defined in `application.yaml`:

```yaml
deepio:
  behavior: RETRY
```

- **RETRY** – Simulates a `ConnectException`
- **NORETRY** – Simulates an `IllegalArgumentException`
- **Other values** – Accepts the event successfully

#### 🔸 Example `curl` Request:
```bash
curl -X POST http://localhost:8080/deep/v1/events/MY_EVENT \
  -H "Content-Type: application/json" \
  -d '{"message": "Hello World"}'
```

---

## ❌ Error Handling

All exceptions are caught globally and returned in the following JSON format:

```json
{
  "exception": "java.net.ConnectException: Connection failure",
  "path": null,
  "message": "Connection failure",
  "error": "RETRY",
  "status": "2000MYSQL"
}
```

Handled error types:
- `ConnectException` → Retryable
- `IllegalArgumentException` → Not retryable
- Others → Default internal server error

---

## 🐳 Docker Support

### Dockerfile already included:
```bash
# Build Docker image
docker build -t sample-processor .

# Run container
docker run -p 8080:8080 sample-processor
```

---

## ⛵ Kubernetes + Helm (Optional)

This project supports Helm-based deployments.

Basic Helm chart structure is available in `helm-charts/`. You can enhance it to fit your cluster setup.

---

## ⚙️ GitLab CI/CD

CI/CD pipeline is defined in `.gitlab-ci.yml` to automate build, test, and deployment stages.

---

## 📄 License

This project is proprietary and internal to T-Mobile DeepIO.  
**All rights reserved.**

---

## 👨‍💻 Author

**Ganesh Kumar**  
Software Engineer  
📧 ganeshkumar.yadagani@gmail.com  
