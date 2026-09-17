# Security policy

Security fixes target the current main branch; this project does not promise production support or an audited security certification.

Report a suspected vulnerability privately to repository owner **@OmmPrakash-tech** using GitHub private vulnerability reporting if enabled. If unavailable, request a private reporting channel from the owner without publishing exploit details, credentials or personal data. Do not file sensitive vulnerability details as a public issue.

Include the affected revision, minimal reproduction, expected/actual behavior and impact. Use disposable test accounts. Do not test against systems without authorization.

Current controls include server-side role and ownership checks, BCrypt passwords, rotating hashed refresh/reset tokens, token revocation, bounded API bodies, parameterized SQL, database constraints and assignment locks. See `docs/security.md` for deployment obligations and limits. Per-instance rate limits are not a distributed abuse-prevention system.
