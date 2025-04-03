# 🕰️ Internal Scheduler - DEEP.io

This project is a Spring Boot-based internal scheduler service that allows you to define, run, and manage cron-based jobs dynamically. Jobs can be initialized at application startup from a YAML file or triggered via REST API.

---

## 🚀 Features

- ✅ Dynamic job scheduling using Quartz
- ✅ Schedule jobs on application startup via YAML
- ✅ Manual job scheduling via REST controller
- ✅ Secure REST API (Spring Security ready)
- ✅ Email notifications on job failures
- ✅ HTML email with Thymeleaf templates and embedded logos
- ✅ Supports both Basic Auth and Bearer Token-based API calls
- ✅ Retry-safe job execution (DisallowConcurrentExecution)

---

## 🧰 Tech Stack

| Layer      | Technology                         |
|------------|-------------------------------------|
| Core       | Java 17, Spring Boot                |
| Scheduler  | Quartz Scheduler                    |
| API        | Spring Web, Spring Security         |
| Email      | Spring Mail + Thymeleaf             |
| JSON       | Jackson (`ObjectMapper`)            |
| Validation | Jakarta Validation (`@Valid`)       |
| Build Tool | Maven                               |

---

## 📂 Project Structure

```
src
├── main
│   ├── java/com/tmobile/deep/internalscheduler
│   │   ├── controller           # REST APIs
│   │   ├── service              # Scheduling + Email
│   │   ├── model                # CronJob, DTOs
│   │   ├── config               # Quartz + Security + YAML configs
│   │   ├── util                 # Token utils, cron conversion
│   └── resources
│       ├── templates/failureEmailTemplate.html
│       ├── application.yml     # Cron jobs, email config
│       └── images              # Logos for email (cid:deep, cid:teams, etc.)
```

---

## ⚙️ How It Works

### 🔁 Job Initialization

On app startup, jobs are read from `application.yml` and registered into Quartz.

```yaml
jobs:
  - name: cleanup-dev
    schedule: "0 18 ? * 2"
    basicAuth: true
    args:
      - "https://api.internal/cleanup"
```

### 💻 Manual Job Scheduling (POST)

Use `/jobs` API to trigger jobs dynamically:

```http
POST /jobs
Content-Type: application/json

{
  "name": "manual-trigger",
  "schedule": "*/5 * * * *",
  "basicAuth": false,
  "args": ["https://my-api/test"]
}
```

### 📩 Email on Failure

If a job fails, an email is sent using Thymeleaf HTML template:

- Includes job name, environment, error code/message, and API path
- Embedded logos via `cid` (e.g. `cid:deep`)

---

## 🔐 Security

- Spring Security enabled
- `/jobs` endpoint can be secured via Basic Auth or API Key
- Customize via `SecurityConfig.java`

---

## 🧪 Testing

Test with Postman:

- Method: `POST`
- URL: `http://localhost:8080/jobs`
- Headers: `Content-Type: application/json`
- Auth: Basic (if Spring Security is enabled)

---

## 📧 Email Configuration

In `application.yml`:

```yaml
spring:
  mail:
    host: smtp.office365.com
    port: 587
    username: your-email@t-mobile.com
    password: your-app-password
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true
```

---

## 📦 Build & Run

```bash
mvn clean install
mvn spring-boot:run
```

---

## 📈 Future Enhancements

- [ ] Add Swagger UI for easier testing
- [ ] Retry strategy per job
- [ ] Job dashboard UI (optional)
- [ ] Job history persistence (e.g., DB or logs)

---

## 👨‍💻 Author

**Ganesh Kumar Yadagani**  
🛠️ Full Stack Java | Spring Boot | Quartz | Kafka | AWS  
📫 ganeshkumar.yadagani@gmail.com

---