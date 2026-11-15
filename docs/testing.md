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
