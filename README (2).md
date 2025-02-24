# Deep Config Producer

## Overview
**Deep Config Producer** is a **Spring Boot** application designed to retrieve and manage **Kafka Producer Configurations** dynamically. It allows fetching **producer configurations** based on different cluster groups, regions, and environments.

## Features
- Provides REST APIs to retrieve Kafka producer configuration.
- Supports multiple cluster groups (e.g., ALPHA, BRAVO).
- Fetches configuration based on **environment** (Production, Non-Production).
- Optimized database queries to reduce DB calls.
- Uses **Spring Boot**, **Spring Cloud Config**, **Hibernate**, and **Kafka**.
- Implements **Lombok**, **MapStruct**, and **Spring Data JPA** for streamlined development.
- Supports **MySQL** as the database.
- Includes **Docker support** and **GitLab CI/CD pipeline**.

## Project Structure
```
deep-config-producer
│── src
│   ├── main
│   │   ├── java
│   │   │   ├── com.tmobile.deep
│   │   │   │   ├── config       # Configuration files
│   │   │   │   ├── controller   # REST API controllers
│   │   │   │   ├── domain       # Entity classes
│   │   │   │   ├── mapper       # MapStruct DTO mappings
│   │   │   │   ├── repository   # JPA repositories
│   │   │   │   ├── service      # Business logic
│   │   │   │   ├── util         # Utility classes
│   │   │   │   ├── DeepConfigProducerApplication.java  # Main class
│   ├── resources
│   │   ├── application.yml  # Configuration file
│── test                     # Test cases
│── target                   # Compiled output
│── .gitignore
│── .gitlab-ci.yml            # CI/CD pipeline
│── Dockerfile                # Containerization support
│── pom.xml                   # Maven dependencies
```

## Technologies Used
- **Spring Boot 3.2.x** - Framework for building microservices.
- **Spring Cloud Config** - Externalized configuration management.
- **Spring Data JPA** - ORM for database interactions.
- **Hibernate 6.1.x** - Persistence framework.
- **MySQL** - Relational database.
- **Lombok** - Reduces boilerplate code.
- **MapStruct** - DTO mapping.
- **OpenAPI (Springdoc)** - API documentation.
- **GitLab CI/CD** - Automated pipeline.
- **Docker** - Containerization.
- **RSA Encryption** - Security for sensitive credentials.

## Setup Instructions

### 1. Clone the Repository
```sh
git clone <repository-url>
cd deep-config-producer
```

### 2. Build the Project
```sh
mvn clean install
```

### 3. Run the Application
```sh
mvn spring-boot:run
```

### 4. Access APIs
Once the application is running, you can access the APIs using:
```
http://localhost:8080/producer-config/{env}
```
For OpenAPI documentation:
```
http://localhost:8080/swagger-ui.html
```

### 5. Docker Build & Run
```sh
docker build -t deep-config-producer .
docker run -p 8080:8080 deep-config-producer
```

## API Endpoints
| Method | Endpoint | Description |
|--------|---------|-------------|
| GET | `/producer-config/{env}` | Get producer config for the environment |
| GET | `/producer-config/{clusterGroup}/{env}` | Get config for a cluster group |
| GET | `/producer-config-per-region/{region}/{env}` | Get config by region |
| GET | `/producer-config-per-region/{rmqClusterRegion}/{kafkaClusterRegion}/{env}` | Get config by RMQ & Kafka region |

## Database Configuration (MySQL)
Update `application.yml` with:
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/kafka_config
    username: root
    password: password
```

## CI/CD Pipeline
- Uses **GitLab CI/CD** for automated testing and deployment.
- **Jacoco** integration for code coverage.
- **SonarQube** for code quality analysis.

## Contributing
1. Fork the repository.
2. Create a new branch (`feature-branch`).
3. Commit changes and push to GitLab.
4. Create a Merge Request.

## License
This project is **proprietary** and maintained by **T-Mobile Deep Team**.

---
**Author:** Deep Team, T-Mobile
