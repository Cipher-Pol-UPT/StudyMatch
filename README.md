<div align="center">

# StudyMatch

**Adaptive student group formation for university courses**

![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4-6DB33F?logo=springboot&logoColor=white)
![React](https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=black)
![TypeScript](https://img.shields.io/badge/TypeScript-6-3178C6?logo=typescript&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-database-4169E1?logo=postgresql&logoColor=white)

</div>

---

## About

University students work in groups many times during a degree, but those groups are usually formed at random, by friendship or by availability. StudyMatch uses what the system knows about each student to form balanced groups instead.

For every group assignment, StudyMatch looks at each student's **attendance**, their **grades in the subject's category** (Programming, Project Management…) and the result of a yearly **personality test**. It proposes groups that are balanced and complementary, explains why each group looks the way it does, and lets the teacher review and adjust them before publishing.

Around that core, StudyMatch helps students support each other. Students who finished a subject near the top of their class can become **tutors** and host online study sessions (**StudyRooms**) for students still taking it. Tutors earn **achievements** they can export as a certificate.

> Software Quality project, built across several sprints. The current sprint is **Sprint 1 - Project Foundation**.

## Features

**For students**
- Take a yearly personality test that shapes how groups are formed
- Keep a profile with picture, banner, about me, skills, interests and languages
- See grades, attendance and academic history
- See the groups they belong to and their group members
- Find, join and leave StudyRooms for the subjects they are taking
- Rate tutors after attending a StudyRoom

**For tutors**
- Apply to tutor a subject they passed in the top 5% of their class (approved by the teacher)
- Create, edit and cancel StudyRooms, and confirm who attended
- Earn achievements and export them as a PDF certificate with a verification code

**For teachers**
- Create assignments with a weight, group size range and constraints
- Generate groups automatically, with a preview and an explanation for each group
- Edit, export (CSV/PDF) and import groups, including groups shared across subjects
- Grade students and approve tutor applications

**For staff**
- Register students, subjects and enrolments
- Register attendance, including justified absences
- Manage special statutes, such as worker-students
- Start a new academic year and view analytics

**For admins**
- Create staff and teacher accounts
- Block and unblock accounts

## Tech stack

| Layer | Technology |
| --- | --- |
| Frontend | React 19, TypeScript, Vite |
| Backend | Java 21, Spring Boot 4 |
| Database | PostgreSQL |
| Build | Maven |
| Project management | GitHub Issues, Projects and Pull Requests |

## Project structure

```
StudyMatch/
├── backend/          # Spring Boot REST API (package com.studymatch)
│   ├── src/main/java
│   ├── src/main/resources/application.properties
│   ├── src/test/java
│   └── pom.xml
├── frontend/         # React + TypeScript single-page app (Vite)
│   ├── src/
│   └── package.json
└── README.md
```

## Getting started

### Prerequisites

| Tool | Version |
| --- | --- |
| [Git](https://git-scm.com/) | any recent version |
| [JDK](https://adoptium.net/) | 21 |
| [Node.js](https://nodejs.org/) | 22 LTS (20.19 or newer) |
| [PostgreSQL](https://www.postgresql.org/download/) |

Maven does not need to be installed: the backend uses the Maven Wrapper (`mvnw`).

### 1. Clone the repository

```bash
git clone https://github.com/Cipher-Pol-UPT/StudyMatch.git
cd StudyMatch
```

### 2. Run the backend

Spring Boot reads the database connection from environment variables, so no credentials are committed to the repository.

**macOS / Linux**

```bash
cd backend
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/studymatch
export SPRING_DATASOURCE_USERNAME=studymatch
export SPRING_DATASOURCE_PASSWORD=studymatch
./mvnw spring-boot:run
```

**Windows (PowerShell)**

```powershell
cd backend
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/studymatch"
$env:SPRING_DATASOURCE_USERNAME="studymatch"
$env:SPRING_DATASOURCE_PASSWORD="studymatch"
.\mvnw.cmd spring-boot:run
```

The API starts on **http://localhost:8080**.

### 3. Run the frontend

In a second terminal:

```bash
cd frontend
npm install
npm run dev
```

The app opens on **http://localhost:5173**.

### 4. Demo data (optional)

A new database has no accounts, so there is a `demo` profile that fills an empty database with accounts and some academic history. It does nothing if the database already has users.

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
```

Every demo account uses the password `StudyMatch1`.

| Role | Login | What to try |
| --- | --- | --- |
| Admin | `admin@studymatch.com` | Create staff and teacher accounts |
| Staff | `ana.lima@studymatch.com` | Register students, one by one or from a CSV |
| Teacher | `joao.silva@studymatch.com` | Teacher home |
| Student | `50000@students.studymatch.com` | Profile with degree, attendance, personality and an achievement |
| Student | `50001@students.studymatch.com` | Cold start: change the one-time password, then take the personality test |

The emails with credentials and reset codes are caught by Mailpit at **http://localhost:8025** (`docker compose up -d`).

### Useful commands

| Where | Command | What it does |
| --- | --- | --- |
| `backend` | `./mvnw spring-boot:run` | Starts the API |
| `backend` | `./mvnw test` | Runs the tests |
| `backend` | `./mvnw verify` | Builds and tests the project |
| `frontend` | `npm run dev` | Starts the dev server |
| `frontend` | `npm run lint` | Checks the code with ESLint |
| `frontend` | `npm run build` | Type-checks and builds for production |

## How we work

Every change follows the same flow:

**Issue → Branch → Pull Request → Review → Merge**

- Each task is a GitHub Issue. Use cases are split into a Frontend and a Backend issue.
- Work happens on a branch created for the issue, never directly on `main`.
- Every Pull Request is reviewed and approved by another team member before it is merged.
- The branch is deleted after the merge.

## Team

| | Name | GitHub |
| :---: | --- | --- |
| <img src="https://github.com/diogof146.png" width="48" alt="Diogo Ferreira" /> | Diogo Ferreira | [@diogof146](https://github.com/diogof146) |
| <img src="https://github.com/ManuYepes.png" width="48" alt="Manu" /> | Manu | [@ManuYepes](https://github.com/ManuYepes) |
| <img src="https://github.com/barbosaz1.png" width="48" alt="Rodrigo Barbosa" /> | Rodrigo Barbosa | [@barbosaz1](https://github.com/barbosaz1) |
| <img src="https://github.com/Falconz0012.png" width="48" alt="Tomás Falcão" /> | Tomás Falcão | [@Falconz0012](https://github.com/Falconz0012) |

## Special thanks

To our professor Fátima Leal , for the guidance and feedback throughout the project, and for reading this far.
Here is a very hard-working developer, our honorary team member:

<div align="center">

<img src="https://media.giphy.com/media/JIX9t2j0ZTN9S/giphy.gif" width="320" alt="A cat typing furiously on a laptop" />

</div>
