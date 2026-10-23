#!/usr/bin/env bash
# Local fixture adapter; normally started/stopped by dev-server.sh.
set -euo pipefail
cd "$(dirname "$0")/.."
exec python3 -u - "$PWD/tests/fixtures/catalogs" <<'PY'
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from io import BytesIO
from pathlib import Path
from urllib.parse import urlsplit
import sys

root = Path(sys.argv[1])
origin = "http://127.0.0.1:18081"


class Catalog(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(root), **kwargs)

    def translate_path(self, path):
        path = urlsplit(path).path
        if path.startswith("/api/v5/"):
            path = "/prism/v1/" + path.removeprefix("/api/v5/")
            if path.startswith("/prism/v1/scryfall/cards/") and not path.endswith(".json"):
                path += ".json"
        return super().translate_path(path)

    def send_head(self):
        path = Path(self.translate_path(self.path))
        if path.is_file() and path.suffix == ".json" and "scryfall" in path.parts:
            # Only art metadata has absolute URLs; catalog/gzip/checksum stay byte-identical.
            body = path.read_bytes().replace(b"http://synthetic-source:8080", origin.encode())
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            return BytesIO(body)
        return super().send_head()


print(f"Synthetic prism/v1 catalog: {origin}/api/v5/", flush=True)
ThreadingHTTPServer(("127.0.0.1", 18081), Catalog).serve_forever()
PY
