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
| GET | `/students/me/groups` | Student | the published groups you're in, latest assignment first, with the subject, the assignment and its date, and each member's name and institutional email; blocked members still show, marked `blocked` (GR5) |
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
| GET | `/subject-editions/{id}/assignments/{assignmentId}/groups` | Teacher of the edition | a group assignment's groups, `draft` and `published` apart (both exist until a regeneration is confirmed, empty when there are none), each with its members by name, their class, `attendanceApplicable` (false with a statute, never its type) and `blocked`, and the languages the group shares |
| GET | `/subject-editions/{id}/assignments/{assignmentId}/groups/export?format=&language=` | Teacher of the edition | download the published groups: `format=CSV` is `groups.csv`, one row per student with `groupNumber,studentNumber,name,institutionalEmail` (the columns Import Groups reads), and `format=PDF` is `groups.pdf`, a table per group with its headings in `language` (`en` or `pt`, `en` when left out). Never the statute (GR7). No published groups is `NO_GROUPS_TO_EXPORT` |
| GET | `/subject-editions/{id}/assignments/{assignmentId}/groups/generation` | Teacher of the edition | about what generating will do, from the class sizes alone (no student's evidence is loaded): the students who can be grouped, how many groups, their optimal size and the most the matching algorithm searches (`searchMillis`, 0 when every class is a single group). The page's loader follows it |
| POST | `/subject-editions/{id}/assignments/{assignmentId}/groups?replacePublished=` | Teacher of the edition | generate (or regenerate) a group assignment's groups as drafts and preview them; with published groups already, `replacePublished=true` is needed |
| POST | `/subject-editions/{id}/assignments/{assignmentId}/groups/publish` | Teacher of the edition | publish the drafts, replacing the groups published before |
| DELETE | `/subject-editions/{id}/assignments/{assignmentId}/groups/draft` | Teacher of the edition | discard the drafts, the published groups stay |
| PUT | `/subject-editions/{id}/assignments/{assignmentId}/groups/draft` | Teacher of the edition | save the edited drafts. The body has every group after the edit, as `{ "groups": [{ "id", "members": [studentNumber] }] }`: a group left out is removed, `id: null` adds one, and the groups after a removed one move down a number. Sizes and constraints only warn on the page, but every enrolled student who isn't blocked has to be in exactly one group (`STUDENT_WITHOUT_GROUP` with their `numbers`, `STUDENT_IN_TWO_GROUPS`). No drafts is `NO_DRAFT_GROUPS` |
| PUT | `/subject-editions/{id}/assignments/{assignmentId}/groups/published` | Teacher of the edition | the same for the published groups. A kept group stays the same group, a new one is published straight away. None published yet is `NO_PUBLISHED_GROUPS` |
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
| GET | `/chats/` | Student, Teacher | your chats, the one with the latest message first, each with its name, the other person in a DM, whether a group chat has a picture and when it last changed, the last message and how many you haven't read. A group chat nobody has written in yet has no last message and sits by when it was created |
| GET | `/chats/unread-count` | Student, Teacher | how many messages you haven't read across every chat, for the side menu |
| GET | `/chats/online` | Student, Teacher | the numbers of the people you share a chat with who have the app open, for the chat list's first load |
| GET | `/chats/{id}/messages?before=&after=` | member of the chat | open a chat: its 50 newest messages oldest first, or with `before` (the oldest message id on screen) the 50 before it, or with `after` (the newest on screen, after jumping to an old message) the 50 after it. Both together is `VALIDATION_FAILED`. `reactions` has the reactions to those messages, one per person and message. `hasMore` is false once the first message is in, and `hasNewer` once the latest is. Scrolling up, `hasNewer` is always true, and scrolling down so is `hasMore` |
| GET | `/chats/{id}/messages/search?text=&before=` | member of the chat | the chat's messages whose own text has that text (any capitals, accents count), newest first, 25 at a time. `before` is the oldest result's id, and `hasMore` says if there are more. Fewer than 2 characters, without the spaces around them, is `SEARCH_TOO_SHORT` |
| GET | `/chats/{id}/messages/{messageId}/around` | member of the chat | jump to a search result: that message with up to 25 on each side, in the same shape as a page of the chat. A message that's gone, in another chat or hidden from you is 404 |
| PUT | `/chats/{id}/read` | member of the chat | mark the chat as read up to now (204), when it opens and when a message arrives while it's on screen |
| GET | `/chats/direct/{number}` | Student, Teacher | your DM with that person, `chatId` is null until the first message creates it. With someone you blocked it's `blocked: true` and no `chatId`. Anyone the contact search wouldn't offer is 404 |
| POST | `/chats/{id}/messages` | member of the chat | send a text message (1 to 2000 characters in UTF-16 units, an emoji is usually 2), pushed to the members. With `replyToId` it answers a message of the same chat, and comes back with `replyTo` (its id, author and current text). `REPLIED_MESSAGE_NOT_FOUND` if that message is gone, in another chat or hidden from you. A GIF is `{ "gifId": "..." }` without text: its GIPHY id, up to 64 letters and digits. Messages, and the ones quoted in replies, have that `gifId`, null if they aren't a GIF. Both a text and a GIF, or neither, is `VALIDATION_FAILED` |
| POST | `/chats/{id}/messages` as `multipart/form-data` | member of the chat | the same with 1 to 5 `files` of up to 10 MB each, and then `text` can be left out. Messages, and the ones quoted in replies, have `attachments` with each file's id, name, content type and size in bytes. More than 5 is `TOO_MANY_ATTACHMENTS`. A file can be refused with `ATTACHMENT_TOO_LARGE`, `ATTACHMENT_TYPE_NOT_ALLOWED` or `ATTACHMENT_CONTENT_MISMATCH` (empty, damaged or not what its name says), with its `name` and its `index` in the form, and then nothing is sent. A whole request over 51 MB is `FILE_TOO_LARGE` |
| GET | `/chats/{id}/messages/{messageId}/attachments/{attachmentId}` | member of the chat | a message's file, for `<img src>` or a download link. Images show in the page and anything else downloads. A message that's hidden from you is 404 |
| PUT | `/chats/{id}/messages/{messageId}` | the message's author | change its text (same rule as sending, and it can be empty if the message has attachments, otherwise that's `MESSAGE_EMPTY`), it gets `editedAt` and the open chat gets the new text. The same text again changes nothing. A GIF is `MESSAGE_NOT_EDITABLE`, and someone else's message is `NOT_MESSAGE_AUTHOR` |
| DELETE | `/chats/{id}/messages/{messageId}` | the message's author | delete it for good, nothing is kept and the unread counts go down with it. Deleting the only message of a DM takes the chat off both lists. Someone else's message is `NOT_MESSAGE_AUTHOR` |
| PUT | `/chats/{id}/messages/{messageId}/reaction` | member of the chat | react to a message with `{ "emoji": "❤️" }`, replacing the one you had, pushed to the members who have the chat open. The same emoji again changes nothing. Anything but a single emoji is `INVALID_EMOJI`, and a message that's gone or hidden from you is 404 |
| DELETE | `/chats/{id}/messages/{messageId}/reaction` | member of the chat | take your reaction off (204), without one it changes nothing |
| POST | `/chats/direct/{number}/messages` | Student, Teacher | send a DM, the first one creates the chat. Both sending the first one at the same moment gives the second `DIRECT_CHAT_ALREADY_EXISTS`, and sending again works. To someone you blocked it's `UNBLOCK_TO_MESSAGE`; to someone who blocked you it looks sent but they never get it. It takes `replyToId`, `gifId` and the form with files too |
| POST | `/chats/groups` | Student, Teacher | create a group chat you own with a name (up to 50 characters), an optional picture and 2 to 19 other Students and Teachers, who are in straight away. It comes back as a chat list row (201) and is pushed to every member's list. Fewer people is `NOT_ENOUGH_GROUP_CHAT_MEMBERS`, more is `TOO_MANY_GROUP_CHAT_MEMBERS`, anyone the contact search wouldn't offer is 404, and anyone who blocked you is `CANNOT_ADD_TO_GROUP_CHAT` with their numbers |
| GET | `/chats/{id}/picture` | member of the chat | the group chat's picture as an image for `<img src>`, cached privately for 5 minutes. No picture, or a DM, is 404 |
| GET | `/chats/{id}/members` | member of the chat | who's in it, a group chat in the order they joined with the owner first |
| POST | `/chats/{id}/members` | member of the chat | add Students and Teachers to a group chat, any member can. They're in straight away and get it in their chat lists with the whole history, already read. Anyone already in is skipped, and the members come back. A chat you're not in is 404, and so is anyone the contact search wouldn't offer. A DM is `CHAT_MEMBERS_FIXED`, going past 20 is `TOO_MANY_GROUP_CHAT_MEMBERS`, and anyone who blocked you is `CANNOT_ADD_TO_GROUP_CHAT` |
| DELETE | `/chats/{id}/members/{number}` | the group chat's owner | remove someone, who loses the chat and its history straight away and gets `CHAT_REMOVED`. The members left come back. Someone who isn't in it changes nothing. A chat you're not in is 404, another member is `NOT_CHAT_OWNER`, yourself is `CANNOT_REMOVE_YOURSELF` and a DM is `CHAT_MEMBERS_FIXED` |
| POST | `/chats/{id}/leave` | member of the chat | leave a group chat (204), your other tabs get `CHAT_REMOVED`. The owner leaving makes whoever's been there longest the owner, and the last one leaving deletes the chat with its messages. A chat you're not in is 404 and a DM is `CHAT_MEMBERS_FIXED` |
| PUT | `/chats/{id}/name` | member of the chat | rename a group chat (204), any member can. The name is checked like when creating it, and every member's list gets `CHAT_EDITED`. A chat you're not in is 404, and so is a DM, which has no name of its own |
| PUT | `/chats/{id}/picture` | the group chat's owner | change a group chat's picture (204), `null` takes it off. It's checked like when creating it (`INVALID_GROUP_CHAT_PICTURE`), and every member's list gets `CHAT_EDITED`. Another member is `NOT_CHAT_OWNER`, and a chat you're not in or a DM is 404 |
| GET | `/blocks/` | Student, Teacher | the people you blocked, by name |
| PUT | `/blocks/{number}` | Student, Teacher | block a Student or Teacher's DMs (204, again changes nothing). They aren't told. Yourself is `CANNOT_BLOCK_YOURSELF` |
| DELETE | `/blocks/{number}` | Student, Teacher | unblock (204, someone not blocked changes nothing). The DM comes back without what they sent while blocked |

