# Contacts and named creation — I09a

Authenticated, no-store JSON routes, sharing the existing writer-epoch transaction
and global `request_identity` / atomic `account_request` business outcomes:

| Route | Body / result |
| --- | --- |
| `GET /v1/users?username=...` | Literal username-key prefix; pinned Unicode 15.1 NFC/case-fold as registration, 1–64 scalar query, at most 50 username/ID results, excluding self. Refine the query for more specific results. Never email search/projection. |
| `GET /v1/contacts?after=UUID` | Own unilateral contacts, 100-row ID keyset pages with `next`; client downloads all pages. |
| `POST /v1/contacts` | `{requestId, accountId}`; add a real other account, idempotently. No reciprocal approval/addition. |
| `POST /v1/invitations` | `{requestId, recipient, gameName}`; recipient must be an added contact. Atomically create persistent game, preparation-round ID, first invitation and original response. |
| `GET /v1/games?after=UUID` | Own current games (inviter, or accepted recipient), including pending/declined inviter lobbies. |
| `GET /v1/invitations?after=UUID` | Recipient's pending invitations. No age expiry. |
| `GET /v1/games/{id}/head` | Current username-only lobby metadata for inviter/accepted recipient. Pending recipients see invitation summaries, not an accepted lobby. Foreign/missing contexts return the same 404. |

Game lists also use 100-row keyset pages. Summaries retain game ID, exact name,
server UTC creation instant, participant IDs/usernames, preparation round,
invitation attempt ID/state, lifecycle and decimal revision. Name is nonblank,
well-formed Unicode without NUL, at most 64 KiB UTF-8; equal names are allowed.
The creation response persists once; retries do not regenerate IDs/name/date or
replace later game state. Changed account/kind/intent conflicts return 409 without
the original private outcome. JSON bodies are bounded at 128 KiB; 401 invalidates
the native credential through the normal reauthentication route.

`V6__contacts_and_named_games.sql` is an explicit forward migration, never run by
HTTP startup. Real generated jOOQ bindings remain server-only. Native drafts,
suggestions, searches and same-process uncertain request identities are volatile;
Logout/account switching clears/fences them, same-account reauthentication keeps
them. A lost creation response freezes that intent for Retry, rather than silently
creating another game after editing. Process restart reads the server; no outgoing
journal or authoritative local game is added.

I09b1 supplies [Accept/Decline/Reinvite over the ordered socket/durable game inbox](GAMES.md).
There are deliberately no competing existing-game HTTP writes. Native decisions
remain I09b2; preparation/Ready/start/cancellation remain I10–I12. The current
waiting surface displays actual state and plainly labels unavailable preparation.
I34–I36 retain broad qualification, compatibility and final UI comparison.

Focused checks:

```sh
./scripts/gradle.sh --no-daemon --console=plain :protocol:jvmTest --tests '*LobbyTest' \
  :server:test --tests '*LobbyHttpTest' :androidApp:testDebugUnitTest --tests '*LobbyControllerTest'
```
