-- StudyMatch demo data. None of these accounts are real, wipe the database before going live.
-- Runs on every startup (after Hibernate creates the tables), so every insert skips rows
-- that already exist. Users are identified by their personal email.
--
-- All demo accounts use the password: Demo1234
--   Admin    ana.admin@studymatch.com
--   Staff    sofia.staff@studymatch.com
--   Teacher  tiago.teacher@studymatch.com   Programming I/II, Databases, Software Quality (+ Inês' tutor application)
--   Teacher  marta.teacher@studymatch.com   Calculus I, Statistics, Project Management, Software Quality
--   Teacher  rui.teacher@studymatch.com     Software Engineering, UX Design
--   Students <number>@students.studymatch.com, numbers on a fresh database:
--     50003  Diogo Student     CS 3rd year, grades from last year, tutor of Programming I with study rooms
--     50006  Inês Duarte       CS 2nd year, best grades, pending tutor application
--     50007  João Pereira      CS 2nd year, failed Programming I, low attendance
--     50008  Beatriz Lopes     CS 1st year, working student statute (no attendance)
--     50009  Rafael Matos      CS 1st year, hasn't taken the personality test
--     50010  Carolina Reis     CS 1st year, first login (has to change the password)
--     50011  Miguel Sousa      Business Management 2nd year
--     50012  Leonor Faria      Communication Design 1st year
--     50013  Pedro Nunes       CS 2nd year, blocked account
--
-- Numbers come from user_sequence, the same counter the app uses, so real users
-- registered later continue after them. Lessons are only seeded in the past, since
-- attendance counts every lesson of the class.

-- degrees and categories

INSERT INTO degree (name, curricular_years) VALUES
  ('Computer Science', 3),
  ('Business Management', 3),
  ('Communication Design', 3),
  ('Data Science', 2)
ON CONFLICT (name) DO NOTHING;

INSERT INTO category (name) VALUES
  ('Programming'),
  ('Mathematics'),
  ('Project Management'),
  ('Design')
ON CONFLICT (name) DO NOTHING;

-- subjects and where they are in each degree

INSERT INTO subject (code, name, ects, category_id)
SELECT v.code, v.name, v.ects, c.id
FROM (VALUES
  ('PROG1', 'Programming I', 6, 'Programming'),
  ('PROG2', 'Programming II', 6, 'Programming'),
  ('DB', 'Databases', 6, 'Programming'),
  ('MATH1', 'Calculus I', 6, 'Mathematics'),
  ('STAT', 'Statistics', 5, 'Mathematics'),
  ('SE', 'Software Engineering', 6, 'Project Management'),
  ('SQ', 'Software Quality', 6, 'Project Management'),
  ('PM', 'Project Management', 5, 'Project Management'),
  ('UX', 'UX Design', 6, 'Design')
) AS v(code, name, ects, category)
JOIN category c ON c.name = v.category
ON CONFLICT (code) DO NOTHING;

INSERT INTO degree_subject (degree_id, subject_id, curricular_year, semester)
SELECT d.id, s.id, v.curricular_year, v.semester
FROM (VALUES
  ('Computer Science', 'PROG1', 1, 1),
  ('Computer Science', 'MATH1', 1, 1),
  ('Computer Science', 'PROG2', 1, 2),
  ('Computer Science', 'DB', 2, 1),
  ('Computer Science', 'STAT', 2, 1),
  ('Computer Science', 'SE', 2, 2),
  ('Computer Science', 'SQ', 3, 1),
  ('Computer Science', 'UX', 3, 1),
  ('Business Management', 'STAT', 1, 2),
  ('Business Management', 'PM', 2, 1),
  ('Communication Design', 'UX', 1, 1),
  ('Data Science', 'STAT', 1, 1),
  ('Data Science', 'DB', 1, 2)
) AS v(degree, code, curricular_year, semester)
JOIN degree d ON d.name = v.degree
JOIN subject s ON s.code = v.code
ON CONFLICT (degree_id, subject_id) DO NOTHING;

-- accounts (students get their institutional email from their number further down)

INSERT INTO users (number, name, personal_email, institutional_email, password, account_status, must_change_password)
SELECT nextval('user_sequence'), v.name, v.personal_email, v.institutional_email,
  '$2a$10$ICLQF5.jMDjljQFJVzahlOjGMsXMhpwXsrYnjrL80I6APoOWCs6mO', v.account_status, v.must_change_password
FROM (VALUES
  (1, 'Ana Admin', 'ana.admin@example.com', 'ana.admin@studymatch.com', 'ACTIVE', false),
  (2, 'Sofia Staff', 'sofia.staff@example.com', 'sofia.staff@studymatch.com', 'ACTIVE', false),
  (3, 'Tiago Teacher', 'tiago.teacher@example.com', 'tiago.teacher@studymatch.com', 'ACTIVE', false),
  (4, 'Diogo Student', 'diogo.student@example.com', NULL, 'ACTIVE', false),
  (5, 'Marta Teacher', 'marta.teacher@example.com', 'marta.teacher@studymatch.com', 'ACTIVE', false),
  (6, 'Rui Teacher', 'rui.teacher@example.com', 'rui.teacher@studymatch.com', 'ACTIVE', false),
  (7, 'Inês Duarte', 'ines.duarte@example.com', NULL, 'ACTIVE', false),
  (8, 'João Pereira', 'joao.pereira@example.com', NULL, 'ACTIVE', false),
  (9, 'Beatriz Lopes', 'beatriz.lopes@example.com', NULL, 'ACTIVE', false),
  (10, 'Rafael Matos', 'rafael.matos@example.com', NULL, 'ACTIVE', false),
  (11, 'Carolina Reis', 'carolina.reis@example.com', NULL, 'ACTIVE', true),
  (12, 'Miguel Sousa', 'miguel.sousa@example.com', NULL, 'ACTIVE', false),
  (13, 'Leonor Faria', 'leonor.faria@example.com', NULL, 'ACTIVE', false),
  (14, 'Pedro Nunes', 'pedro.nunes@example.com', NULL, 'BLOCKED', false)
) AS v(position, name, personal_email, institutional_email, account_status, must_change_password)
WHERE NOT EXISTS (SELECT 1 FROM users u WHERE u.personal_email = v.personal_email)
ORDER BY v.position;

INSERT INTO admin (number)
SELECT number FROM users WHERE personal_email = 'ana.admin@example.com'
ON CONFLICT (number) DO NOTHING;

INSERT INTO staff (number)
SELECT number FROM users WHERE personal_email = 'sofia.staff@example.com'
ON CONFLICT (number) DO NOTHING;

INSERT INTO teacher (number)
SELECT number FROM users
WHERE personal_email IN ('tiago.teacher@example.com', 'marta.teacher@example.com', 'rui.teacher@example.com')
ON CONFLICT (number) DO NOTHING;

INSERT INTO student (number, degree_id, curricular_year, statute_type, statute_end_date, about_me)
SELECT u.number, d.id, v.curricular_year, v.statute_type, CAST(v.statute_end_date AS date), v.about_me
FROM (VALUES
  ('diogo.student@example.com', 'Computer Science', 3, NULL, NULL, 'Third year, happy to help with Programming I.'),
  ('ines.duarte@example.com', 'Computer Science', 2, NULL, NULL, 'I like clean code and long walks through stack traces.'),
  ('joao.pereira@example.com', 'Computer Science', 2, NULL, NULL, NULL),
  ('beatriz.lopes@example.com', 'Computer Science', 1, 'WORKING_STUDENT', '2027-07-31', 'Working part-time, studying at night.'),
  ('rafael.matos@example.com', 'Computer Science', 1, NULL, NULL, NULL),
  ('carolina.reis@example.com', 'Computer Science', 1, NULL, NULL, NULL),
  ('miguel.sousa@example.com', 'Business Management', 2, NULL, NULL, 'Spreadsheets are my love language.'),
  ('leonor.faria@example.com', 'Communication Design', 1, NULL, NULL, 'Illustration, typography and too many fonts.'),
  ('pedro.nunes@example.com', 'Computer Science', 2, NULL, NULL, NULL)
) AS v(personal_email, degree, curricular_year, statute_type, statute_end_date, about_me)
JOIN users u ON u.personal_email = v.personal_email
JOIN degree d ON d.name = v.degree
ON CONFLICT (number) DO NOTHING;

UPDATE users SET institutional_email = number || '@students.studymatch.com'
WHERE institutional_email IS NULL AND number IN (SELECT number FROM student);

-- profile details

INSERT INTO student_skills (student_number, skills)
SELECT u.number, v.skill
FROM (VALUES
  ('diogo.student@example.com', 'Java'),
  ('diogo.student@example.com', 'Spring Boot'),
  ('diogo.student@example.com', 'React'),
  ('ines.duarte@example.com', 'Python'),
  ('ines.duarte@example.com', 'SQL'),
  ('ines.duarte@example.com', 'TypeScript'),
  ('beatriz.lopes@example.com', 'Excel'),
  ('miguel.sousa@example.com', 'Excel'),
  ('miguel.sousa@example.com', 'Public speaking'),
  ('leonor.faria@example.com', 'Figma'),
  ('leonor.faria@example.com', 'Illustrator')
) AS v(personal_email, skill)
JOIN users u ON u.personal_email = v.personal_email
ON CONFLICT DO NOTHING;

INSERT INTO student_interests (student_number, interests)
SELECT u.number, v.interest
FROM (VALUES
  ('diogo.student@example.com', 'Games'),
  ('diogo.student@example.com', 'Open source'),
  ('ines.duarte@example.com', 'Machine learning'),
  ('ines.duarte@example.com', 'Chess'),
  ('miguel.sousa@example.com', 'Startups'),
  ('leonor.faria@example.com', 'Photography')
) AS v(personal_email, interest)
JOIN users u ON u.personal_email = v.personal_email
ON CONFLICT DO NOTHING;

INSERT INTO student_languages (student_number, languages)
SELECT u.number, v.language
FROM (VALUES
  ('diogo.student@example.com', 'PORTUGUESE'),
  ('diogo.student@example.com', 'ENGLISH'),
  ('ines.duarte@example.com', 'PORTUGUESE'),
  ('ines.duarte@example.com', 'ENGLISH'),
  ('ines.duarte@example.com', 'SPANISH'),
  ('joao.pereira@example.com', 'PORTUGUESE'),
  ('beatriz.lopes@example.com', 'PORTUGUESE'),
  ('miguel.sousa@example.com', 'PORTUGUESE'),
  ('miguel.sousa@example.com', 'FRENCH'),
  ('leonor.faria@example.com', 'PORTUGUESE'),
  ('leonor.faria@example.com', 'ENGLISH')
) AS v(personal_email, language)
JOIN users u ON u.personal_email = v.personal_email
ON CONFLICT DO NOTHING;

-- personality profiles: one statement per student, since each profile row belongs to one student.
-- Rafael and Carolina have none, so they get the personality test.

WITH profile AS (
  INSERT INTO personality_profile (leadership, problem_solving, creativity, execution)
  SELECT 0.7, 0.6, 0.8, 0.5 FROM student s JOIN users u ON u.number = s.number
  WHERE u.personal_email = 'diogo.student@example.com' AND s.personality_profile_id IS NULL
  RETURNING id)
UPDATE student SET personality_profile_id = (SELECT id FROM profile)
WHERE number = (SELECT number FROM users WHERE personal_email = 'diogo.student@example.com') AND personality_profile_id IS NULL;

WITH profile AS (
  INSERT INTO personality_profile (leadership, problem_solving, creativity, execution)
  SELECT 0.55, 0.9, 0.6, 0.85 FROM student s JOIN users u ON u.number = s.number
  WHERE u.personal_email = 'ines.duarte@example.com' AND s.personality_profile_id IS NULL
  RETURNING id)
UPDATE student SET personality_profile_id = (SELECT id FROM profile)
WHERE number = (SELECT number FROM users WHERE personal_email = 'ines.duarte@example.com') AND personality_profile_id IS NULL;

WITH profile AS (
  INSERT INTO personality_profile (leadership, problem_solving, creativity, execution)
  SELECT 0.4, 0.5, 0.75, 0.3 FROM student s JOIN users u ON u.number = s.number
  WHERE u.personal_email = 'joao.pereira@example.com' AND s.personality_profile_id IS NULL
  RETURNING id)
UPDATE student SET personality_profile_id = (SELECT id FROM profile)
WHERE number = (SELECT number FROM users WHERE personal_email = 'joao.pereira@example.com') AND personality_profile_id IS NULL;

WITH profile AS (
  INSERT INTO personality_profile (leadership, problem_solving, creativity, execution)
  SELECT 0.65, 0.45, 0.4, 0.9 FROM student s JOIN users u ON u.number = s.number
  WHERE u.personal_email = 'beatriz.lopes@example.com' AND s.personality_profile_id IS NULL
  RETURNING id)
UPDATE student SET personality_profile_id = (SELECT id FROM profile)
WHERE number = (SELECT number FROM users WHERE personal_email = 'beatriz.lopes@example.com') AND personality_profile_id IS NULL;

WITH profile AS (
  INSERT INTO personality_profile (leadership, problem_solving, creativity, execution)
  SELECT 0.9, 0.5, 0.55, 0.7 FROM student s JOIN users u ON u.number = s.number
  WHERE u.personal_email = 'miguel.sousa@example.com' AND s.personality_profile_id IS NULL
  RETURNING id)
UPDATE student SET personality_profile_id = (SELECT id FROM profile)
WHERE number = (SELECT number FROM users WHERE personal_email = 'miguel.sousa@example.com') AND personality_profile_id IS NULL;

WITH profile AS (
  INSERT INTO personality_profile (leadership, problem_solving, creativity, execution)
  SELECT 0.35, 0.55, 0.95, 0.6 FROM student s JOIN users u ON u.number = s.number
  WHERE u.personal_email = 'leonor.faria@example.com' AND s.personality_profile_id IS NULL
  RETURNING id)
UPDATE student SET personality_profile_id = (SELECT id FROM profile)
WHERE number = (SELECT number FROM users WHERE personal_email = 'leonor.faria@example.com') AND personality_profile_id IS NULL;

WITH profile AS (
  INSERT INTO personality_profile (leadership, problem_solving, creativity, execution)
  SELECT 0.5, 0.6, 0.5, 0.45 FROM student s JOIN users u ON u.number = s.number
  WHERE u.personal_email = 'pedro.nunes@example.com' AND s.personality_profile_id IS NULL
  RETURNING id)
UPDATE student SET personality_profile_id = (SELECT id FROM profile)
WHERE number = (SELECT number FROM users WHERE personal_email = 'pedro.nunes@example.com') AND personality_profile_id IS NULL;

-- academic years: 2025 is finished (grades), 2026 is the current one

INSERT INTO academic_year (start_year) VALUES (2025), (2026)
ON CONFLICT (start_year) DO NOTHING;

INSERT INTO subject_edition (subject_id, academic_year)
SELECT s.id, v.academic_year
FROM (VALUES
  ('PROG1', 2025),
  ('MATH1', 2025),
  ('PROG2', 2025),
  ('STAT', 2025),
  ('PROG1', 2026),
  ('MATH1', 2026),
  ('DB', 2026),
  ('STAT', 2026),
  ('SE', 2026),
  ('SQ', 2026),
  ('UX', 2026),
  ('PM', 2026)
) AS v(code, academic_year)
JOIN subject s ON s.code = v.code
ON CONFLICT (subject_id, academic_year) DO NOTHING;

INSERT INTO subject_edition_teachers (subject_edition_id, teachers_number)
SELECT e.id, u.number
FROM (VALUES
  ('PROG1', 2025, 'tiago.teacher@example.com'),
  ('MATH1', 2025, 'marta.teacher@example.com'),
  ('PROG2', 2025, 'tiago.teacher@example.com'),
  ('STAT', 2025, 'marta.teacher@example.com'),
  ('PROG1', 2026, 'tiago.teacher@example.com'),
  ('MATH1', 2026, 'marta.teacher@example.com'),
  ('DB', 2026, 'tiago.teacher@example.com'),
  ('STAT', 2026, 'marta.teacher@example.com'),
  ('SE', 2026, 'rui.teacher@example.com'),
  ('SQ', 2026, 'tiago.teacher@example.com'),
  ('SQ', 2026, 'marta.teacher@example.com'),
  ('UX', 2026, 'rui.teacher@example.com'),
  ('PM', 2026, 'marta.teacher@example.com')
) AS v(code, academic_year, teacher_email)
JOIN subject s ON s.code = v.code
JOIN subject_edition e ON e.subject_id = s.id AND e.academic_year = v.academic_year
JOIN users u ON u.personal_email = v.teacher_email
ON CONFLICT DO NOTHING;

INSERT INTO class_section (subject_edition_id, code)
SELECT e.id, v.section
FROM (VALUES
  ('PROG1', 2025, 'T1'),
  ('MATH1', 2025, 'T1'),
  ('PROG2', 2025, 'T1'),
  ('STAT', 2025, 'T1'),
  ('PROG1', 2026, 'T1'),
  ('PROG1', 2026, 'T2'),
  ('MATH1', 2026, 'T1'),
  ('DB', 2026, 'T1'),
  ('DB', 2026, 'T2'),
  ('STAT', 2026, 'T1'),
  ('SE', 2026, 'T1'),
  ('SQ', 2026, 'T1'),
  ('UX', 2026, 'T1'),
  ('PM', 2026, 'T1')
) AS v(code, academic_year, section)
JOIN subject s ON s.code = v.code
JOIN subject_edition e ON e.subject_id = s.id AND e.academic_year = v.academic_year
ON CONFLICT (subject_edition_id, code) DO NOTHING;

-- enrolments: the 2025 ones are finished with a final grade, the 2026 ones are in progress

INSERT INTO enrolment (student_id, subject_edition_id, class_section_id, status, final_grade)
SELECT u.number, e.id, cs.id, v.status, v.final_grade
FROM (VALUES
  ('diogo.student@example.com', 'PROG2', 2025, 'T1', 'PASSED', 15),
  ('diogo.student@example.com', 'STAT', 2025, 'T1', 'PASSED', 13),
  ('diogo.student@example.com', 'SQ', 2026, 'T1', 'IN_PROGRESS', NULL),
  ('diogo.student@example.com', 'UX', 2026, 'T1', 'IN_PROGRESS', NULL),
  ('ines.duarte@example.com', 'PROG1', 2025, 'T1', 'PASSED', 18),
  ('ines.duarte@example.com', 'MATH1', 2025, 'T1', 'PASSED', 14),
  ('ines.duarte@example.com', 'PROG2', 2025, 'T1', 'PASSED', 17),
  ('ines.duarte@example.com', 'DB', 2026, 'T1', 'IN_PROGRESS', NULL),
  ('ines.duarte@example.com', 'STAT', 2026, 'T1', 'IN_PROGRESS', NULL),
  ('ines.duarte@example.com', 'SE', 2026, 'T1', 'IN_PROGRESS', NULL),
  ('joao.pereira@example.com', 'PROG1', 2025, 'T1', 'FAILED', 7),
  ('joao.pereira@example.com', 'MATH1', 2025, 'T1', 'PASSED', 11),
  ('joao.pereira@example.com', 'PROG1', 2026, 'T2', 'IN_PROGRESS', NULL),
  ('joao.pereira@example.com', 'DB', 2026, 'T2', 'IN_PROGRESS', NULL),
  ('joao.pereira@example.com', 'STAT', 2026, 'T1', 'IN_PROGRESS', NULL),
  ('beatriz.lopes@example.com', 'PROG1', 2026, 'T1', 'IN_PROGRESS', NULL),
  ('beatriz.lopes@example.com', 'MATH1', 2026, 'T1', 'IN_PROGRESS', NULL),
  ('rafael.matos@example.com', 'PROG1', 2026, 'T2', 'IN_PROGRESS', NULL),
  ('rafael.matos@example.com', 'MATH1', 2026, 'T1', 'IN_PROGRESS', NULL),
  ('carolina.reis@example.com', 'PROG1', 2026, 'T1', 'IN_PROGRESS', NULL),
  ('carolina.reis@example.com', 'MATH1', 2026, 'T1', 'IN_PROGRESS', NULL),
  ('miguel.sousa@example.com', 'STAT', 2025, 'T1', 'PASSED', 12),
  ('miguel.sousa@example.com', 'PM', 2026, 'T1', 'IN_PROGRESS', NULL),
  ('leonor.faria@example.com', 'UX', 2026, 'T1', 'IN_PROGRESS', NULL),
  ('pedro.nunes@example.com', 'DB', 2026, 'T1', 'IN_PROGRESS', NULL)
) AS v(personal_email, code, academic_year, section, status, final_grade)
JOIN users u ON u.personal_email = v.personal_email
JOIN subject s ON s.code = v.code
JOIN subject_edition e ON e.subject_id = s.id AND e.academic_year = v.academic_year
JOIN class_section cs ON cs.subject_edition_id = e.id AND cs.code = v.section
ON CONFLICT (student_id, subject_edition_id) DO NOTHING;

-- lessons: 4 weekly lessons per 2026 class, all already given (the weekday depends on the class)

INSERT INTO lesson (class_section_id, starts_at)
SELECT cs.id, TIMESTAMP '2026-09-21 09:00' + (cs.id % 5) * INTERVAL '1 day' + week * INTERVAL '7 days'
FROM class_section cs
JOIN subject_edition e ON e.id = cs.subject_edition_id
CROSS JOIN generate_series(0, 3) AS week
WHERE e.academic_year = 2026
ON CONFLICT (class_section_id, starts_at) DO NOTHING;

-- absences: the student misses the first <lessons> lessons of that subject

INSERT INTO absence (enrolment_id, lesson_id, justified)
SELECT en.id, l.id, v.justified
FROM (VALUES
  ('diogo.student@example.com', 'SQ', 1, true),
  ('diogo.student@example.com', 'UX', 1, false),
  ('ines.duarte@example.com', 'SE', 1, false),
  ('joao.pereira@example.com', 'PROG1', 2, false),
  ('joao.pereira@example.com', 'DB', 3, false),
  ('joao.pereira@example.com', 'STAT', 2, false),
  ('rafael.matos@example.com', 'MATH1', 1, false),
  ('leonor.faria@example.com', 'UX', 1, false)
) AS v(personal_email, code, lessons, justified)
JOIN users u ON u.personal_email = v.personal_email
JOIN subject s ON s.code = v.code
JOIN subject_edition e ON e.subject_id = s.id AND e.academic_year = 2026
JOIN enrolment en ON en.student_id = u.number AND en.subject_edition_id = e.id
JOIN LATERAL (
  SELECT id FROM lesson WHERE class_section_id = en.class_section_id ORDER BY starts_at LIMIT v.lessons
) AS l ON true
ON CONFLICT (lesson_id, enrolment_id) DO NOTHING;

-- tutoring: Diogo is an approved tutor of Programming I, Inês is waiting for an answer

INSERT INTO tutor_application (student_number, subject_id, status, motivation, submitted_at, decided_at, decided_by_number)
SELECT u.number, s.id, v.status, v.motivation, CAST(v.submitted_at AS timestamp), CAST(v.decided_at AS timestamp), t.number
FROM (VALUES
  ('diogo.student@example.com', 'PROG1', 'APPROVED', 'I passed Programming I and II and like explaining things.',
    '2026-09-25 10:00', '2026-09-26 15:00', 'tiago.teacher@example.com'),
  ('ines.duarte@example.com', 'PROG1', 'PENDING', 'Got an 18 last year and I want to help first years get started.',
    '2026-10-14 18:30', NULL, NULL)
) AS v(personal_email, code, status, motivation, submitted_at, decided_at, decided_by_email)
JOIN users u ON u.personal_email = v.personal_email
JOIN subject s ON s.code = v.code
LEFT JOIN users t ON t.personal_email = v.decided_by_email
WHERE NOT EXISTS (SELECT 1 FROM tutor_application a WHERE a.student_number = u.number AND a.subject_id = s.id);

INSERT INTO tutor_role (application_id, student_id, subject_id)
SELECT a.id, a.student_number, a.subject_id
FROM tutor_application a
JOIN users u ON u.number = a.student_number
JOIN subject s ON s.id = a.subject_id
WHERE u.personal_email = 'diogo.student@example.com' AND s.code = 'PROG1' AND a.status = 'APPROVED'
ON CONFLICT DO NOTHING;

-- study rooms are dated from today, so there's always a past one and an upcoming one

INSERT INTO study_room (tutor_role_id, description, capacity, link, starts_at, ends_at, status)
SELECT r.id, v.description, v.capacity, v.link,
  CAST(CURRENT_DATE AS timestamp) + v.starts_in, CAST(CURRENT_DATE AS timestamp) + v.starts_in + INTERVAL '90 minutes', v.status
FROM (VALUES
  ('Loops and functions before the first test', 6, 'https://meet.google.com/prg-loop-fun', INTERVAL '-5 days 18 hours', 'COMPLETED'),
  ('Solving last year''s Programming I exam', 8, 'https://meet.google.com/prg-exam-rev', INTERVAL '3 days 18 hours', 'SCHEDULED')
) AS v(description, capacity, link, starts_in, status)
CROSS JOIN tutor_role r
JOIN users u ON u.number = r.student_id
JOIN subject s ON s.id = r.subject_id
WHERE u.personal_email = 'diogo.student@example.com' AND s.code = 'PROG1'
  AND NOT EXISTS (SELECT 1 FROM study_room sr WHERE sr.tutor_role_id = r.id AND sr.description = v.description);

INSERT INTO participation (study_room_id, student_id, joined_at, attended)
SELECT sr.id, u.number, sr.starts_at - INTERVAL '2 days', v.attended
FROM (VALUES
  ('Loops and functions before the first test', 'beatriz.lopes@example.com', true),
  ('Loops and functions before the first test', 'rafael.matos@example.com', true),
  ('Loops and functions before the first test', 'joao.pereira@example.com', false),
  ('Solving last year''s Programming I exam', 'beatriz.lopes@example.com', false),
  ('Solving last year''s Programming I exam', 'rafael.matos@example.com', false),
  ('Solving last year''s Programming I exam', 'carolina.reis@example.com', false)
) AS v(description, personal_email, attended)
JOIN study_room sr ON sr.description = v.description
JOIN users u ON u.personal_email = v.personal_email
ON CONFLICT (study_room_id, student_id) DO NOTHING;

INSERT INTO rating (participation_id, stars, comment, rated_at)
SELECT p.id, v.stars, v.comment, sr.ends_at + INTERVAL '1 hour'
FROM (VALUES
  ('beatriz.lopes@example.com', 5, 'Clear explanations, the exercises helped a lot.'),
  ('rafael.matos@example.com', 4, NULL)
) AS v(personal_email, stars, comment)
JOIN users u ON u.personal_email = v.personal_email
JOIN study_room sr ON sr.description = 'Loops and functions before the first test'
JOIN participation p ON p.study_room_id = sr.id AND p.student_id = u.number
ON CONFLICT (participation_id) DO NOTHING;

INSERT INTO earned_achievement (student_id, achievement, earned_at, verification_code)
SELECT u.number, 'FIRST_STUDYROOM', CAST(CURRENT_DATE AS timestamp) - INTERVAL '5 days' + INTERVAL '20 hours', CAST(gen_random_uuid() AS varchar)
FROM users u
WHERE u.personal_email = 'diogo.student@example.com'
ON CONFLICT DO NOTHING;
