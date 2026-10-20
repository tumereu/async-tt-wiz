# Authentication and sessions

## Confirmed convenience requirement — D-268

The owner requests an auth token that lasts indefinitely, or saved credentials and
automatic reauthentication until explicit Logout. Use the indefinite **revocable
session-token** route, without storing the password. Preserve D-263's Spring
Security and Keystore-backed credential protection, and D-218's concurrent devices.
No app/scaffold or executable security tests are authorized by this design.

- No idle timeout, maximum age or periodic password/login requirement. Persist
  the token across ordinary app/service restarts, inactivity and reconnects.
- Explicit Logout clears this installation's login; no saved-password fallback
  may immediately log it back in. Other device sessions remain independent.
- Non-expiring is not unrevocable. Continue server authorization checks; do not
  silently reauthenticate around explicit security revocation or removed access.
  Local token/app-data/key loss can also require login; this is not time expiry.
- Invalid authentication does not reject an unknown-outcome game command by
  implication, erase its recovery context or turn it into a fresh invocation.
  Same-account reauthentication restores existing game/outcome recovery.
- Private-download/Logout cleanup and protected request/exposure obligations follow
  the [account contract](../03-contracts/accounts-decks-and-lobby.md#logout-and-account-private-downloads--d-241).

## Recommended token mechanics

Keep the preceding opaque-token proposal: generate a high-entropy random bearer
token, store only its verifier/hash with the server session, and encrypt the client
copy with a Keystore-backed key. Never persist the password or place auth tokens in
URLs/logs. Exact formats, hashing, secure storage and session registration remain
technical design; this is not selection of JWT/refresh-token machinery.

Persist valid sessions in PostgreSQL without expiry by age/inactivity. Last-used
timestamps may be metadata, not a hidden eviction criterion. Routine cleanup,
service restarts and deployments must not silently reintroduce time-based logout.
Manage explicit revocation independently from game history/revision retention.

Authenticate HTTP and WebSocket access with TLS. An already-open socket must not
provide permanent authorization after explicit session revocation; commands and
subscriptions obey actual access. Expiring socket connections, heartbeat deadlines
and network timeouts are transport conditions, not reasons to expire the login.

Long-lived bearer-token theft can grant access until revocation; strong random
tokens, protected local storage, TLS and no secret logging remain necessary. Saving
the reusable password would increase the exposure and enable bypassing revocation.

## Earlier security proposals not finalized by the lifetime correction

Argon2id password hashing, measured parameters/rate limits, hashed short-lived
single-use password-reset tokens, generic reset-request responses, email delivery
adapter and reset-revokes-all-sessions were presented as recommendations. D-268
does **not** approve every one of these proposals or decide reset revocation policy.
In particular, time limits on reset tokens are distinct from the non-expiring login
token. Exact policies and providers remain to design; no new device-management UI
is implied. Preserve password whitespace/Unicode consistently under D-249.
