# UI review artifact validation — 2026-10-21

This records documentation-artifact checks, not implemented-app or approved-UI
verification.

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
