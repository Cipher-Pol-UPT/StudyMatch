# Contributing

Every change goes through the same five steps: issue, branch, pull request, review and merge. Nothing goes straight to `main`; GitHub blocks merging a pull request until someone has approved it.

## 1. Issue

Each piece of work is a GitHub issue. Use cases are split into a Backend and a Frontend issue with the same description, and the frontend one usually waits for the backend one. Bugs, tests, docs and setup work get issues too, so every commit can be traced back to one.

Before you start, assign the issue to yourself and move it to **In progress** on the [project board](https://github.com/orgs/Cipher-Pol-UPT/projects/1). The board's columns are Backlog, Ready, In progress, In review and Done. If the issue leaves something open (a rule that isn't clear, a case it doesn't mention), ask in the issue or on Discord before building it, and update the issue with the answer.

## 2. Branch

Branch off an up-to-date `main`:

```bash
git switch main
git pull
git switch -c feat/grade-student-backend
```

Branch names are `type/short-name`, lowercase with hyphens:

| Type | For |
| --- | --- |
| `feat` | a use case or a new piece of one |
| `fix` | a bug |
| `test` | tests only |
| `refactor` | changing code without changing what it does |
| `docs` | documentation only |
| `chore` | anything else (dependencies, demo data, cleanup) |
| `setup` | project setup (security, tooling, base structure) |

For a use case, end the name with `-backend` or `-frontend` so both halves can be open at once.

## 3. Commits

```
type: what changed, in lowercase (#issue)
```

For example `feat: grade student for teachers (#79)` or `test: final grade rounding (#79)`. The types are the same as for branches; a scope is fine when it helps (`fix(frontend): ...`). Keep each commit to one idea. A use case usually ends up as a `feat` commit with the code and a `test` commit with its tests. Only stage the files you mean to commit (`git add <path>`, not `git add .`), so local files like personal notes never end up in the repo.

If `main` moves on while you work, merge it into your branch (`git merge origin/main`) and fix any conflicts there, not in the pull request.

## 4. Pull request

Open the pull request against `main` once the work meets the Definition of Done below. Fill in the template:

- the title says what the PR adds, in lowercase (`added grade student for teachers`)
- the description starts with `closes #79`, so the issue closes and moves to Done on merge
- the description lists what changed: endpoints, rules, error codes, and anything the reviewer should look at closely
- assign it to yourself and move the issue to **In review**

Keep pull requests to one issue. A huge PR gets a worse review.

## 5. Review

Anyone in the team except the author can review. Pick someone in the PR's Reviewers box so they get notified.

The reviewer:

1. checks the code against the issue: the main scenario, the alternative scenarios and the business rules
2. pulls the branch and runs the tests (`./mvnw verify` or `npm test`), and tries the feature in the app or in Swagger UI
3. comments on specific lines. Anything that has to change before merging goes in as **Request changes**; small things that can wait are just comments, marked as non-blocking
4. approves once it's ready

The author answers every comment, either with a fix or with a reason for leaving it. If a non-blocking comment is left for later, it gets its own issue so it isn't lost.

## 6. Merge

The reviewer merges after approving, once the Backend and Frontend checks on the PR are green (merge commit, not squash, so the commits keep their issue numbers). If a check fails, its run on the Actions tab shows which step broke, and a failed backend run has the test reports attached. Then the branch is deleted, on GitHub with the button on the PR and locally with `git branch -d <branch>`. Check that the issue closed and moved to Done.

## Definition of Done

A pull request is ready for review when all of these are true:

- [ ] It's linked to its issue (`closes #N`) and does what the issue describes, including the alternative scenarios and business rules.
- [ ] New business rules have unit tests, including the boundary values. New endpoints have an integration test covering success, the main errors and who's not allowed. See [testing.md](testing.md).
- [ ] `./mvnw verify` passes for the backend; `npm test`, `npm run lint` and `npm run build` pass for the frontend.
- [ ] New error codes and enum values are in all four places (`ErrorCode.java`, `errors.ts`, `en.json`, `pt.json`) with both translations. See [conventions.md](conventions.md#error-codes).
- [ ] New UI text is in `en.json` and `pt.json`, not written in the component.
- [ ] The docs are up to date: each new or changed endpoint has its `@Operation` summary and `@ErrorCodes`, the right file in `docs/` reflects the change (a new rule goes in `domain.md`, a new endpoint in `api.md`), and the README is updated if the setup changed.
- [ ] No secrets, passwords or personal files in the diff.

It's done when, on top of that, the Backend and Frontend checks passed, another team member approved it, it's merged, the branch is deleted and the issue is in Done.

A pull request that adds or changes behaviour without updating the docs it affects doesn't get approved.
