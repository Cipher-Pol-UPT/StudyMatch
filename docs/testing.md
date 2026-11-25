# Testing

## Running the tests

### Backend

The integration tests use the real PostgreSQL from Docker, so start it first:

```bash
cd backend
docker compose up -d
./mvnw test                          # every test
./mvnw test -Dtest=GradeServiceTest  # one class
./mvnw verify                        # tests + coverage report
```

After `./mvnw verify`, open `backend/target/site/jacoco/index.html` in a browser. It shows line and branch coverage per package and class, and inside a class it colours each line: green is covered, red isn't, yellow is a branch where only some of the cases ran.

The tests run with the `test` profile, which doesn't load `data.sql`. Each integration test rolls back at the end, so nothing it creates stays in your database. Your demo data is still there while the tests run, though, so tests use values that can't clash with it: academic years like 2089 or 2099, and emails like `it.grade.teacher@studymatch.com`.

When this was written (Sprint 3, PR for #155) there were 942 tests, with 98% instruction and 96% branch coverage. What's left uncovered is mostly getters and setters on the models and `main()`.

### Frontend

```bash
cd frontend
npm test        # Vitest, once
npx vitest      # Vitest in watch mode, reruns on save
```

The tests are in `frontend/tests` and run in jsdom, a fake browser. They never call the real backend: each test replaces `fetch` with `vi.stubGlobal` and decides what the "backend" answers. `tests/contracts.test.ts` is the exception; it reads the backend's `ErrorCode.java` and enums from disk to check the frontend's copies match.

### In CI

GitHub Actions runs the same commands on every pull request and every push to `main`, on a clean machine:

Everything is in `.github/workflows/ci.yml`, as three jobs (each one is a check on the PR), plus a short one that finds what the PR changed:

| Job | What it runs |
| --- | --- |
| Backend build and tests | `./mvnw -B verify`, with a PostgreSQL 17 container next to it (same database and user as `docker-compose.yml`, with no password check since it only exists inside the job) |
| Frontend lint, tests and build | `npm ci --ignore-scripts`, `npm run lint`, `npm run test:coverage` and `npm run build` |
| SonarQube analysis | starts once the other two passed, takes their coverage reports and compiled classes, and sends the whole repo to SonarQube |

A PR that only touches `frontend/`, `docs/`, `performance/` or the README skips the backend job, since it can't break it. The frontend job always runs, because `contracts.test.ts` reads the backend's code, and a push to `main` always runs everything. A PR doesn't have to be up to date with `main` to be merged, so `main`'s own run is what catches two PRs that only break together.

The backend tests hash passwords with bcrypt's lowest strength (`FastPasswordConfig`, in the test profile only), since the real one was most of their time.

The backend and frontend checks have to pass before a PR can be merged (a skipped one counts as passed). Each backend run keeps the JaCoCo report as an artifact called `jacoco-report`; download it from the bottom of the run's page on the Actions tab. When a backend run fails, it also keeps the Surefire test reports (`surefire-reports`), which show which test broke and why.

### SonarQube

The SonarQube job sends the whole repo (backend and frontend) to [SonarQube Cloud](https://sonarcloud.io/project/overview?id=Cipher-Pol-UPT_StudyMatch), with the JaCoCo report from the backend job and the Vitest `lcov.info` from the frontend job. When the backend job is skipped it compiles the classes itself. The settings are in `sonar-project.properties` at the root.

On a pull request, SonarQube only judges the code the PR changes, against the default quality gate (Sonar way). The new code needs at least 80% coverage, at most 3% duplicated lines, an A for reliability, security and maintainability (so no new bugs or vulnerabilities), and every new security hotspot reviewed. It comments on the PR with the result. If the gate fails, the SonarQube check goes red and the PR can't be merged. Security hotspots have to be marked as reviewed on SonarQube Cloud; whoever's PR adds one reviews it.

On a push to `main`, it updates the full analysis of the project, which is where the numbers for the quality report come from.

Coverage counts the backend and the frontend, except `frontend/src/studymatch-ui`. That's our component library: the pages that use it are what we test, and it's left out of the coverage numbers on purpose. It's still analysed for bugs, code smells, duplication and security. To see the frontend coverage locally, run `npm run test:coverage` and open `frontend/coverage/lcov-report/index.html`. When this was written it was 94% of lines and 90% of branches.

The tests themselves (`backend/src/test` and `frontend/tests`) aren't analysed. The scanner ignores `sonar.tests`, so without excluding them it treated test code as production code: every new test counted as uncovered code, shared test setup counted as duplication, and the assertions were reported as bugs. Their coverage still shows up, because it comes from the JaCoCo and Vitest reports.

## How we write backend tests

### Structure

Every test follows Arrange, Act, Assert, with a comment for each part (`// Act + Assert` when it's one line). The `@DisplayName` is a sentence that states the rule, so a failing test reads like a broken rule:

```java
@ParameterizedTest(name = "{0} and {1} -> {2} {3}")
@CsvSource({ "9.5, 9.5, 10, PASSED", "9.49, 9.49, 9, FAILED", "20, 20, 20, PASSED", "0, 0, 0, FAILED" })
@DisplayName("The final grade is rounded half up, so 9.5 passes and 9.49 doesn't")
void finalGradeRounding(String projectGrade, String examGrade, int finalGrade, EnrolmentStatus status) {
  // Arrange
  grade(project, projectGrade);

  // Act
  GradeResponseDTO response = grade(exam, examGrade);

  // Assert
  assertEquals(finalGrade, response.student().finalGrade());
  assertEquals(status, response.student().status());
}
```

(from `GradeServiceTest`, where `grade` is a small helper that calls `gradeService.gradeStudent`)

We use JUnit 5's own `Assertions` (`assertEquals`, `assertThrows`, `assertTrue`...). For an expected error, check the code, and the params when there are any:

```java
StudyMatchException e = assertThrows(StudyMatchException.class, () -> enrolmentService.getEnrolment(7L, 1001L));
assertEquals(ErrorCode.STUDENT_NOT_ENROLLED, e.getCode());
assertEquals(Map.of("number", 1001L), e.getParams());
```

### Boundaries

Every limit gets tested on both sides. A grade from 0 to 20 with two decimals is tested with -0.01, 0, 20, 20.01, and with 2 and 3 decimals. The final grade's rounding is tested at 9.49 and 9.5. Group sizes are tested at the minimum of 2 and one below it. If a rule says "at most", there's a test exactly at the limit and one just past it.

### What each layer's tests look like

| What | How | Example |
| --- | --- | --- |
| Models | plain JUnit, no Spring. Build the object, check the rule it enforces | `StudyRoomTest`, `PersonalityProfileTest` |
| Request DTOs | a real Bean Validation `Validator`, checking which annotation failed | `GradeRequestDTOTest` |
| Services | Mockito mocks for the repository and the other services it uses, since those are its dependencies. Nothing else gets mocked | `GradeServiceTest` |
| Security and error handling | unit tests for the filter and the error writer, plus `SecurityIntegrationTest` for the whole chain | `AccountStateFilterTest` |
| Use cases end to end | `@SpringBootTest` + `@AutoConfigureMockMvc` + `@ActiveProfiles("test")` + `@Transactional`, real HTTP calls through MockMvc against the real database | `GradeStudentIntegrationTest` |

Model objects for unit tests come from `ModelTestData` (`ModelTestData.student("Ana")`, `ModelTestData.subjectEdition()`). All test data is made up; emails use `example.com` or the `studymatch.com` test domain.

### What an integration test covers

Each use case gets one integration test class, which logs in through `POST /users/login` like the frontend does and checks:

- the main scenario: the status, the response body, and what ended up in the database
- the main errors, by their code (`jsonPath("$.code").value("STUDENT_NOT_ENROLLED")`)
- who isn't allowed: the wrong role gets 403, a teacher of another edition gets 403, and nobody logged in gets 401

Requests that change something need `.with(csrf())`, the same way the real frontend sends the token.

## How we write frontend tests

The tests render a page inside `StudyMatchProvider`, stub `fetch` with the backend answer the case needs, act like a user would (`fireEvent`, typing, clicking), and check what's on screen with Testing Library's `screen` queries. Look things up by role or visible text, not by CSS class, so restyling a page doesn't break its tests.
