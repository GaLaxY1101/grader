# Grader

**Thesis topic:** "Automated laboratory work checking system based on CI/CD approaches"
(«Система автоматизованої перевірки лабораторних робіт на основі підходів CI/CD»).

Українська версія: [README.md](README.md).

## Overview

Grader is a web system for running university courses and automatically checking programming
lab assignments. A teacher creates a course and assignments with tests. A student submits a
solution in the browser, and the system checks it the way production code is checked: the code
goes into its own Git repository, and **GitLab CI/CD** builds it and runs the tests in an isolated
Docker container. The student then sees the result of every test (expected vs. actual), and the
teacher sees the grades for the whole group.

Main features:

- courses, assignments, deadlines, file attachments, course archive and course templates;
- three roles: **student**, **teacher**, **administrator**; single sign-on through Keycloak;
- automatic checking of **Python** and **C/C++** code through GitLab CI/CD;
- compile check before submitting, in an isolated environment;
- per-test report, with a configurable level of detail for students;
- grade book with Excel export, and returning work to the student for rework;
- **test generation with a local LLM** (Ollama): the model writes tests, and the system checks
  them against the reference solution and against deliberately broken versions of it (mutants),
  then asks the model to fix the tests. Student data never leaves the server;
- bulk import of students and groups from an `.xlsx` file.

### Architecture

| Component | Technology |
|---|---|
| Backend (REST API) | Java 25, Spring Boot 4, PostgreSQL 16, Flyway |
| Frontend | Next.js 14, TypeScript, Material UI ([separate repository](https://github.com/GaLaxY1101/grader-frontend)) |
| Authentication | Keycloak 26 (OpenID Connect) |
| Checking submissions | GitLab CE 16.9 + GitLab Runner (Docker executor) |
| Test generation | Ollama, model `qwen2.5-coder:3b` |
| Sandboxes | Docker images `grader-sandbox-cpp`, `grader-sandbox-py` (no network, resource limits) |
| Files | S3-compatible storage |

## Installation

The whole system starts with one Docker Compose command. Only Docker and Git have to be
installed by hand. The databases, Keycloak, GitLab, the LLM and the other components are
pulled as containers automatically.

### Required software

| What | Requirement |
|---|---|
| OS | Linux (Ubuntu 22.04/24.04 recommended) or Windows 10/11 with Docker Desktop (WSL 2) |
| Docker | Docker Engine 24+ with the Docker Compose v2 plugin |
| Git | any recent version |
| Hardware | 4+ CPU cores, 12 GB RAM, 30 GB free disk space |
| Network | internet access on first start and for CI jobs |
| Browser | current Chrome, Firefox or Edge with internet access (the code editor loads from a CDN) |
| Optional | NVIDIA GPU with `nvidia-container-toolkit`: test generation is about 10× faster |

### Steps

1. Clone both repositories into one folder:
   ```bash
   mkdir grader && cd grader
   git clone https://github.com/GaLaxY1101/grader.git grader
   git clone https://github.com/GaLaxY1101/grader-frontend.git grader-frontend
   cd grader
   ```
2. Create the settings file and fill it in:
   ```bash
   cp .env.example .env
   ```
   Set `PUBLIC_HOST` to the address users open in their browser (`localhost` for a local run).
   Replace every `change-me` with a random string, e.g. from `openssl rand -hex 32`.
3. Start the system:
   ```bash
   docker compose -f compose.server.yaml up -d --build
   ```
   The first start takes 10–20 minutes: images are built, the LLM model (~2 GB) is downloaded,
   and GitLab initializes.
4. Check the backend: `curl http://localhost:8080/actuator/health` should return
   `{"status":"UP"}`.
5. Connect GitLab for checking submissions. This is a one-time step: create a token and
   register the runner. The exact commands are in [DEPLOY.md](DEPLOY.md), step 5.

The system is then available at `http://PUBLIC_HOST:3000`.

The full server guide, including common problems, security, backups and updates, is in
[DEPLOY.md](DEPLOY.md).

### Demo accounts

| Role | Login | Password |
|---|---|---|
| Administrator | `admin@grader.ua` | `Admin123!` |
| Teacher | `teacher@grader.ua` | `Teacher123!` |
| Student | `student@grader.ua` | `Student123!` |

Disable these accounts on a real server (Keycloak admin console → realm `university-grader` →
Users).

## User guide

### Signing in

Open `http://PUBLIC_HOST:3000` and click **Sign in with Keycloak**. Enter your login and
password. The system detects your role and shows the matching menu. To sign out, click the
sign-out icon in the top right corner.

### Student

1. **My Courses** lists the courses you are enrolled in. Open a course to see its assignments.
2. Open an assignment and read the description and attached files.
3. Depending on the assignment type:
   - **code**: write the solution in the built-in editor (a function stub is already there) and
     click **Submit**. Before submitting, the system checks that the code compiles; if it does not,
     a dialog shows the compiler errors;
   - **files**: drag the files into the upload area and submit them.
4. The attempt status changes from **Pending** / **Running** to **Passed** or **Failed** within
   about a minute. Open the attempt to see which tests passed, which failed, and what was
   expected. The teacher decides how much detail you see.
5. You can submit new attempts. If the teacher returned your work for rework, the assignment
   shows their comment.
6. **Archive** holds finished courses.

### Teacher

1. **Courses** → **Create Course**: create a course (name, academic year, semester), or create it
   from a template. **Manage Students** adds students or whole groups to the course.
2. In the course, click **+ Add** to create an assignment: title, description, max score,
   deadline and type (**Code only**, **Files only**, **Code + files**).
3. For automatic checking, turn on **Enable Code Check** and fill in:
   - **Language**: Python or C++;
   - **Function Signature / Template Code**: the stub the student sees in the editor;
   - **Reference Solution**: your model solution (never shown to students);
   - tests: pytest for Python (`assert actual == expected`), `TEST_CASE` / `EXPECT_EQ` macros for
     C++;
   - **Student feedback**: how much test detail students see (full report, test names only, or
     summary only).
4. **Generate tests with AI** generates tests from the assignment description, checks them
   against the reference solution and shows the progress. You can insert the result into the
   form and edit it.
5. On save, the system checks that the tests compile together with the reference solution.
6. The assignment lists all student submissions. Open one to see the code, the test report
   and the CI log. From there you can grade it, or return it for rework with a comment.
7. The course's **Grades** tab is the grade book, with Excel export.
8. **Templates** holds reusable course templates with assignments. **Archive Course** moves a
   finished course to the archive; it can be restored.

### Administrator

Has everything a teacher has, plus **Users & Groups**:

- **Users**: create, edit and delete users, and assign roles;
- **Groups**: academic groups and their members. The **Bulk Import** button imports students
  and groups from an `.xlsx` file;
- **Students**: the list of all students.
