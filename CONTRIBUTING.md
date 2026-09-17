# Contributing

Use Java 25, Node 24 and PostgreSQL 18. Follow the README setup, copy `.env.example` to an ignored `.env`, and use your own secrets. Demo accounts are opt-in and must never be enabled in a public deployment.

Keep changes focused on existing domain behavior. Explain the problem, resulting behavior and validation in a pull request. Preserve applied Flyway migrations: add a new version for schema changes. Do not rewrite shared Git history.

Before submitting:

- Run `scripts/backend.ps1 verify` on Windows, or `cd backend && ./mvnw verify` with database environment variables on other platforms.
- Run `npm ci` and `npm run build` inside `frontend`.
- For UI changes, run `npx playwright test` against running services with explicit demo credentials. The tests use real APIs; never target a production deployment.
- Validate containers with `docker compose config --quiet` and build the affected images.
- Add regression tests for correctness, security or concurrency fixes. Describe checks you could not run.

Never include credentials, tokens, `.env`, private customer data or fabricated performance claims. Report vulnerabilities privately as described in SECURITY.md.
