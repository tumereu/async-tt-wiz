# UI review drafts

These are specification artifacts, not application code or an approved final UI.

- **[Non-gameplay pilot](review.md):** screen/state mockups and interaction notes,
  with both portrait and landscape views: 21 primary drafts plus two scrolled
  continuations, 46 captures total. Gameplay is intentionally a placeholder.
- `sources/` retains the static Stitch design exports; `images/` contains review
  captures. The source manifest records screen identities, prompts and provenance.
- [Artifact validation](validation.md) records checks, corrections and hosting
  limitations without claiming a passed UI or implementation gate.
- [HTML](review.html) and [PDF](review.pdf) are generated from the same Markdown. The HTML is
  the phone-friendly reading edition; the PDF is the fixed-layout review edition.

## Review contract

The owner requested both orientations and a Stitch-based non-gameplay pilot
(D-290). Approximate geometry, icon-versus-text distinctions, visible actions,
state changes and navigation matter now; colors, typography and decorative
styling are provisional. Existing feature contracts still govern behavior.

Reviewing a screen does not approve new product behavior or authorize application
implementation. Nor does this limited pilot claim that gameplay, accessibility,
every transient state or the full UI gate is complete.

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

This blocks HTTP(S) requests and checks that the sources remain inert, their
fonts are local/loaded and their page width fits each viewport. It is an artifact
sanity check, not full interaction, accessibility or Android verification.

## Private review hosting

Serve only this directory through a dedicated Tailscale Serve path, preserving
unrelated existing routes. Never use Funnel/public hosting for the pilot. The
host must remain running for phone access. Authentication credentials and local
OAuth helper files are outside the repository and outside this served directory.

On the current review host, the document-only HTTP server is a per-user systemd
service named `async-tt-wiz-ui-review.service`, bound to `127.0.0.1:8765`.
Tailscale Serve proxies only `/ui-review` to it; the pre-existing root route is
unchanged. The server unit/Tailscale configuration are local review-host setup,
not application or production-deployment scaffolding. Check availability with
`systemctl --user status async-tt-wiz-ui-review.service` and
`tailscale serve status`. Do not use the broad “turn Serve off” command to remove
this route: it would also affect unrelated routes.
