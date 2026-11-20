# Quality report

Where the code stands at the end of Sprint 3, and what we changed because of what we measured. The numbers are from 20 November 2026: SonarQube Cloud's analysis of `main` (commit `6dcc339`), JaCoCo for the backend and Vitest for the frontend. How each tool runs is in [testing.md](testing.md).

## Summary

| Metric | Result | Interpretation |
| --- | --- | --- |
| Line coverage | 96.9% (SonarQube, backend and frontend). Backend alone 97.9% (JaCoCo) | Almost every line runs in a test. What's left is small: getters nothing calls and a few error paths, not whole features (see below). `studymatch-ui` is left out on purpose, see testing.md |
| Branch coverage | 93.1% (SonarQube). Backend alone 96.6% | Lower than lines, as expected: the missing branches are edge cases like a blank search or a 19-digit search, where one side of an `if` never runs. One of them was a real rule (GR7 in the staff list), and it has a test now |
| Cyclomatic complexity | 3,095 over 1,715 functions, about 1.8 per function | Most methods have one or two paths. The most complex file is `StudentService` (72 over 26 methods), which does registering, importing, editing, statutes, the profile and the personality test. It's the class to split first if it grows |
| Duplicated lines | 0.4% | Well under the 3% the quality gate allows. Since the tests aren't analysed, this is only production code |
| Maintainability issues | 1 code smell, 5 minutes of technical debt, rating A | The one smell is a `tabIndex` on a non-interactive element in `ImageCropper`, an accessibility finding. It was listed in #244, which is closed, so it slipped through |
| Team-selected: reviewed PRs | 52 of 53 PRs merged into `main` had an approving review from someone else (98%). 16 of them (30%) had changes requested first | Every change except the very first one was read by another person before it reached `main`, and almost a third needed changes, so the reviews catch things. See below |

The other SonarQube ratings: security A with 0 vulnerabilities and every security hotspot reviewed, and reliability C because of 2 bugs. One of them is fixed in this sprint (improvement 3 below). The other is a `reduce()` without an initial value in `personalityScores.ts`.

## The team-selected metric

We picked **the share of merged PRs that had an approving review**. SonarQube and JaCoCo measure the code, but none of them says anything about how the code got there. Our process is that every change goes through a pull request that someone else approves (contributing.md), and this checks we actually did it. We counted it with the GitHub CLI over every PR merged into `main` since the first one on 10 October: 53 PRs, 52 approved by someone other than the author. The one without a review is #84, the very first PR, a README update. The 16 PRs that got "changes requested" at least once are the useful part: those are bugs and design problems found by a person before the merge, like the two simultaneous confirmations in #180.

Two other numbers back it up. With this sprint's changes there are 1,120 backend tests and 157 frontend tests, and all of them pass. And SonarQube's cognitive complexity (how hard the code is to follow, not just how many paths it has) points at the same places as cyclomatic complexity: `Table.tsx` (49) and `StudentService` (47) are the top two.

## Important code with weak coverage

We went through the areas the brief names.

