# Conventions

How the code is organised and named, so a new piece looks like the rest. When in doubt, copy what the nearest similar code does.

## Backend

### Packages

Everything is under `com.studymatch`:

| Package | What goes there |
| --- | --- |
| `controllers` | REST controllers, one per entity |
| `services` | business rules, one per entity |
| `repositories` | Spring Data repositories, one per entity |
| `models` | JPA entities |
| `dtos` | request and response records |
| `enums` | enums shared by models and DTOs |
| `exceptions` | `ErrorCode`, `StudyMatchException`, `GlobalExceptionHandler`, `@ErrorCodes` |
| `security` | Spring Security setup and the account state filter |
| `config` | other Spring configuration (Swagger) |
| `utils` | helpers that don't belong to an entity: CSV readers, `EmailSender` |

### One repository, service and controller per entity

Every entity has its own `XRepository`, `XService` and `XController`, even if some are still empty. A service only uses its own repository. When it needs another entity, it calls that entity's service: `GradeService` asks `EnrolmentService` for the enrolment, it doesn't query `EnrolmentRepository` itself. That way an entity's queries are all in one file, and a service test only mocks one repository.

Two services can't depend on each other. When that would happen, the controller fetches what's needed from one and passes it to the other. For example, `StudentService` already depends on `EnrolmentService`, so to enrol students the controller gets the students from `StudentService` and hands them to `EnrolmentService.enrolStudents`.

That doesn't work when the change has to happen inside the other service's transaction. Then the service publishes a Spring event and the other one listens to it with `@EventListener`. Starting an academic year is the example: `AcademicYearService` publishes `AcademicYearStartedEvent` and `StudentService.clearPersonalityProfiles` deletes the profiles. The listener runs straight away, in the publisher's transaction, so if it fails the whole thing is rolled back. Use an event only for that case; when the controller can pass things along, it should.

An endpoint goes in the controller of the resource in its URL, not the entity it changes. Grading is `PUT /subject-editions/{id}/assignments/{assignmentId}/grades/{studentNumber}`, so it's in `SubjectEditionController`, which calls `GradeService`.

### Naming

| What | How | Example |
| --- | --- | --- |
| Request body | `<Action>RequestDTO` | `CreateAssignmentRequestDTO` |
| Response | `<Thing>ResponseDTO` or `<Thing>DTO` | `AssignmentResponseDTO`, `GradeSheetDTO` |
| DTOs | records, built with a static `from(model)` | `TutorSubjectDTO.from(tutorRole)` |
| URLs | plural nouns, kebab-case, `me` for the logged-in user | `/subject-editions/{id}/students`, `/students/me/profile` |
| Lookups that throw | `getX` | `getEnrolment` throws `STUDENT_NOT_ENROLLED` |
| Lookups that may be empty | `findX` returning `Optional` | `findCurrent` |
| Unit tests | `<Class>Test` | `GradeServiceTest` |
| Integration tests | `<UseCase>IntegrationTest` | `GradeStudentIntegrationTest` |

### Error codes

The backend never sends text meant for people. It sends a code, and the frontend translates it, so the same error reads well in English and in Portuguese.

```java
throw new StudyMatchException(ErrorCode.ASSIGNMENT_WEIGHT_EXCEEDED, Map.of("remaining", 40));
```

`GlobalExceptionHandler` turns that into the code's HTTP status (each `ErrorCode` has one) and this body:

```json
{ "code": "ASSIGNMENT_WEIGHT_EXCEEDED", "params": { "remaining": 40 } }
```

The params fill in the translation (`"... Weight available: {{remaining}}%."` in `en.json`). A failed `@Valid` check gives `VALIDATION_FAILED` with one entry per field, where the code is the annotation's name (`@NotBlank` becomes `NOT_BLANK`) and the params are its limits:

```json
{ "code": "VALIDATION_FAILED", "fieldErrors": [ { "field": "value", "code": "DECIMAL_MAX", "params": { "value": "20", "inclusive": true } } ] }
```

Adding a new code means touching four files, in the same position in each:

