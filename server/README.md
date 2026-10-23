# Server

JDK 21 Spring Boot service with PostgreSQL, explicit Flyway migrations and generated
jOOQ bindings. Uses `:core` for game rules/state and `:protocol` for wire types.
Accounts, saved decks, contacts, invitations, ordered game commands, history and
chat are server-authoritative. HTTPS terminates at an external proxy.

## Build and test

From the repository root, with JDK 21, Android SDK 36 and Docker available:

```sh
./scripts/gradle.sh :server:installDist
./scripts/gradle.sh :server:test
./scripts/gradle.sh :server:verifyJooqGeneration
./scripts/build-server-image.sh
```

Compilation generates jOOQ against a disposable Testcontainers PostgreSQL using
the same migrations as deployment. Tests own their databases; they never need a
developer or production database. The pinned database image is in
`database-images.properties`; runtime dependencies are version-catalog/lockfile managed.
See [self-hosting](../deploy/README.md) for the Docker image and Compose stack.

## Runtime and database boundary

The installed executable requires an explicit `migrate` or `serve` command:

```sh
DB_URL=jdbc:postgresql://127.0.0.1:5432/your_database \
DB_USER=your_user DB_PASSWORD_FILE=/absolute/path/to/password \
  server/build/install/server/bin/server migrate
```

Migrations are forward-only and separate from service startup. Checksums/history
are validated; unsupported, nonempty unversioned or newer schemas fail closed,
without automatic clean, repair or baseline. Add migrations; do not edit deployed SQL.

`serve` needs the DB inputs above, `PUBLIC_BASE_URL`, and reset-mail configuration:

| Input | Meaning |
| --- | --- |
| `SERVER_ADDRESS`, `SERVER_PORT` | Public listener; defaults `0.0.0.0:8080` |
| `HEALTH_ADDRESS`, `HEALTH_PORT` | Separate health listener; defaults `127.0.0.1:8081` |
| `PUBLIC_BASE_URL` | Absolute external HTTP(S) base URL; use HTTPS outside local development |
| `RESET_BASE_URL` | HTTPS origin for `/reset-password` links |
| `RESET_MAIL_KEY_FILE` | Canonical base64 of 32 random bytes; retain across restarts |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_FROM`, `SMTP_TLS` | Required relay configuration; TLS mode `starttls` or `tls` |
| `SMTP_USERNAME_FILE`, `SMTP_PASSWORD_FILE` | Optional paired file secrets |

`local-plaintext` SMTP is only for isolated synthetic development, without credentials.
The CLI has no disabled-mail mode, but SMTP outage does not block readiness or play.
Secrets are bounded UTF-8 regular files; one final LF/CRLF is removed. Do not log
JDBC URLs, environment values, SQL parameters, reset queries or mail bodies.

Only the health listener serves `/health/live` and `/health/ready`: liveness is
process-only, readiness requires valid schema, writer ownership and recovered
workers. Both return plain text, 200 or (for unready) 503. The public root/health
paths return 404. Forwarded headers are not trusted.

One writer holds a PostgreSQL session advisory lock and advances a persisted epoch
on acquisition. Mutations enter `ServiceRuntime.transaction()` and its epoch fence,
not a cached ready flag. Recovery revalidates schema and reloads durable work;
SIGTERM stops admission and drains bounded transactions/workers. Reset-mail, game
execution/publication and activity workers participate in lifecycle management.

## Accounts and password reset

Stateless JSON, `Cache-Control: no-store`; private requests use `Authorization:
Bearer …`, never cookies or URL credentials. Account bodies are limited to 8192
bytes with strict UTF-8 and typed fields. Shared types are in
[`Accounts.kt`](../protocol/src/commonMain/kotlin/org/asyncttwiz/protocol/Accounts.kt).

| Route | Success |
| --- | --- |
| `POST /accounts` (`username,email,password`) | 201 safe identity; does not sign in |
| `POST /sessions` (`email,password`) | 201 opaque bearer token and safe identity |
| `GET /account` | 200 current identity (no email) |
| `DELETE /session` | 204; revokes only this token, idempotent |
| `POST /password-reset-requests` (`email`) | 202 for known and unknown email |
| `POST /password-reset-links` (`token`) | 204 usable link; read-only preflight |
| `POST /password-resets` (`token,password`) | 204 password changed; no login token |

Usernames use trimmed NFC display/full case-fold keys; email uses ASCII local
parts and IDNA domains without dot/plus alias guessing. Passwords are exact
8–256 Unicode scalars, at most 1024 UTF-8 bytes; never trim or normalize them.
Argon2id stores password verifiers; only SHA-256 session/reset-token verifiers
persist. Sessions have no age expiry. Requests recheck revocation; work already
authorized may finish. Hashing concurrency and request rates are bounded.

Errors use stable `{code,field?}` values, not exception text: 400 invalid input,
401 invalid credentials/authentication required, 409 account unavailable, 429
`try_later` with `Retry-After`, 503 busy/unavailable. Do not infer commit/rejection
from a transport failure.

Reset links expire after 30 minutes. Only successful confirmation changes the
password, revokes all sessions and consumes outstanding tokens atomically. The
durable mail outbox encrypts payloads with AES-256-GCM and retries temporary SMTP
failures with bounded backoff; a crash may resend the same link. A wrong retained
mail key blocks recovery rather than deleting pending records.

`GET /reset-password?token=…` provides a no-script browser fallback. Explicit POST
confirmation uses cookie-bound CSRF protection; opening a link never changes a
password or signs in. Keep proxy access logs off or redact token queries.

## Feature notes

- [Definitions and saved decks](DECKS.md)
- [Contacts, game lists and invitations](LOBBY.md)
- [Ordered game service, synchronization and history](GAMES.md)
- [Authentication design](../docs/design/04-technical/authentication-and-sessions.md)
- [Storage mechanics](../docs/design/04-technical/storage-mechanics.md)
