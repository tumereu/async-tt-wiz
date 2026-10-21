# UI review artifact validation — 2026-10-21

This records documentation-artifact checks, not implemented-app or approved-UI
verification.

**Current edition: D-292 owner-feedback revision**, documented below. The original
pilot/gameplay counts and cloud checks in earlier sections are historical.

- The original pilot has **21 distinct Stitch-generated responsive sources**,
  plus two alternate scroll positions: 23 review states and **46 PNG captures**.
  The D-291 extension adds six gameplay sources in the same private Stitch
  project: live, populated Stack/combat, paused/running history, armed
  continuation and explicit Redo menu. Extended total: **27 primary sources,
  29 review states and 58 PNG captures**.
- Exact captured viewports: 412 × 892 portrait and 892 × 412 landscape; no
  screenshot stretching. The Markdown embeds all 58 images.
- Sources were recaptured with all HTTP(S) requests blocked, local fonts loaded,
  no executable scripts/event handlers and the body marked inert. Both page
  widths fit the viewport. This does not validate native semantics or gestures.
- Generated action/input targets were normalized to at least 48 logical px.
  Control-bound audits found no remaining offscreen control without a scrollable
  ancestor. This is a simple geometry check, not exhaustive reachability,
  contrast, TalkBack or accessibility validation.
- Contact sheets were visually inspected in both orientations. The original
  Stitch exports remain as text; presentation/wording repairs are recorded in
  `manifest.json` and summarized in the review packet.
- Compiled HTML contains all embedded images, loads without broken-image/page
  errors and fits a 412 px-wide phone reading viewport.
- The original PDF had 27 pages; the extension is rebuilt from the same updated
  Markdown/images as the HTML. Its build checks at least all 58 image instances
  and every declared review-state ID. See the gameplay validation results below
  for the final page count and visual/capture checks.
- Relative documentation links, Python syntax and staged whitespace are checked
  before saving. These scripts are document tooling, not an app/test scaffold.
- The document-only loopback HTTP server responds successfully for HTML, PDF and
  image files. A separate `/ui-review` Tailscale Serve proxy preserves the existing
  root proxy. Direct file serving was denied by host permissions; the loopback
  proxy is the non-root alternative. The tailnet HTTPS endpoint refuses a
  self-connection from this WSL host; **phone/other-peer access is not independently
  verified here**. The owner should open the supplied URL from the phone.
- No billing was attached, no public Funnel was enabled and no credentials were
  placed in the repository or served directory. Availability depends on WSL,
  Tailscale and the per-user review service remaining running.