The live part is the WebSocket at `/ws` (STOMP), open to logged-in Students and Teachers, where the browser subscribes to `/topic/chats/{id}` and `/user/queue/chats/{id}` for a chat it's in and `/user/queue/chats` for its chat list, and sends to `/app/chats/{id}/typing` while the user types. The CONNECT frame needs the `X-XSRF-TOKEN` header. How it works is in [architecture.md](architecture.md#live-updates).

## Analytics

| Method | Path | Who | What it does |
| --- | --- | --- | --- |
| GET | `/groups/analytics?academicYear=&subjectEditionId=&classSection=&groupId=` | Staff | a year's indicators (students, active subjects, average attendance), attendance per month, students by strongest Big Five trait and the published groups' balance, with what each filter offers. Every filter is optional, the year defaults to the current one |

## Notes

| Method | Path | Who | What it does |
| --- | --- | --- | --- |
| GET | `/notes/` | anyone logged in | your sticky notes, oldest change first so the last one touched is on top |
| POST | `/notes/` | anyone logged in | add a note with `text` (up to 500 characters without the spaces around it, can be empty), `colour` (`YELLOW`, `PINK`, `BLUE`, `GREEN`) and `x`, `y` as percentages of the board, 0 to 100 (201). `NOTE_TOO_LONG`, `INVALID_NOTE_POSITION`, and `NOTE_LIMIT_REACHED` past 20 notes |
| PATCH | `/notes/{id}` | the note's owner | change its text, colour or position, only the fields sent change. Someone else's note is `NOT_FOUND`, like one that doesn't exist |
| DELETE | `/notes/{id}` | the note's owner | delete it for good (204). Someone else's note is `NOT_FOUND` |

## Coming next

The other use cases (tutor applications, notifications, and the rest of the chat ones) don't have endpoints yet. Each one is an open Backend issue on the [project board](https://github.com/orgs/Cipher-Pol-UPT/projects/1), and its endpoints go in this file and in Swagger as part of its pull request.