1. `backend/.../exceptions/ErrorCode.java`, with its HTTP status
2. `frontend/src/api/errors.ts`, in `ERROR_CODES`
3. `frontend/src/i18n/en.json`, under `errors`
4. `frontend/src/i18n/pt.json`, under `errors`

Lists like these have one item per line with a trailing comma (where the format allows it), so two people adding codes at the same time get a clean merge. The frontend test `tests/contracts.test.ts` fails if `errors.ts` doesn't match `ErrorCode.java` exactly or a code is missing a translation. It does the same for every Java enum and its copy in `frontend/src/types/enums.ts`.

Each endpoint lists the codes it can send with `@ErrorCodes`, next to an `@Operation` summary. The common ones (`UNAUTHENTICATED`, `ACCESS_DENIED`, `VALIDATION_FAILED`, `CSRF_TOKEN_INVALID`) are added for you, so only list the endpoint's own.

### What the DTOs check and what the models check

There are three levels of checks, and each rule goes in the first one that can do it.

The request DTO checks the shape of the input with Bean Validation: required fields, lengths, number ranges, email format. `@Valid` runs these before the controller method, and all the problems come back at once as `fieldErrors`, so a form can mark every wrong field.

The model checks what must always be true about itself, whoever creates or changes it: a StudyRoom ending after it starts, valid group sizes, a statute end date that isn't in the past. These throw a `StudyMatchException` with a specific code.

The service checks what needs the database or other objects: duplicates, the weight left in an edition, whether the student is enrolled.

Sometimes the same limit is in the DTO and in the model (grades from 0 to 20, for example). That's on purpose: the DTO gives the user a field-level message, and the model makes sure nothing else can save a bad value. Data that comes back from the client after a preview, like the student CSV import, is checked again from scratch.

### Code style

- Two-space indentation, lines up to about 120 characters.
- Imports grouped as `java`, `org`, `com.studymatch`, then `io`/`jakarta`, with a blank line between groups.
- `BigDecimal` for weights and anything compared exactly, using `compareTo` (the database returns `1.0000`, and `equals` says that's not `1`).
- Comments are one short line, and only where the name doesn't already say it. They explain why, not what.
- Service methods that write are `@Transactional`; reads that touch lazy relations are `@Transactional(readOnly = true)`.

## Frontend

### Folders

| Folder | What goes there |
| --- | --- |
| `src/api` | one file per area with a function per endpoint, all through `client.ts` (`request`, `upload`, `download`) |
| `src/types` | TypeScript types for the DTOs; `enums.ts` mirrors the Java enums |
| `src/pages/<area>` | pages per role or feature (`staff`, `teacher`, `student`, `login`...). The four role areas each have a `sections.ts` with their navigation |
| `src/components` | components that belong to this app (layout, navigation, loading and error states) |
| `src/studymatch-ui` | the shared component library, see below |
| `src/hooks` | shared hooks |
| `src/i18n` | `en.json` and `pt.json` |
| `tests` | Vitest tests |

Components and pages are PascalCase `.tsx` files; everything else is camelCase.

### studymatch-ui

`src/studymatch-ui` is our component library: buttons, fields, tables, dialogs, badges, charts, themes. Its components get data and permissions through props and report what the user did through callbacks. They don't call the API and have no business rules, so they can be reused on any page and tested on their own. Import them from the folder's `index.ts`:

```tsx
import { Button } from './studymatch-ui';
```

### Loading data

`useRemoteData(key, load)` loads something and returns `{ data, error, loading, retry }`. When `key` changes it loads again, and an answer for an old key is thrown away, so a slow request can't overwrite a newer one. `DataLoadState` shows a spinner while it loads, and if it fails, the translated error with a "try again" button that calls `retry`.

### Text and translations

All text goes through i18next (`t('...')`), never written straight into a component. Every key exists in `en.json` and `pt.json` (European Portuguese). Error codes are under `errors`, field validation codes under `validation`, and each enum has its own namespace (`roles`, `enrolmentStatuses`...). A backend error is shown with:

```tsx
t(`errors.${error.code}`, error.params ?? {})
```

### Lint

`npm run lint` runs ESLint with `--max-warnings 0`, so a warning fails it the same way an error does.
