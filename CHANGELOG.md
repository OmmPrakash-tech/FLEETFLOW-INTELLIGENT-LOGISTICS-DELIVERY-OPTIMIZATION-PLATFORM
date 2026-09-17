# Changelog

## Unreleased — 2026-09-17

### Fixed

- Lock only a selected driver/vehicle candidate and recheck feasibility, preserving spare resources during concurrent assignments.
- Reject malformed/disconnected graph components, null stops, non-finite scores and ETA overflow.
- Serialize driver-account validation with account administration; prevent active warehouse relocation.
- Validate JWT version claims, bind SSE initialization to authenticated version and avoid permit loss on failed initial lookup.
- Return meaningful missing-resource responses for address deletion and alert resolution.
- Scope map SVG sizing so location icons keep their intended size.

### Added

- Shared safe API error envelope, generated request IDs, bounded JSON request bodies and structured access-log messages.
- Additive lookup indexes without changing existing migration history.
- Live multi-stop planning form using the existing nearest-neighbor endpoint.
- Regression/API/browser coverage, Docker/browser CI job and maintainer/contributor documentation.

See `docs/verification.md` for executed results and limitations. These changes are local and have not been pushed.