The [packet's coverage section](review.md#coverage-and-limits) explicitly lists
unpictured gameplay overlays/states and later device/accessibility work. This
packet does not pass the full UI gate or authorize implementation. Static inert
sources cannot verify hold recognition, pause-on-drag, real server Undo or any
native gesture; T-674/T-675 specify future normal-app verification instead.

## Gameplay extension results — D-291

- Direct Stitch `get_screen` reads verified all six initial gameplay resources
  and six corrected editable cloud revisions in project `6617750535460836476`.
  These are two cloud versions of six states, not 12 extra captured states.
  The list endpoint returned no inventory, so the
  direct named-resource checks, not an invented successful list, establish this.
- All six original outputs are retained as text. The captured derivatives are
  **substantially locally recomposed**, using G01's gray/teal abstract-card
  vocabulary rather than pretending to be pixel-identical Stitch exports.
  Original drafts clipped historical controls, cropped landscape boards and
  invented Pass/concession/Declare Action wording. The derivatives repair those
  defects, show both battlefield halves, preserve the fixed own Hand and separate
  read-only history chrome. The manifest records original/revised cloud IDs and
  derivation. The corrected local reference HTML/CSS was supplied to the cloud
  edit pass; captures remain locally checked derivatives, not a pixel-parity
  assertion about cloud revisions.
- All 12 gameplay captures were inspected together in portrait/landscape contact
  sheets. The sparse initial framing shows all six battlefield rows; G02 adds
  two combat rows. **Short landscape produces small abstract card thumbnails**;
  this is a structural composition for review, not approval of a readable minimum
  zoom or native card hit-target sizes. Larger cards with vertical pan, expanded
  hands, dense states and camera bounds remain explicit UI review work.
- Offline recapture of the six repaired sources passed at the declared viewports:
  inert bodies, no scripts, no HTTP(S) assets, locally loaded icon font and no
  horizontal page overflow. A separate control audit found no button below
  48 × 48 logical px and no offscreen button lacking a scroll route, in either
  orientation. These are artifact geometry checks, not TalkBack/device tests.
- Rebuilt packet: **29 review states, 58 image instances minimum, 34 PDF pages**.
  Build validation found every state ID, correct PNG sizes and phone-width HTML
  without broken images/page errors. PDF text checks found no near-blank pages;
  gameplay headings are on pages 27–32. Rendered G01/H01 pages were inspected.
- Loopback HTML, PDF, gameplay image and review CSS each returned HTTP 200.
  The existing tailnet-only `/ui-review` proxy and unrelated root proxy remain
  unchanged. Phone/other-peer HTTPS access still needs the owner's check.
- Original pilot captures/audits are preserved rather than saving unrelated
  animation-frame churn. The capture helper now accepts optional screen IDs for
  targeted recapture. Newly downloaded fonts unused by the normalized sources
  are not included in the served packet. No cloud credentials enter these files.

## Review-link repair — dedicated private origin

The owner reported that the port-443 `/ui-review` links opened OpenCode instead.
The loopback document server still returned the correct HTML/PDF, but its logs
showed no corresponding remote document request. This does not establish an HTTP
redirect or prove a particular browser bug. Cached same-origin app navigation or
a service worker is a possible explanation.

- Added **tailnet-only HTTPS port 8443**, proxying `/` to `127.0.0.1:8765`.
  Different ports are different browser origins, isolating the review from the
  root OpenCode app without clearing that app's browser data.
- Serve configuration confirms both original port-443 handlers are unchanged:
  `/` → `127.0.0.1:49374`, `/ui-review` → `127.0.0.1:8765`. Only the separate
  port-8443 document listener was added. No Funnel, public hosting, account/OAuth
  change or Drive upload was performed.
- Updated `reviewBaseUrl` and rebuilt the packet so PDF link annotations use
  `https://desktop-b2kalvm.tail27cccc.ts.net:8443/`, not the problematic old path.
- Loopback HTML/PDF availability and the new Serve configuration are checked.
  This host still cannot independently verify its own tailnet HTTPS endpoint;
  **the owner must try the new port-8443 URL from the phone/browser**. Do not
   describe that device-level check as already successful.

## Owner-feedback iteration — D-292

- Added the durable [cumulative feedback record](feedback.md), preserving D-290/
  D-291 and this round's F01–F10 plus both explicit clarification answers.
  `AGENTS.md` requires same-URL full publication and carry-forward in every round.
- Revised all **27 retained local design derivatives**, not the raw cloud exports,
  for fullscreen and the applicable authenticated shell. Added a locally composed
  N02 open-navigation source: **28 primary sources + two scroll continuations =
  30 review states / 60 captures**. No new Stitch/cloud revision is claimed.
- Offline capture passed for all 30 states in both orientations: inert body,
  no executable scripts, no HTTP(S) dependencies, loaded local fonts and no page
  horizontal overflow. Targets are at least 48 × 48; no offscreen audited control
  lacks a scrollable ancestor. A first-pass sidebar-induced L03 overflow and
  D01 grid collapse were corrected before the final recapture.
- Added static artifact regression checks for D-292: hidden OS allowances/title
  badges; exact Menu/Games categories and indented main destinations; appropriate
  persistent/drawer visibility; fixed Logout under an artificially long scrolling
  list; identical 380 px centered sign-in card and exact name/subtitle; no reset
  session explanations; no main-menu utility/subtitle clutter; Games then
  Invitations in one browser list. This supplements, not replaces, future T-397/
  T-676 native interaction/rotation/lifecycle/cutout/accessibility verification.
- Inspected both-orientation contact sheets and enlarged key screens. The sign-in
  card is the same composition in both orientations; **short landscape scrolls**,
  so lower sign-in links are not visible in the initial capture. Navigation's
  bottom Logout remains visible while the game list scrolls. P01 landscape needs
  content scrolling to reach Invitations after Games. Deck details/import and
  gameplay retain their original feature routes. Redundant helper panels were
  removed rather than leaving empty landscape explanation columns.
- Full HTML/PDF rebuilt from the same Markdown/images: **30 states, 60 embedded
  captures, 35 PDF pages**. Build checks include image dimensions, declared screen
  IDs in PDF text, decoded HTML images and no phone-width reading overflow.
- The same document service serves this repository's UI directory directly; no
  upload, new URL, public host or route change is needed. The served full HTML is
  compared byte-for-byte with `review.html`, with loopback HTTP availability for
  HTML/PDF/assets and unchanged tailnet-only port-8443 proxy configuration.
  Self-connection/another-device HTTPS is not established by those local checks;
  the browser refresh remains at the exact existing private review URL.
- Local Markdown-link, Python syntax and whitespace checks apply before saving.
  These are documentation tooling/artifact checks, not application implementation
  or a passed UI/review gate.
