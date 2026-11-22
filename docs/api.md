# API

This is the map of the backend's endpoints. The details (request and response bodies, every error code per endpoint) are in Swagger UI at http://localhost:8080/swagger-ui.html while the backend is running, and the raw OpenAPI JSON is at `/v3/api-docs`.

The backend runs on `http://localhost:8080`; the frontend calls the same paths with `/api` in front. Every endpoint needs a login except the first three under Users. Requests that change something (POST, PUT, PATCH, DELETE) need the `X-XSRF-TOKEN` header, see [architecture.md](architecture.md#csrf).

Errors always come back as `{ "code": ..., "params": ..., "fieldErrors": ... }` with the code's HTTP status, see [conventions.md](conventions.md#error-codes). A logged-in user who is blocked, hasn't changed their first password or hasn't taken this year's personality test gets 403 on almost everything, see [architecture.md](architecture.md#account-state).

"Logged in" in the tables means any role.

## Users

| Method | Path | Who | What it does |
| --- | --- | --- | --- |
| POST | `/users/login` | anyone | log in with the institutional email, starts the session |
| POST | `/users/forgot-password` | anyone | email a reset code to the personal email (always 204) |
| POST | `/users/reset-password` | anyone | set a new password with the reset code |
| POST | `/users/logout` | logged in | end the session (204) |
| GET | `/users/me` | logged in | who's logged in, and whether they still have to change their password or take the personality test |
| PUT | `/users/change-password` | logged in | change your own password |
| GET | `/users/chat-contacts?search=&page=` | Student, Teacher | who you can message: Students and Teachers that aren't blocked, by name, institutional email or number, 20 per page, without yourself |
| POST | `/users/` | Admin | create a Staff or Teacher account |
| GET | `/users/` | Admin | search accounts of every role by name, institutional email or number, 20 per page, optionally by status |
| GET | `/users/{number}/block-impact` | Admin | what blocking would cancel, and a teacher's editions this year that need another teacher |
| PUT | `/users/{number}/block` | Admin | block an account with a reason, cancelling its upcoming StudyRooms and pending tutor applications |
| PUT | `/users/{number}/unblock` | Admin | unblock an account so it can log in again, what the block cancelled stays cancelled |

## Students

| Method | Path | Who | What it does |
| --- | --- | --- | --- |
| GET | `/students/` | Staff | search students, 20 per page, filtered by name or number, degree, year and account status |
| GET | `/students/{number}` | Staff | a student's full record: profile, average grade, category levels, enrolments |
| PUT | `/students/{number}` | Staff | edit name, personal email, degree and curricular year |
| POST | `/students/` | Staff | register one student and email their credentials |
| POST | `/students/{number}/resend-credentials` | Staff | new one-time password by email, optionally to a corrected address |
| PUT | `/students/{number}/statute` | Staff | add a special statute (type and end date) or replace the current one |
| DELETE | `/students/{number}/statute` | Staff | end the student's statute |
| GET | `/students/import/template` | Staff | the empty CSV for importing students |
| POST | `/students/import/preview` | Staff | check a CSV row by row without saving anything |
| POST | `/students/import` | Staff | create the previewed rows |
| GET | `/students/me/profile` | Student | your own profile |
| PUT | `/students/me/profile` | Student | edit your banner, photo, about me, skills, interests and languages |
| GET | `/students/{number}/profile` | logged in | a student's profile, with the private part only for them, Staff and their teachers |
| GET | `/students/{number}/pfp` | logged in | just the profile photo, as an image for `<img src>` (cached for 5 minutes), 404 without one |
| POST | `/students/personality-test` | Student | send the 20 Mini-IPIP answers, once per academic year |

## Degrees, categories, subjects and academic years

| Method | Path | Who | What it does |
| --- | --- | --- | --- |
| GET | `/degrees/`, `/degrees/{id}` | logged in | list degrees, or one |
| POST | `/degrees/` | Staff | register a degree |
| PUT | `/degrees/{id}` | Staff | rename a degree, its students and subjects stay linked |
| GET | `/categories/`, `/categories/{id}` | logged in | list categories, or one |
| POST | `/categories/` | Staff | register a category |
| PUT | `/categories/{id}` | Staff | rename a category, its subjects (and so the students' levels in it) stay linked |
| GET | `/subjects/`, `/subjects/{id}` | logged in | list subjects with their category and degrees, or one |
| POST | `/subjects/` | Staff | register a subject and its first edition |
| PUT | `/subjects/{id}` | Staff | change name and ECTS (code and category stay), and optionally this year's edition's teachers, keeping at least one |
| POST | `/subjects/{id}/editions` | Staff | add an edition of an existing subject for another year |
| GET | `/subjects/{id}/editions/current` | Staff | the subject's edition this year with its teachers, to edit them |
| GET | `/academic-years/` | logged in | academic years, newest (the current one) first |
| GET | `/academic-years/next` | Staff | the year Start Academic Year would create, and the current year's enrolments with no final grade |
| POST | `/academic-years/` | Staff | start the next academic year (the body repeats its start year), everyone retakes the personality test |
| GET | `/teachers/` | Staff | every teacher, to pick an edition's teachers |

## Subject editions

| Method | Path | Who | What it does |
| --- | --- | --- | --- |
| GET | `/subject-editions/` | Teacher | the editions you teach |
| GET | `/subject-editions/current` | Staff | this year's editions, the ones open for enrolment |
| POST | `/subject-editions/{id}/enrolments` | Staff | enrol students by number in one class, with a result per student |
| PUT | `/subject-editions/{id}/enrolments/{studentNumber}/class-section` | Staff | move an enrolled student to another class of the edition, keeping their grades and absences |
| GET | `/subject-editions/enrolments/import/template` | Staff | the empty CSV for enrolments |
| POST | `/subject-editions/{id}/enrolments/import` | Staff | enrol the students in a CSV, with a result per row |
| GET | `/subject-editions/{id}/students` | Teacher of the edition | its students, with personality, category level and attendance |
| POST | `/subject-editions/{id}/assignments` | Teacher of the edition | add an assignment, with group sizes and separations for group work |
| GET | `/subject-editions/{id}/grades` | Teacher of the edition | the grade sheet; each assignment says whether it's group work (`groupWork`, it has a grouping rule) |
| PUT | `/subject-editions/{id}/assignments/{assignmentId}/grades/{studentNumber}` | Teacher of the edition | give or change a grade; sets the final grade once everything's graded |
| GET | `/subject-editions/{id}/assignments/{assignmentId}/groups/generation` | Teacher of the edition | about what generating will do, from the class sizes alone (no student's evidence is loaded): the students who can be grouped, how many groups, their optimal size and the most the matching algorithm searches (`searchMillis`, 0 when every class is a single group). The page's loader follows it |
| POST | `/subject-editions/{id}/assignments/{assignmentId}/groups?replacePublished=` | Teacher of the edition | generate (or regenerate) a group assignment's groups as drafts and preview them; with published groups already, `replacePublished=true` is needed |
| POST | `/subject-editions/{id}/assignments/{assignmentId}/groups/publish` | Teacher of the edition | publish the drafts, replacing the groups published before |
| DELETE | `/subject-editions/{id}/assignments/{assignmentId}/groups/draft` | Teacher of the edition | discard the drafts, the published groups stay |
| GET | `/subject-editions/{id}/attendance?classSection=&startsAt=` | Staff | one lesson's attendance sheet, with the saved record if there is one |
| PUT | `/subject-editions/{id}/attendance` | Staff | register or correct a lesson's absences (current year only) |
| DELETE | `/subject-editions/{id}/attendance?classSection=&startsAt=` | Staff | delete a lesson registered by mistake, with its absences (current year only) |

## Tutors and StudyRooms

| Method | Path | Who | What it does |
| --- | --- | --- | --- |
| GET | `/tutors/me/subjects` | Student | the subjects you tutor |
| GET | `/tutors/me/applicable-subjects` | Student | the subjects you passed, with your grade, position and whether you can apply to tutor them |
| POST | `/tutor-applications/` | Student in the subject's top 5% | apply to tutor a subject, creating a Pending application |
| GET | `/tutor-applications/?subjectId=` | Teacher of the subject's current edition | the Pending applications, with the applicant's grade, rank and previous ratings |
| POST | `/tutor-applications/{id}/approve` | Teacher of the subject's current edition | approve the application, making the student the subject's tutor |
| POST | `/tutor-applications/{id}/reject` | Teacher of the subject's current edition | reject the application, with an optional reason |
| POST | `/study-rooms/` | Student who tutors the subject | create a StudyRoom, with the tutor's Meet link or, without one, a link the server creates on Google |
| PUT | `/study-rooms/{id}` | the room's tutor | edit a StudyRoom before it starts (the subject can't change), with the same time, overlap and capacity rules as creating |
| GET | `/study-rooms/` | Student | the open StudyRooms (not cancelled, not completed), filtered by subject, tutor, topic or date |
| GET | `/study-rooms/{id}` | Student | one StudyRoom's description, the tutor's rating in that subject, and whether you can join |
| GET | `/study-rooms/{id}/participants` | the room's tutor | who joined, and whether they attended |
| POST | `/study-rooms/{id}/attendance` | the room's tutor | confirm who attended once the room ended, within 48 hours |
| POST | `/study-rooms/{id}/rating` | Student who attended | rate the tutor, 1 to 5 stars and an optional comment, once per room within 7 days |
| POST | `/study-rooms/{id}/join` | Student enrolled in the subject | join an open StudyRoom, becoming a participant |
| POST | `/study-rooms/{id}/leave` | Student who joined | leave a StudyRoom before it starts, freeing the place |
| POST | `/study-rooms/{id}/cancel` | the room's tutor | cancel a StudyRoom before it starts, with an optional reason |
| GET | `/students/{number}/ratings` | Anyone logged in | a tutor's ratings and comments, newest first, without who wrote them |

## Achievements

| Method | Path | Who | What it does |
| --- | --- | --- | --- |
| GET | `/achievements/me` | Student | all four achievements, with the date and verification code of the ones you've earned |
| POST | `/achievements/certificate` | Student | the PDF certificate of the achievements chosen (one achievement, or a record with several), in English or Portuguese |
| GET | `/achievements/certificates/{code}` | Anyone, no login | what a certificate's QR code checks: who earned which achievements and when |

## Chats

| Method | Path | Who | What it does |
| --- | --- | --- | --- |
| GET | `/chats/` | Student, Teacher | your chats, the one with the latest message first, each with its name, the other person in a DM, the last message and how many you haven't read |
| GET | `/chats/unread-count` | Student, Teacher | how many messages you haven't read across every chat, for the side menu |
| GET | `/chats/{id}/messages?before=` | member of the chat | open a chat: its 50 newest messages oldest first, or with `before` (the oldest message id on screen) the 50 before it. `hasMore` is false once the first message is in |
| PUT | `/chats/{id}/read` | member of the chat | mark the chat as read up to now (204), when it opens and when a message arrives while it's on screen |
| GET | `/chats/direct/{number}` | Student, Teacher | your DM with that person, `chatId` is null until the first message creates it. With someone you blocked it's `blocked: true` and no `chatId`. Anyone the contact search wouldn't offer is 404 |
| POST | `/chats/{id}/messages` | member of the chat | send a text message (1 to 2000 characters in UTF-16 units, an emoji is usually 2), pushed to the members |
| POST | `/chats/direct/{number}/messages` | Student, Teacher | send a DM, the first one creates the chat. Both sending the first one at the same moment gives the second `DIRECT_CHAT_ALREADY_EXISTS`, and sending again works. To someone you blocked it's `UNBLOCK_TO_MESSAGE`; to someone who blocked you it looks sent but they never get it |
| GET | `/blocks/` | Student, Teacher | the people you blocked, by name |
| PUT | `/blocks/{number}` | Student, Teacher | block a Student or Teacher's DMs (204, again changes nothing). They aren't told. Yourself is `CANNOT_BLOCK_YOURSELF` |
| DELETE | `/blocks/{number}` | Student, Teacher | unblock (204, someone not blocked changes nothing). The DM comes back without what they sent while blocked |

The live part is the WebSocket at `/ws` (STOMP), open to logged-in Students and Teachers, where the browser subscribes to `/topic/chats/{id}` and `/user/queue/chats/{id}` for a chat it's in and `/user/queue/chats` for its chat list. The CONNECT frame needs the `X-XSRF-TOKEN` header. How it works is in [architecture.md](architecture.md#live-updates).

## Analytics

| Method | Path | Who | What it does |
| --- | --- | --- | --- |
| GET | `/analytics/?academicYear=&subjectEditionId=&classSection=&groupId=` | Staff | a year's indicators (students, active subjects, average attendance), attendance per month, students by strongest Big Five trait and the published groups' balance, with what each filter offers. Every filter is optional, the year defaults to the current one |

## Coming next

The other use cases (editing and viewing groups, tutor applications, notifications, and the rest of the chat ones) don't have endpoints yet. Each one is an open Backend issue on the [project board](https://github.com/orgs/Cipher-Pol-UPT/projects/1), and its endpoints go in this file and in Swagger as part of its pull request.
