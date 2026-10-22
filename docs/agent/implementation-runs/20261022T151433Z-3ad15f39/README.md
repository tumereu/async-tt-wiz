# I07b — reusable deck-context full inspection

Delivered from saved mainboard/sideboard rows and resolved import rows through the
normal APK. The independent thumbnail target is at least 48 × 48 dp; the whole row
is a larger alternative with a named accessibility action. Inspection captures the
retained definition/printing faces, keeps full Unicode text/default values, and
offers face navigation, lazy large art, enlargement/panning and text fallback.
Close/native Back changes no quantity, section, deck metadata or import draft.
The existing rotation-retained controllers own this personal state; their existing
Logout/exit cleanup clears it. No inspection event, deck write, new catalog lookup
or gameplay permission is added. The preview/save face mapping is shared unchanged.

## Affected checks and evidence limits

- `host-junit.json`: 17 focused controller/save/transport checks, including four
  new inspection checks. Captured definitions work without a public catalog;
  close/Back/clear keep drafts and accepted sections/quantities intact, never write
  a deck, and cannot inspect an unattached definition. Shared mapping retains exact
  face text/defaults and explicit provider face indices.
- The original native command is **diagnostic**: the real preview/save/saved-face
  journey completed through large-text checking, but cleanup attempted to put the
  literal `null` into Android's originally absent `font_scale`. That failed before
  restart. The repaired test deletes an originally absent setting instead; its
  separate green retained restart check restores the setting and completes reopen
  assertions without repeating accepted imports/saves.
- Capture inspection then exposed intrinsic-size artwork instead of full-scene
  sizing. `CardArtwork` now fills its allocated scene; final green native bounds/
  both-face/rotation/zoom/large-text checks and two actual API29 production-loader
  instrumentation tests check that repair. The latter also rechecks delayed HTTP
  rebind/disposal, exact hosted front/back art, decode/cache and text fallback.
  The old native captures are not evidence of full-sized final artwork.
- `input-identities.json` pins final sources/fixtures/locks/APKs;
  `environment-inputs.json` records the reused local service/adapter images. No
  backend change or image rebuild, full suite, clean build or platform fault matrix
  was needed. All resources are synthetic and locally owned. Original command
  output and XML are retained; any CRLF/trailing-space XML is losslessly enveloped.

## Sources and remaining obligations

Partial I07.collection scope: P-044/P-049, T-399/T-680, F21 and D-293; primary
[deck inspection contract](../../../design/03-contracts/accounts-decks-and-lobby.md),
[D08 reference](../../../design/02-experience/ui/review.md#d08--full-cardart-inspection)
and [acceptance](../../../design/05-verification/acceptance.md).
This is not a whole cross-feature scenario pass. Preparation rows/Ready remain
I10/I12; hidden in-game disclosure and events remain I17/I18. I07 parent closure,
I08 contextual Overwrite/Merge, independent grouped B2 review, I33-I36 performance/
compatibility/full-suite/complete interface-to-mock comparison and all external
release gates remain. No extra review is added after this child. Final I34 should
run the corrected complete native test; this unit composes actual initial segments
and fresh affected rechecks rather than claiming one wholly green initial command.
