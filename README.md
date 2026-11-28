<div align="center">

# StudyMatch

**Adaptive student group formation for university courses**

![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4-6DB33F?logo=springboot&logoColor=white)
![React](https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=black)
![TypeScript](https://img.shields.io/badge/TypeScript-6-3178C6?logo=typescript&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17-4169E1?logo=postgresql&logoColor=white)

[![CI](https://github.com/Cipher-Pol-UPT/StudyMatch/actions/workflows/ci.yml/badge.svg)](https://github.com/Cipher-Pol-UPT/StudyMatch/actions/workflows/ci.yml)
[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=Cipher-Pol-UPT_StudyMatch&metric=alert_status)](https://sonarcloud.io/project/overview?id=Cipher-Pol-UPT_StudyMatch)

</div>

---

## About

University students work in groups many times during a degree, but those groups are usually formed at random, by friendship or by availability. StudyMatch uses what the system knows about each student to form balanced groups instead.

For every group assignment, StudyMatch looks at each student's **attendance**, their **grades in the subject's category** (Programming, Project Management…) and the result of a yearly **personality test**. It proposes groups that are balanced and complementary, explains why each group looks the way it does, and lets the teacher review and adjust them before publishing.

Around that core, StudyMatch helps students support each other. Students who finished a subject near the top of their class can become **tutors** and host online study sessions (**StudyRooms**) for students still taking it. Tutors earn **achievements** they can export as a certificate.

> Software Quality project, built across three sprints. We're in **Sprint 3: automatic group formation, software quality and the final release**, due 6 December 2026.

## Features

This is the whole product as planned. Not all of it is built yet; the [project board](https://github.com/orgs/Cipher-Pol-UPT/projects/1) shows what's done and what's left.

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

| Layer | Technology | Why |
| --- | --- | --- |
| Frontend | React 19, TypeScript, Vite | TypeScript catches a wrong field name before the browser does, and Vite's dev server proxies `/api` to the backend, so there's no CORS setup while developing. |
| Backend | Java 21, Spring Boot 4 | Java is what the course's labs and testing tools (JUnit 5, JaCoCo) use. Spring gives us sessions, CSRF protection, validation and JPA without writing them ourselves. |
| Database | PostgreSQL 17 (Docker) | Our data is relational (students, enrolments, grades, groups), and the unique constraints and row locks are what stop two Staff from enrolling the same student at once. Docker gives everyone the same version. |
| Build | Maven (wrapper) | `./mvnw` downloads the right Maven, so nobody has to install it, and the JaCoCo report comes out of the same build. |
| API docs | springdoc-openapi (Swagger UI) | Built from the controllers, so it can't fall behind the code the way a hand-written list would. |
| Tests | JUnit 5, Mockito, JaCoCo, Vitest, Testing Library | JUnit 5 and JaCoCo are what the course asks for; Vitest runs with the same config as Vite. |
| Local email | Mailpit (Docker) | Catches every email the backend sends (credentials, reset codes) so we can read them without a real mail server. |
| Project management | GitHub Issues, Projects and Pull Requests | Every change can be traced from its issue to its branch, PR and review. |

## Project structure

```
StudyMatch/
├── backend/                  # Spring Boot REST API (package com.studymatch)
│   ├── src/main/java         # controllers, services, repositories, models, dtos...
│   ├── src/main/resources    # application.properties, data.sql (demo data)
│   ├── src/test/java         # unit and integration tests
│   ├── docker-compose.yml    # PostgreSQL + Mailpit
│   └── pom.xml
├── frontend/                 # React + TypeScript single-page app (Vite)
│   ├── src/                  # pages, api client, studymatch-ui components, i18n
│   ├── tests/                # Vitest tests
│   └── package.json
├── docs/                     # architecture, domain, conventions, contributing, testing, api, group matching
└── README.md
```

## Getting started

### Prerequisites

| Tool | Version |
| --- | --- |
| [Git](https://git-scm.com/) | any recent version |
| [JDK](https://adoptium.net/) | 21 |
| [Node.js](https://nodejs.org/) | 22 LTS (20.19 or newer) |
| [Docker](https://docs.docker.com/get-docker/) with Compose | any recent version |

Maven doesn't need to be installed, the backend uses the Maven Wrapper (`mvnw`). PostgreSQL doesn't either, it runs in Docker. If you also have a local PostgreSQL running, stop it first: it takes port 5432 and the backend ends up talking to the wrong database (`role "studymatch" does not exist`).

### 1. Clone the repository

```bash
git clone https://github.com/Cipher-Pol-UPT/StudyMatch.git
cd StudyMatch
```

### 2. Start the database and the mail server

```bash
cd backend
docker compose up -d
```

This starts PostgreSQL on port 5432 and Mailpit on 1025 (SMTP) and 8025 (web inbox). The data stays in a Docker volume between runs.

| Command (in `backend/`) | What it does |
| --- | --- |
| `docker compose down` | stops both, keeps the data |
| `docker compose down -v` | stops both and deletes the database |
| `docker compose exec db psql -U studymatch -d studymatch` | opens psql (`\dt` lists the tables, `\q` quits) |

### 3. Run the backend

```bash
./mvnw spring-boot:run        # macOS / Linux
.\mvnw.cmd spring-boot:run    # Windows
```

The API starts on **http://localhost:8080**. Hibernate creates the tables on the first run, and `data.sql` fills them with demo data on every start (it skips rows that already exist).

### 4. Run the frontend

In a second terminal:

```bash
cd frontend
npm ci
npm run dev
```

The app opens on **http://localhost:5173**.

### Configuration

Everything has a default that matches `docker-compose.yml`, so nothing needs to be set to run it locally. Set these environment variables to point somewhere else:

| Variable | Default | Used for |
| --- | --- | --- |
| `DB_NAME` | `studymatch` | database name (also read by `docker compose`) |
| `DB_USER` | `studymatch` | database user (also read by `docker compose`) |
| `DB_PASSWORD` | `studymatch` | database password (also read by `docker compose`) |
| `MAIL_HOST` | `localhost` | SMTP server |
| `MAIL_PORT` | `1025` | SMTP port |
| `MAIL_USERNAME` | empty | SMTP login |
| `MAIL_PASSWORD` | empty | SMTP password |
| `MAIL_AUTH` | `false` | `true` if the SMTP server needs a login |
| `MAIL_STARTTLS` | `false` | `true` to use STARTTLS |
| `MAIL_FROM` | `no-reply@studymatch.com` | sender of every email |
| `GOOGLE_CLIENT_ID` | empty | Google OAuth client that creates the StudyRooms' Meet links |
| `GOOGLE_CLIENT_SECRET` | empty | its secret |
| `GOOGLE_REFRESH_TOKEN` | empty | refresh token of the StudyMatch Google account the Meet links are made on |

Without the three Google variables everything works, but tutors have to paste their own Meet link. To set them up: in a Google Cloud project, enable the Google Meet REST API and create an OAuth client of type "Web application" with `https://developers.google.com/oauthplayground` as a redirect URI. In the [OAuth Playground](https://developers.google.com/oauthplayground), under the settings use your own client id and secret, authorise the `https://www.googleapis.com/auth/meetings.space.created` scope with the StudyMatch Google account and exchange the code for a refresh token. Never commit any of them.

The GIFs in the chat come from GIPHY, which the browser calls with its own key. Without one everything else works and the GIF panel says GIFs aren't set up. To set it, create an API key at [developers.giphy.com](https://developers.giphy.com/dashboard/) and put it in `frontend/.env.local`, which git ignores, then restart `npm run dev`:

```bash
VITE_GIPHY_API_KEY=your-key
```

The key ends up in the JavaScript the browser downloads, so anyone using the app can read it. That's how GIPHY expects it to be used, but never commit it.

On a real server, also set `springdoc.api-docs.enabled=false` and `springdoc.swagger-ui.enabled=false` to hide the API docs.

### Demo accounts

All of them use the password **`Demo1234`**. They come from `backend/src/main/resources/data.sql` and aren't real people.

| Role | Login | What's there |
| --- | --- | --- |
| Admin | `ana.admin@studymatch.com` | |
| Staff | `sofia.staff@studymatch.com` | |
| Teacher | `tiago.teacher@studymatch.com` | Programming I/II, Databases, Software Quality, plus Inês' tutor application |
| Teacher | `marta.teacher@studymatch.com` | Calculus I, Statistics, Project Management, Software Quality |
| Teacher | `rui.teacher@studymatch.com` | Software Engineering, UX Design |
| Student | `50003@students.studymatch.com` | Diogo Student, CS 3rd year, grades from last year, tutor of Programming I with StudyRooms |
| Student | `50006@students.studymatch.com` | Inês Duarte, CS 2nd year, best grades, pending tutor application |
| Student | `50007@students.studymatch.com` | João Pereira, CS 2nd year, failed Programming I, low attendance |
| Student | `50008@students.studymatch.com` | Beatriz Lopes, CS 1st year, working-student statute (no attendance) |
| Student | `50009@students.studymatch.com` | Rafael Matos, CS 1st year, hasn't taken the personality test |
| Student | `50010@students.studymatch.com` | Carolina Reis, CS 1st year, first login (has to change the password) |
| Student | `50011@students.studymatch.com` | Miguel Sousa, Business Management 2nd year |
| Student | `50012@students.studymatch.com` | Leonor Faria, Communication Design 1st year |
| Student | `50013@students.studymatch.com` | Pedro Nunes, CS 2nd year, blocked account |

The student numbers are the ones a fresh database gets. If you registered students before `data.sql` ran, they'll be different; `docker compose down -v` gives you a clean start.

### Useful links

| URL | What |
| --- | --- |
| http://localhost:5173 | the app |
| http://localhost:8080/swagger-ui.html | API docs, every endpoint with its roles and error codes |
| http://localhost:8025 | Mailpit, the emails the backend sent |

### Useful commands

| Where | Command | What it does |
| --- | --- | --- |
| `backend` | `./mvnw spring-boot:run` | starts the API |
| `backend` | `./mvnw test` | runs the tests |
| `backend` | `./mvnw verify` | builds, runs the tests and writes the coverage report to `target/site/jacoco/index.html` |
| `frontend` | `npm run dev` | starts the dev server |
| `frontend` | `npm test` | runs the tests |
| `frontend` | `npm run test:coverage` | runs the tests and writes the coverage report to `coverage/` |
| `frontend` | `npm run lint` | checks the code with ESLint |
| `frontend` | `npm run build` | type-checks and builds for production |

## Documentation

| File | What's in it |
| --- | --- |
| [docs/architecture.md](docs/architecture.md) | the layers from React to PostgreSQL, where the business rules live, login and CSRF |
| [docs/domain.md](docs/domain.md) | the class diagram and the rules it can't show: profile, category levels, cold start, attendance, tutors |
| [docs/conventions.md](docs/conventions.md) | packages and naming, error codes, DTOs vs models, how the frontend is organised |
| [docs/contributing.md](docs/contributing.md) | issues, branches, commits, reviews and the Definition of Done |
| [docs/testing.md](docs/testing.md) | running the tests, the coverage report, and how we write tests |
| [docs/performance.md](docs/performance.md) | the JMeter load test of the student directory, its settings and results |
| [docs/group-matching.md](docs/group-matching.md) | how Generate Groups scores a grouping, the personality rules behind it, and how we picked the algorithm |
| [docs/api.md](docs/api.md) | the endpoints per area and role (the details are in Swagger UI) |

## How we work

Every change starts as an issue and reaches `main` through a reviewed pull request. Use cases are split into a Frontend and a Backend issue. The work happens on its own branch, someone other than the author approves the PR, and the branch is deleted after the merge. A PR that changes behaviour also updates the docs it touches. GitHub Actions builds and tests the backend and the frontend on every pull request and then runs SonarQube on both, and a PR can only be merged when all three checks pass. The details are in [docs/contributing.md](docs/contributing.md).

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
