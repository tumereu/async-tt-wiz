# I07a — saved-deck collection management

Delivered D01/D02/D05/D06 normal collection/details, rename, in-deck cover and
confirmed deletion through the existing authenticated service. Shared protocol,
V5 migration, writer-fenced row locks and original request outcomes preserve
account privacy and immutable entries/definitions. Current-state reads after a
mutation avoid installing a stale original retry result. The native collection
has cover/count/name rows, fixed bottom Import, section tabs, personal rotating
drafts, safe errors and same-account refresh. No authoritative local deck store.

Sources: D-189/D-199/D-218/D-221/D-241/D-259, D-293/D-294 and protocol-v1's HTTP
business identity/serialization. Partial I07.collection accounting includes
P-044/P-049, T-399/T-428/T-429/T-483, F18/F19/F29/F38. T-491/T-492/T-542's selected
copy assertions remain I10/I12; I07b owns D08/F21/T-680 inspection. I08 owns contextual
Overwrite/Merge. No whole cross-feature scenario is declared passed here.

## Evidence, including red diagnostics

- `host-junit-initial.json` retains exact initial XML strings: three shared deck,
  22 Android management/save/account and 14 server deck/migration cases. Thirteen
  server cases passed; pagination returned a 503. That entire command stays
  diagnostic. `pagination-and-apk-recheck.json` plus lossless
  `host-junit-rechecks.json` retain its passing one-case recheck with unchanged
  assertion, plus incremental assembly/lint/boundaries. No root cause/fix is claimed
  for that transient. I34 must investigate if it recurs.
- The ten affected management/save host cases pass again after callback-fence
  changes (`controller-recheck.json`). Later selectable-cover presentation has
  build/lint and actual native evidence, not another unchanged host-suite rerun.
- Initial codegen-count, cross-module nullable smart-cast, baseline-oracle and ADB
  peer-observation failures are retained with their narrow corrections. Pairing is
  only read-only observation of the two owned live peers. No controller or grants
  changed. V4-to-V5 and clean/retained real PostgreSQL checks pass.
- Native Appium evidence is **composed from retained segments**, not a wholly green
  initial journey. Early root-button, auth prefill/viewport, modal-tree and
  disabled-parent selectors were corrected in test code. An already-open cover
  draft correctly remains personal rather than tracking a remote cover choice.
  Selectable Material Surface now exports the cover's real selected semantics.
  Process restart correctly returns to collection, then explicitly opens the deck.
  Long-list controls require scrolling. These are not product-data-loss findings.

| Retained native segment | Completed assertions before its reported diagnostic |
| --- | --- |
| `runner-fb49bddbc4ed4919bf440b5c32afdc36-*` | UI-created account/import, exact 6/3 section tabs, both orientations, rename/rotation, remote rename while preserving an unfinished personal draft, first-instance cover choice/rotation on final APK |
| `runner-002ee3fe138d45ef9a3b85a234e215ae-*` and paired `runner-17b87f777b414d7e942c9d3b22d7adc1-*` | Same-account accepted cover without re-import, selected accessibility semantics; X/Keep/native Back cancel deletion |
| `runner-6b3a6667abfe4187adcc4478d9ad4745-*` and paired `runner-1bb78db02d0b495da357ded752ebb7b2-*` | Restart/reopen metadata, Exit without saving, six further UI imports, scrolling seven-deck collection/fixed Import, landscape confirmation and actual accepted deletion on both instances |
| `runner-9ade44d7898042039fee9e8ef0bfba72-*` and paired `runner-0c3ad2b0c1a644f69155d7b102a1e303-*` | **Passing targeted command:** retained deleted collections on both instances, scrolling remaining decks and process restart/reopen after deletion |

`accepted-state.json` independently corroborates only normal UI effects with
read-only SQL: six remaining decks, 18 entries, three immutable definitions, exact
original revision-2 rename/revision-3 Amberglass cover and deletion outcomes.
`artifact-identities.json` pins current relevant sources, fixtures, locks, final
normal APK and actual service image. Original screenshots/XML and every referenced
command output are retained. Earlier outputs are never relabeled new executions.

The normal test defaults to the whole journey. This unit's explicit retained-tail
environment flags exercise missing assertions against already accepted UI state;
they never seed or modify product data. I34 still owes a complete final journey
with the corrected test harness. Existing unchanged artwork fallback/transport
evidence is reused; no real licensed provider was contacted.

## Remaining scope

Only I07a is implemented. I07b, I07 parent reconciliation, I08, independent B2
review, selected lobby/game copy isolation, I33-I36 final workloads/full suite/
compatibility/interface comparison and all external gates remain. The older
NativeShellTest pre-I05 empty-page oracle is updated for the real empty collection;
its complete navigation journey remains final I34 work, not a newly claimed pass.
All owned device/Appium/ADB/Compose resources are cleaned. Build/image caches may
remain. No subagent, parallel writer, loop, deployment or paid resource was started.
