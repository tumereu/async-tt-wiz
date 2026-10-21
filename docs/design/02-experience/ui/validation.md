# UI pilot validation — 2026-10-21

This records documentation-artifact checks, not implemented-app or approved-UI
verification.

- **21 distinct Stitch-generated responsive sources**, plus two alternate scroll
  positions: 23 review states and **46 PNG captures**.
- Exact captured viewports: 412 × 892 portrait and 892 × 412 landscape; no
  screenshot stretching. The Markdown embeds all 46 images.
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
- Compiled PDF has **27 pages**, contains at least all 46 image instances and all
  review-state IDs. Rendered sample pages and page text were checked; no blank
  pages were found. PDF and HTML come from the same Markdown/images.
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
unpictured states and later gameplay/device/accessibility work. This pilot does
not pass the full UI gate or authorize implementation.
