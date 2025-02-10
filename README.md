
# **DEEPio Config Server** 🌐

A **Spring Boot application** that merges YAML configuration files from multiple sources and exposes them through a **REST API**. Built using **Spring Cloud Config Server** and **SnakeYAML**, this project is ideal for managing configuration across multiple environments.

---

## 🚀 **Features**
- Fetches configuration from remote repositories (Git, Bitbucket, GitHub).
- Merges properties from multiple YAML files.
- Returns clean YAML responses through REST endpoints.
- Handles nested keys and conflicts during merging.
- Robust exception handling and informative error messages.
- Simple and minimal configuration.

---

## 📁 **Project Structure**
```
src/main/java
└── com.tmobile.deepio.configserver
    ├── ConfigServerApplication.java
    ├── controller
    │   └── YamlResolveController.java
    ├── exception
    │   ├── CustomErrorResponse.java
    │   ├── GlobalExceptionHandler.java
    │   └── YamlMergeException.java
    └── service
        └── YamlResolveService.java
```

---

## 🛠️ **Built With**
| Dependency                         | Description                                        |
|------------------------------------|----------------------------------------------------|
| **Spring Boot**                    | Framework for building the application.            |
| **Spring Cloud Config Server**     | Fetches configuration files from remote repositories. |
| **SnakeYAML**                      | Parses and processes YAML files.                   |

---

## 📦 **Setup Instructions**

### **Step 1:** Clone the repository
```bash
git clone https://github.com/yourusername/spring-boot-yaml-configserver.git
cd spring-boot-yaml-configserver
```

### **Step 2:** Build the project
```bash
mvn clean install
```

### **Step 3:** Run the project
```bash
mvn spring-boot:run
```

---

## 🔧 **Configuration**
### **Remote Repository Configuration**
Specify the URI of your remote configuration repository (GitLab, Bitbucket, etc.) in `bootstrap.properties`:

```properties
spring.cloud.config.server.git.uri=https://gitlab.com/your-repo/configurations
spring.cloud.config.server.git.default-label=main
```

---

## 🔗 **REST API Usage**

### **Endpoint**
```
GET /yaml/{application}/{profile}
```

| Parameter    | Description                                |
|--------------|--------------------------------------------|
| application  | The name of the application (e.g., `app1`) |
| profile      | The environment/profile (e.g., `dev`)      |

### **Example Request**
```bash
GET http://localhost:8080/yaml/app1/dev
```

### **Example Response**
```yaml
app:
  name: MyApplication
logging:
  level: DEBUG
database:
  host: localhost
  port: 5432
```

---

## 🛑 **Error Handling**
The application provides clean error responses when failures occur, such as YAML merging errors or unavailable sources.

### **Error Response Example**
```json
{
    "statusCode": 500,
    "message": "YAML merge failed: Error merging YAML for application: app1, profile: dev"
}
```

---

## 📚 **Project Overview**

### **1. `YamlResolveController`**
Handles REST requests and returns merged YAML configurations.

### **2. `YamlResolveService`**
- Fetches configuration properties from multiple sources.
- Merges configurations using nested YAML structures.
- Handles conflicts when multiple files provide the same keys.

### **3. `GlobalExceptionHandler`**
Catches application-specific and generic exceptions, returning meaningful error responses.

### **4. `CustomErrorResponse`**
Defines the structure of the error response containing:
  - `statusCode`: HTTP status code (e.g., 500).
  - `message`: A user-friendly error message.

