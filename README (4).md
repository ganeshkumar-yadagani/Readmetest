# Project Report Automation Tool

This Spring Boot application automates the process of standardizing and managing GitLab repositories across multiple projects. It fetches repo details, applies policies, updates files, and generates reports based on project metadata.

---

## 🧩 Features

- 🔐 **Protect Git Branches** – Apply protection rules (e.g., prevent force pushes).
- 🛡️ **Secure Environments** – Assign deployment permissions based on config.
- 📁 **Auto-Sync Files** – Sync standard files like `.gitlab-ci.yml`, `Dockerfile`, `README.md`.
- 📊 **Generate Reports** – Create README.md summaries for each project.
- 🔄 **Bulk Automation** – Works across all accessible GitLab repositories.

---

## 🏗️ Architecture

```text
Spring Boot App
│
├── Config (GitLab token, environments)
├── Models (Repo, Branch, File, etc.)
├── Services
│   └── RepoServiceService – GitLab API interaction
├── Processes
│   ├── ProtectBranches
│   ├── ProtectEnvironment
│   ├── SourceReport
│   └── UpdateProjects
└── Main Runner
    └── ProjectReportApplication
```

---

## ⚙️ Configuration

Edit the `application.yml`:

```yaml
application:
  gitLabToken: <your_private_token>
  gitLabAuthorEmail: your.email@example.com
  environments:
    - name: dev
      userIds:
        - 12345
        - 67890
```

You can also customize IDs, folder paths, and other constants in:
```
Constants.java
```

---

## 🛠️ How to Run

1. **Clone the repo**

```bash
git clone https://your-repo-url/project-report.git
cd project-report
```

2. **Build the project**

```bash
mvn clean install
```

3. **Run the app**

```bash
java -jar target/project-report-0.0.1-SNAPSHOT.jar
```

> The app will automatically fetch all projects, update README files, protect branches, and environments based on logic defined in the services.

---

## 📡 GitLab API Used

- `GET /projects`
- `GET /projects/:id/repository/files`
- `GET /projects/:id/repository/tree`
- `POST /projects/:id/protected_branches`
- `POST /projects/:id/protected_environments`
- `PATCH /projects/:id/repository/files/:file_path`

---

## 🔍 Example Output

Each project’s README will be updated with content like:

```
## Project: my-service
- Web URL: https://gitlab.com/tmobile/my-service
- CI/CD Config: HELM_DEPLOYMENT_NAME
- Docker Image: java8
```

---

## 👨‍💻 Contributors

- Ganesh Kumar – Architect & Developer
- T-Mobile Platform Engineering Team

---

## 📄 License

Internal use only – T-Mobile proprietary tool.
