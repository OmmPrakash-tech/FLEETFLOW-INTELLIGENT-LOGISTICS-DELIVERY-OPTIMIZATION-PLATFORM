# Deployment and observability

## Containers

Compose defines `backend`, `frontend`, `postgres`, and `redis`. Java and Node builds run in separate image stages. Backend and nginx runtime processes use non-root users. PostgreSQL uses a persistent named volume. Credentials are supplied from `.env` at runtime and are excluded from Docker build contexts.

Compose syntax can be validated with `docker compose config --quiet` without printing resolved secrets. Runtime validation requires a functioning Docker engine. The initial local engine was unavailable; see `verification.md` for the final executed state. A configured Compose file is not evidence that images have successfully built or containers have run.

The frontend nginx proxy forwards API requests and disables buffering for SSE. It adds CSP, frame protection, MIME sniffing protection and a same-origin referrer policy. Enable TLS at the production ingress. The example only binds published ports to localhost.

## Health and metrics

- `/actuator/health`: basic health, publicly readable without component details.
- `/actuator/health/liveness` and `/actuator/health/readiness`: probe groups.
- `/actuator/metrics`: ADMIN only. Includes framework HTTP observations and custom meters.
- `fleetflow.order.processing`: order creation timer, including failures.
- `fleetflow.inventory.conflicts`: rejected reservations when no candidate can fulfill.
- `fleetflow.route.computation`: Dijkstra computation time.
- `fleetflow.route.cache.hits` and `fleetflow.redis.fallbacks`: actual cache behavior.

Redis is not part of core health because it is optional. Mail health is active only when email is enabled. Database failures return 503 for affected operations; the backend does not substitute an in-memory store. Logs are standard Spring application logs; secrets are not intentionally included.

## AWS mapping

| Local component | Future AWS service |
| --- | --- |
| PostgreSQL | RDS PostgreSQL |
| Redis | ElastiCache |
| Containers | ECS/Fargate or EKS |
| Runtime secrets | Secrets Manager |
| Logs/metrics | CloudWatch and a configured telemetry exporter |
| Future object files | S3 |

No cloud resources have been created. Externalize database/cache locations and origin/SMTP settings. Use a dedicated database role, backups, restore tests, capacity planning and load testing before deployment. Rolling schema changes must remain backward compatible with simultaneously running application versions. There is no Kubernetes, Terraform or automated cloud rollout in this implementation.

## CI

GitHub Actions defines backend verification against a temporary PostgreSQL 18 service, a frontend production build, and a separate Docker/Chromium job that validates Compose, builds and starts the stack, waits for database readiness, and runs real browser tests. CI credentials are public, ephemeral test-only values with no relationship to local or deployed credentials. Local browser tests require seeded data and Chrome; BASE_URL can point to an isolated verification stack. CI installs Chromium. The new CI job has not run remotely because this upgrade has not been pushed. Equivalent local container builds/startup and browser checks passed.

## Formatting

Frontend: `npx prettier --write src tests vite.config.ts playwright.config.ts`.
Java: `./mvnw com.spotify.fmt:fmt-maven-plugin:2.29:format` from `backend`.
