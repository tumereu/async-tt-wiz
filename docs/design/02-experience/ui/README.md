# UI review drafts

These are specification artifacts, not application code or an approved final UI.

- **[Review packet](review.md):** original non-gameplay pilot plus the gameplay
  extension and cumulative D-292–D-294 revisions, with both orientations: 32 primary
  drafts plus two scroll and one faded-feedback continuation, 70 captures total.
  N02 shows navigation;
  P03/P04 show context/deletion, D07 contextual import and D08 full inspection.
  P05 adds named creation. G00 is retired provenance, not a current screen;
  G01–G03 and H01–H03 add the live/history table and controls.
- **[Gameplay layout brief](../gameplay-screen-proposal.md):** owner direction,
  clarified interactions and remaining geometry proposals.
- `sources/` retains raw Stitch output and inert review derivatives; `images/`
  contains captures of the latter. The gameplay derivatives were substantially
  recomposed locally to fix responsive cropping and invented actions; they are
  not claimed pixel-identical to the designs in Stitch. Those repaired references
  were also sent back to Stitch to create six corrected editable cloud revisions.
  The manifest records both cloud versions and retained raw outputs.
- [Artifact validation](validation.md) records checks, corrections and hosting
  limitations without claiming a passed UI or implementation gate.
- **[Cumulative feedback](feedback.md):** preserve every owner decision across
  rounds unless explicitly superseded; read before regeneration. F01–F36 apply;
  the packet's full-screen copy audit lists further deletion suggestions, not
  silently approved removals. Raw Stitch sources/older prompts remain provenance only.
- [HTML](review.html) and [PDF](review.pdf) are generated from the same Markdown. The HTML is
  the phone-friendly reading edition; the PDF is the fixed-layout review edition.

## Review contract

The owner requested both orientations and a Stitch-based non-gameplay pilot
(D-290), then explicitly extended it to gameplay (D-291). Approximate geometry,
icon-versus-text distinctions, visible actions,
state changes and navigation matter now; colors, typography and decorative
styling are provisional. Existing feature contracts still govern behavior.

Reviewing a screen does not approve new product behavior or authorize application
implementation. Nor does this limited packet claim that all gameplay overlays,
accessibility, every transient state or the full UI gate is complete.

## Rebuild

From the repository root, with Pandoc and Google Chrome installed:

```sh
uv run --with playwright --with pypdf scripts/build-ui-review.py
```

The build renders the Markdown, embeds its review images in the HTML, prints the
PDF and checks that every declared screen has both image orientations. It does
not call Stitch, authenticate, generate application code or configure hosting.
The static source mockups are visual design material, not a working app.

**Required after each UI-document edit batch:** recapture affected sources, rebuild
the **full** packet, and publish/verify `review.html` in place at the exact preferred
URL below. The current service serves this repository directory directly, so
rebuilding these files publishes the replacement without a new URL or route.
Compare the served HTML with the rebuilt file and retain all screen sections.
Also rebuild PDF from the same Markdown/images. Never replace the full edition
with a changed-screen subset. Append owner feedback/explicit supersessions to
`feedback.md` before regeneration; carry it forward through every later round.
The manifest's review base URL gives PDF links their phone-accessible addresses;
keep it at the existing publication origin for owner review. The tooling's
`UI_REVIEW_BASE_URL` override is not permission to move publication to another host.
Printing fulfills the self-contained HTML from disk at that address; it is not a
remote-access test.

To recapture both orientations after editing the inert design sources, first run:

```sh
uv run --with playwright scripts/capture-ui-review.py
```

Optional screen IDs restrict a recapture batch, for example
`uv run --with playwright scripts/capture-ui-review.py G01 G02 H01 H02 H03 G03`.
This avoids changing unrelated captures of animation frames while editing one screen.

This blocks HTTP(S) requests and checks that the sources remain inert, their
fonts are local/loaded and their page width fits each viewport. It is an artifact
sanity check, not full interaction, accessibility or Android verification. D-292
checks also reject visible mock OS/title-bar badges, wrong navigation categories/
visibility, scrolling Logout, a split/off-center login card, restored reset-session
copy, main-menu utility/subtitle clutter, wrong browser section order and undersized
or offscreen unscrollable controls. These are static artifact regressions, not a
proof that every earlier feedback decision or future native state is verified.

D-293 checks additionally cover auth top-Back removal, ATW logo, navigation icons
and bare game names, chevrons/no list Reinvite, art-after-quantity, one/all handles,
Overwrite default/no generic replacement selector and the shared-deletion warning.
The full-screen copy audit remains a set of suggestions, not a capture assertion
that every suggested deletion has already been approved or applied.

D-294 checks reject app Back/home arrows globally, misaligned title/hamburger or
missing import/editor title/exit, missing state/date/game-name details, misplaced
lobby Cancel/Reinvite, leaving-Ready copy, per-row Select, wide preparation handles,
cover checkbox, long deck-delete explanations, missing name/reroll and empty U01.
G00 is excluded from the active manifest; provenance remains in `retiredScreens`.
Ordinary buttons remain at least 48 px; the explicitly selected 24 px-wide **1 / ∞**
tap buttons/drag origins are a separate geometry exception with accessible alternatives.
L04/L04b freeze visible/faded result-text endpoints and check unchanged layout;
actual short row fade/slide and ~1–2 s text fade remain native interaction requirements.

## Private review hosting

Serve only this directory through a dedicated Tailscale Serve HTTPS origin,
preserving unrelated existing routes. Never use Funnel/public hosting. The
host must remain running for phone access. Authentication credentials and local
OAuth helper files are outside the repository and outside this served directory.

On the current review host, the document-only HTTP server is a per-user systemd
service named `async-tt-wiz-ui-review.service`, bound to `127.0.0.1:8765`.
The preferred review URL is now:

**<https://desktop-b2kalvm.tail27cccc.ts.net:8443/review.html>**

The PDF is at the same address with `review.pdf` instead of `review.html`.
Tailscale Serve proxies HTTPS port **8443** at `/` to this document-only service.
The original port-443 root OpenCode route and legacy `/ui-review` route remain
unchanged. The owner reported that the old same-origin path opened OpenCode;
the document service did not receive that navigation. A browser-side navigation/
cache/service-worker conflict is a hypothesis, not a proven root cause. A separate
port gives the review its own browser origin without clearing OpenCode data.

The server unit/Tailscale configuration are local review-host setup,
not application or production-deployment scaffolding. Check availability with
`systemctl --user status async-tt-wiz-ui-review.service` and
`tailscale serve status`. The dedicated listener can be removed with
`tailscale serve --https=8443 off` if no longer wanted; do not use a broad Serve
reset/off operation that would affect unrelated routes. Phone access still
requires Tailscale and confirmation from another device; host self-connection
is unavailable here. Nothing was uploaded to Drive or made public.
