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
- Private-download/Logout cleanup and D-278's volatile request/exposure boundary follow
  the [account contract](../03-contracts/accounts-decks-and-lobby.md#logout-and-account-private-downloads--d-241).

## Selected technical token mechanics — D-284

Keep the preceding opaque-token proposal: generate a high-entropy random bearer
token, store only its verifier/hash with the server session, and encrypt the client
copy with a Keystore-backed key. Never persist the password or place auth tokens in
URLs/logs. Use 32 secure random bytes, base64url without padding; server stores
SHA-256 verifier indexed for lookup, account/session identity and auth epoch.
No JWT/refresh-token pair. Android stores AES-GCM ciphertext with a Keystore-held
key, unique random nonce per encryption, no biometric prompt on ordinary startup.
Exclude token/private database from platform backup and redact network logging.

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

## Passwords and recovery — D-279/D-284

D-279 confirms revocation of **all existing sessions when the reset successfully
changes the password**, not on requesting/sending the email or merely opening the
link. Sending email must not log the user out. Invalid/expired/used token or failed
reset leaves password/sessions unchanged. Reset is not automatic login; sign in
using the new password. No device-management/password-change UI is added.

Technical profile:

- Argon2id v19, initial 64 MiB / 3 iterations / parallelism 1, 16-byte random salt,
  32-byte output; store versioned PHC string. Benchmark on declared host and tune
  cost without changing password bytes; rehash on successful login if parameters
  change. Bound concurrent hashing workers to 2 initially to prevent RAM exhaustion.
- Passwords: 8–256 Unicode scalar values, at most 1024 UTF-8 bytes, no composition
  rules or periodic rotation. Preserve exact whitespace/case/Unicode (D-249), allow
  paste and password managers. Reject unsupported/malformed encoding consistently
  rather than replacing/truncating. No plaintext password persistence/logging.
- Username: trim boundary whitespace, NFC normalize for display; 1–64 scalar values,
  reject control/bidi override characters. Lookup/uniqueness key uses Unicode default
  case-fold of NFC, pinned to Unicode 15.1 for semantic v1; retain display form.
  Search uses the same key, bounded results. No R1 username rename feature.
- Email: trim boundary whitespace; IDNA-normalize/lowercase domain and case-fold
  ASCII local part for account lookup; preserve delivery form, max 254 bytes. No
  provider-specific dot/plus stripping or guessed aliases. R1 supports normal ASCII
  mailbox local parts, not SMTPUTF8; explain invalid input. Registration/login/reset
  use exactly the same key. No mandatory email-verification gate is added.
- Generic response for reset requests regardless of account existence. Login/reset
  errors never expose account email to other users. Unique-email registration
  failure can explain that the address cannot be used without revealing owner data.
- Reset token: 32 secure random bytes, SHA-256 verifier, account/creation/30-minute
  expiry/consumed timestamp. Store no plaintext token except bounded encrypted
  outbound-email payload until delivery. Concurrent valid tokens may exist; issuing
  a new email does not invalidate an earlier usable link or sessions. Successful
  reset atomically consumes all outstanding tokens, changes password and increments
  account auth epoch/revokes all sessions; two concurrent uses have one winner.
- Email contains configured public HTTPS reset link; no secret in analytics/referrer
  headers/access logs. Reset form uses `Referrer-Policy: no-referrer`, no third-party
  scripts/assets and explicit POST confirmation with CSRF protection, not password
  change on GET. SMTP with TLS/credential files is the replaceable delivery adapter;
  synthetic SMTP sink in tests, no selected commercial email service.
- Durable email outbox: retry network/temporary SMTP failures with bounded backoff
  1/5/15 minutes, stop at token expiry; permanent rejection stops retries. Delivery
  failure never changes password/sessions or falsely reports a completed reset.
  User can request another email. Delete encrypted token-bearing mail payload after
  delivery/expiry; redacted operational failure only.
- Rate limits initially: login 10/min/IP and 5/min/email key, reset requests 3/hour/
  email key and 20/hour/IP, registration 5/hour/IP; generic bounded retry responses.
  No permanent account lockout based on an attacker sending bad passwords/emails.
  Tune against legitimate shared-network use; limits do not expire login tokens.

### Ordinary revocation boundary — D-289

Check current session validity for new HTTP requests, game-command admission and
subscription/read access; an open socket is not permanent authorization. After a
successful password reset, old sessions no longer authorize new requests. Notify/
close revoked sockets best effort. Requests already authorized/in flight, including
durably admitted game commands, may finish normally; their committed effects and
original outcomes remain. A new same-account session may recover those outcomes,
not another account's work.

Use ordinary authentication-library/database handling for password verification,
session issuance, reset and rehash. No special reset-versus-login/HTTP/game-commit
linearization guarantee, account authorization lock hierarchy, or execution-time
admitted-session epoch fence is required. This supersedes the stronger D-284/D-288
race requirements; it does not remove password hashing, reset-token validation,
transactional successful reset or normal authorization checks. A retained original
never admitted before reauthentication may use the valid same-account session.

Explicit Logout immediately clears local credentials/private data and fences old
callbacks. Send ordinary idempotent server logout/revocation best effort; once the
server processes it, that session cannot authorize new requests. Local logout does
not wait for connectivity or confirmed server revocation. An offline/lost logout
may leave the server session valid; do not retain credentials or create a durable
revocation workflow solely to close that window. Other sessions remain independent.

Credential endpoints are not generic business-outcome replay stores: registration
uniqueness prevents duplicate accounts, reset-token consumption makes one password
change possible, and Logout is idempotent revocation. A lost login response may
require fresh password verification and a newly issued session; never persist a
plaintext/recoverable session token merely to replay that HTTP response. Business
mutations such as saved-deck/new-game creation instead use the durable non-secret
request/outcome contract in protocol v1.

Auth invalidation while process lives locks network writes and requests login;
same-account login can resume retained immutable requests; changing accounts or
Logout drops them. Process restart has no such queue (D-278). Downloaded read-only
content remains available to its same local account profile pending reauthentication,
not to another signed-in account; explicit Logout deletes private downloads.
