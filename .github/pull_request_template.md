closes #

<!-- what this adds or fixes: endpoints, rules, error codes, screens. Mention anything the reviewer should look at closely -->

**How to try it**

<!-- which account, which page or endpoint, what to click or send -->

**Checklist** (see the Definition of Done in docs/contributing.md)

- [ ] Tests for the new rules and endpoints, and `./mvnw verify` / `npm test`, `npm run lint`, `npm run build` pass
- [ ] New error codes and UI text are in `ErrorCode.java`, `errors.ts`, `en.json` and `pt.json`
- [ ] Docs updated: `@Operation` and `@ErrorCodes` on new or changed endpoints, the right file in `docs/`, the README if the setup changed
