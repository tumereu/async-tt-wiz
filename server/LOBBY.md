# Contacts, game lists and invitations

Authenticated, no-store JSON routes use the writer-epoch transaction boundary.
HTTP creates the first invitation; existing-game mutations use the
[ordered WebSocket service](GAMES.md), not a competing HTTP write path.

| Route | Behavior |
| --- | --- |
| `GET /v1/users?username=...` | Literal normalized username prefix, at most 50 results excluding self; no email search/projection |
| `GET /v1/contacts?after=UUID` | Own unilateral contacts |
| `POST /v1/contacts` | `{requestId,accountId}`; add another real account idempotently, without reciprocal approval |
| `POST /v1/invitations` | `{requestId,recipient,gameName}`; recipient must be a contact; atomically create game, preparation and first invitation |
| `GET /v1/games?after=UUID` | Own games as inviter or accepted recipient, including pending/declined inviter lobbies |
| `GET /v1/invitations?after=UUID` | Recipient's pending invitations; no age expiry |
| `GET /v1/games/{id}/head` | Current username-only metadata for inviter/accepted recipient; missing/foreign contexts give the same 404 |

Lists use 100-row keyset pages; follow `next` until null. Game summaries retain
exact name, server UTC date, participants, preparation/invitation IDs, lifecycle
and decimal revision. Duplicate names are allowed. Bodies are bounded at 128 KiB.

Creation retains its original response atomically. Retry the same immutable
request after a lost response; changing account/kind/intent under that ID conflicts
without disclosing another outcome. Native drafts and uncertain originals are
volatile; process restart reads server state rather than recreating requests.

```sh
./scripts/gradle.sh :server:test --tests '*LobbyHttpTest'
```