- Profile calculation (`PersonalityProfile.fromAnswers`): every line and branch of the calculation is covered. The class shows 80% because of `getId` and four setters that nothing in the main code calls. #193 removes the setters.
- Cold start: `hasAcademicHistory` and the empty values (no average, no level, no attendance before the first lesson) are covered. The part of cold start that uses them, generating groups (#57), isn't built yet, so it can't be tested yet. It will need tests for each level of GR6.
- Validation: every request DTO has its own test with the boundaries (for example 64 characters is a valid username and 65 isn't), and the models test their own rules.
- Statute in the staff list: a student with an active statute must not show attendance (GR7). The teachers' list tested it, but the staff list only ever ran the side without a statute. We added `staffListHidesAttendanceWithStatute`.

What's still not covered is low risk: a blank search, a numeric search longer than a `Long`, and a duplicate email found while importing a CSV row. We don't think those need more tests before the deadline.

## Improvements

Each one follows Problem, Evidence, Modification, Verification. After each change we ran the whole test suite and the analysis again.

### 1. Design: small OOP fixes in the models (#193)

Still in progress. #193 fixes the design problems found in the code review (for example `User.checkCanBeBlocked` checking the role in the base class). This section gets filled in when it's merged.

### 2. Performance: the student list did a few queries per student

**Problem.** The load test of the staff student directory (`GET /students/`, see [performance.md](performance.md)) slowed down 5 times with 50 users at once, while the throughput only went up 7 times. Requests were queuing.

**Evidence.** We logged the SQL of one request: 78 statements for one page of 9 students. For each student the list asked for their enrolments, then counted the lessons and the unjustified absences of every enrolment one by one, then loaded the class, subject, degree and personality profile separately. 18 lesson counts, 18 absence counts and 17 enrolment selects were the bulk of it.

**Modification.** The list now gets everything for the whole page at once (`EnrolmentService.getListStats`): one query for the page's enrolments with their class and subject, one grouped count of lessons per class, and one grouped count of absences per enrolment. The degree and personality profile come with the students in the same query (`@EntityGraph`). The calculations didn't change, and each service still only uses its own repository. The profile and record pages use the old per-student methods, which are still there.

**Verification.**

- The same request now runs 11 statements instead of 78.
- All the existing list tests pass with the same values (attendance 50%, average 14), and two new unit tests check a whole page.
- We ran the same JMeter plan against both versions, one after the other, on the same machine and with the same settings:

| 50 users, 400 requests each | Before | After |
| --- | --- | --- |
| Average | 176.1 ms | 34.2 ms |
| Median | 176 ms | 33 ms |
| 95th percentile | 201 ms | 48 ms |
| Throughput | 280 requests/s | 1,388 requests/s |
| Errors | 0% | 0% |

With one user the average went from 25.2 ms to 10.8 ms. This comparison uses a 1-second ramp-up instead of the 10 seconds in performance.md, because after the change a request takes so little that with 10 seconds the users finished before the others started and there were never 50 at once. With 1 second, both versions had 49 to 50 users running together the whole time.

### 3. Reliability: a long username could overflow the stack

**Problem.** SonarQube reported a bug (rule S5998) in `CreateAccountRequestDTO`: the username pattern `[a-z0-9]+(\.[a-z0-9]+)*` repeats a group, and Java matches each repetition with a recursive call. `@Size(max = 64)` doesn't help, because Bean Validation checks every constraint, so the pattern still runs on a huge value.

**Evidence.** A 1 MB username like `a.a.a...a` threw `StackOverflowError` in the pattern. Through the API, an Admin creating an account with it would get a 500 instead of a validation error.

**Modification.** The same pattern with possessive quantifiers, `[a-z0-9]++(?:\.[a-z0-9]++)*+`. They don't backtrack, so Java matches it in a loop instead of recursion. It accepts and rejects exactly the same usernames.

**Verification.** The new test `hugeUsernameDoesNotOverflow` fails with `StackOverflowError` on the old pattern and passes on the new one (the answer is just `Size`, too long). Every existing username test passes unchanged. SonarQube's analysis of this PR no longer reports the bug.

## Other results

- Performance (JMeter, #188): [performance.md](performance.md)
- Static analysis (SonarQube, #189): the [project on SonarQube Cloud](https://sonarcloud.io/project/overview?id=Cipher-Pol-UPT_StudyMatch), and how it's set up in [testing.md](testing.md#sonarqube)
- Security (OWASP ZAP, #190): added when #190 is merged

## ISO/IEC 25010

How the results above map to the product quality characteristics from the quality standards class:

| Characteristic | What shows it |
| --- | --- |
| Functional suitability | 1,277 automated tests over the use cases and business rules, 96.9% line coverage |
| Performance efficiency | the JMeter runs, and improvement 2 (5 times the throughput on the student list) |
| Security | 0 vulnerabilities and every hotspot reviewed in SonarQube, and the ZAP scan (#190) |
| Maintainability | rating A, 0.4% duplication, 1.8 complexity per function, reviewed PRs |
| Reliability | 0% errors under load, and improvement 3 |
