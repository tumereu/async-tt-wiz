# UI review drafts

These are specification artifacts, not application code or an approved final UI.

- **[Review packet](review.md):** original non-gameplay pilot plus the gameplay
  extension, with both portrait and landscape views: 27 primary drafts plus two
  scrolled continuations, 58 captures total. G00 remains the original placeholder;
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
not call Stitch, authenticate, generate application code or publish anything.
The static source mockups are visual design material, not a working app.
The manifest's review base URL gives PDF links their phone-accessible addresses;
override `UI_REVIEW_BASE_URL` when rebuilding for another host. Printing fulfills
the self-contained HTML from disk at that address; it is not a remote-access test.

To recapture both orientations after editing the inert design sources, first run:

```sh
uv run --with playwright scripts/capture-ui-review.py
```

Optional screen IDs restrict a recapture batch, for example
`uv run --with playwright scripts/capture-ui-review.py G01 G02 H01 H02 H03 G03`.
This avoids changing unrelated captures of animation frames while editing one screen.

This blocks HTTP(S) requests and checks that the sources remain inert, their
fonts are local/loaded and their page width fits each viewport. It is an artifact
sanity check, not full interaction, accessibility or Android verification.

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
