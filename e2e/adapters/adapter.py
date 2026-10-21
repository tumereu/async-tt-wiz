#!/usr/bin/env python3
"""Test-only synthetic HTTP source and SMTP sink. No application/backend imports.

One event loop serializes store commits and reset without suspension. SMTP sessions
are fenced by a generation as well as closed on reset. Never make outbound calls.
"""
import argparse
import asyncio
import base64
import fcntl
import hashlib
import hmac
import json
import os
from pathlib import Path
import re
import signal
import sqlite3
import sys
from urllib.parse import urlsplit

CATALOGS = {"Meta.json", "AllPrintings.json", "AllPrintings.json.gz", "AllPrintings.json.gz.sha256"}
UUID = r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
MAX_MAIL = 8 * 1024 * 1024


def encoded(value):
    return (json.dumps(value, sort_keys=True, indent=2, ensure_ascii=False) + "\n").encode()


def digest(value):
    return hashlib.sha256(value).hexdigest()


class Store:
    def __init__(self, directory, instance):
        directory.mkdir(mode=0o700, parents=True, exist_ok=True)
        self.lock = (directory / "owner.lock").open("a+")
        fcntl.flock(self.lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        self.db = sqlite3.connect(directory / "state.sqlite", timeout=2)
        self.db.execute("PRAGMA synchronous=FULL")
        with self.db:
            self.db.execute("CREATE TABLE IF NOT EXISTS settings (id INTEGER PRIMARY KEY CHECK(id=1), value TEXT NOT NULL)")
            self.db.execute("CREATE TABLE IF NOT EXISTS messages (id INTEGER PRIMARY KEY, sender TEXT NOT NULL, recipients TEXT NOT NULL, body BLOB NOT NULL)")
            value = self.db.execute("SELECT value FROM settings WHERE id=1").fetchone()
            if value is None:
                self.state = {"instance": instance, "generation": 0, "versions": {"prism": 1, "quilt": 1},
                              "sourceFault": "ok", "mailFault": "ok"}
                self.save()
            else:
                self.state = json.loads(value[0])
                if self.state["instance"] != instance:
                    raise ValueError("wrong store owner")
                validate_controls(self.state)
        self.probe()

    def save(self):
        self.db.execute("INSERT OR REPLACE INTO settings VALUES (1, ?)", (encoded(self.state).decode(),))

    def probe(self):
        # Exercise the writable store, not merely an open socket/SELECT 1.
        with self.db:
            self.save()
            return self.db.execute("SELECT count(*) FROM messages").fetchone()[0]

    def messages(self):
        return [{"id": i, "sender": s, "recipients": json.loads(r), "bodyBase64": base64.b64encode(b).decode()}
                for i, s, r, b in self.db.execute("SELECT id, sender, recipients, body FROM messages ORDER BY id")]

    def close(self):
        self.db.close()
        self.lock.close()


def validate_controls(value):
    if (value["versions"].keys() != {"prism", "quilt"}
            or any(type(v) is not int or v not in {1, 2} for v in value["versions"].values())
            or value["sourceFault"] not in {"ok", "unavailable", "corrupt-gzip", "truncate-gzip"}
            or value["mailFault"] not in {"ok", "temporary", "permanent"}):
        raise ValueError("invalid controls")


class Adapter:
    def __init__(self, fixtures, origin, store, token):
        parsed = urlsplit(origin)
        if (parsed.scheme not in {"http", "https"} or not parsed.hostname or parsed.username is not None
                or parsed.password is not None or parsed.path or parsed.query or parsed.fragment
                or not 1 <= (parsed.port or 80) <= 65535):
            raise ValueError("invalid origin")
        if not re.fullmatch(r"[0-9a-f]{64}", token):
            raise ValueError("invalid harness token")
        self.token, self.store = token, store
        self.public_sessions, self.smtp_sessions = set(), set()
        manifest = json.loads((fixtures / "manifest.json").read_bytes())
        self.files = {}
        allowed = allowed_fixture_paths(manifest)
        for relative, expected in manifest["files"].items():
            if relative not in allowed:
                raise ValueError("unexpected fixture path")
            path = fixtures / relative
            if path.is_symlink() or not path.resolve().is_relative_to(fixtures.resolve()):
                raise ValueError("unsafe fixture path")
            raw = path.read_bytes()
            if digest(raw) != expected:
                raise ValueError("fixture digest mismatch")
            # Rebind only provider image metadata. Catalog/compression/art stay exact.
            if "/scryfall/cards/" in relative:
                obj = json.loads(raw)
                for face in obj.get("card_faces", [obj]):
                    face["image_uris"] = {size: origin + uri.removeprefix(manifest["origin"])
                                           for size, uri in face["image_uris"].items()
                                           if uri.startswith(manifest["origin"] + "/")}
                    if not face["image_uris"]:
                        raise ValueError("missing fixture origin")
                raw = encoded(obj)
            if "import-cases" not in relative:
                self.files[relative] = raw
        self.inventory = {"origin": origin, "fixtureManifestSha256": digest((fixtures / "manifest.json").read_bytes()),
                          "files": {p: digest(b) for p, b in sorted(self.files.items())}}
        self.listeners = []

    def reset(self):
        old = self.store.state
        baseline = {**old, "generation": old["generation"] + 1, "versions": {"prism": 1, "quilt": 1},
                    "sourceFault": "ok", "mailFault": "ok"}
        # No await between fence/store replacement/session close. Any resumed old
        # DATA must fail its generation check even if EOF was already buffered.
        with self.store.db:
            self.store.db.execute("DELETE FROM messages")
            self.store.db.execute("INSERT OR REPLACE INTO settings VALUES (1, ?)", (encoded(baseline).decode(),))
        self.store.state = baseline
        for writer in self.public_sessions | self.smtp_sessions:
            writer.close()

    async def line(self, reader, limit=8192):
        value = await asyncio.wait_for(reader.readline(), 10)
        if len(value) > limit or not value.endswith(b"\r\n"):
            raise ValueError("invalid line")
        return value

    async def http(self, reader, writer, admin=False):
        sessions = set() if admin else self.public_sessions
        sessions.add(writer)
        try:
            async with asyncio.timeout(15):
                request = (await self.line(reader))[:-2].decode("ascii").split(" ")
                if len(request) != 3 or request[2] != "HTTP/1.1":
                    raise ValueError("invalid request")
                method, path, _ = request
                headers = {}
                for _ in range(64):
                    line = await self.line(reader)
                    if line == b"\r\n":
                        break
                    name, value = line.decode("ascii").split(":", 1)
                    name = name.lower()
                    if name in headers:
                        raise ValueError("duplicate header")
                    headers[name] = value.strip()
                else:
                    raise ValueError("too many headers")
                if "transfer-encoding" in headers:
                    raise ValueError("unsupported framing")
                length = int(headers.get("content-length", "0"))
                if not 0 <= length <= 65536:
                    raise ValueError("body too large")
                body = await reader.readexactly(length)
                status, mime, data, extra = self.route(method, path, headers, body, admin)
                # Logs deliberately contain neither request target/headers nor body.
                response = f"HTTP/1.1 {status} Result\r\nContent-Type: {mime}\r\nContent-Length: {len(data)}\r\nConnection: close\r\nCache-Control: no-store\r\n{extra}\r\n".encode()
                writer.write(response)
                if method != "HEAD":
                    truncated = (not admin and status == 200 and path.endswith("/AllPrintings.json.gz")
                                 and self.store.state["sourceFault"] == "truncate-gzip")
                    writer.write(data[:len(data) // 2] if truncated else data)
                await writer.drain()
        except (ValueError, UnicodeError, OSError, EOFError, asyncio.IncompleteReadError, TimeoutError, sqlite3.Error):
            # Content-safe protocol errors; close rather than echo attacker input.
            pass
        finally:
            sessions.discard(writer)
            writer.close()
            await writer.wait_closed()

    def route(self, method, path, headers, body, admin):
        def reply(status, value):
            return status, "application/json", encoded(value), ""

        if admin:
            if not hmac.compare_digest(headers.get("authorization", ""), "Bearer " + self.token):
                return reply(403, {"error": "forbidden"})
            if method == "GET" and path in {"/health/live", "/health/ready"}:
                try:
                    self.store.probe()
                    ready = all(s.is_serving() for s in self.listeners) and len(self.listeners) == 3
                    ready &= self.store.state["sourceFault"] == "ok" and self.store.state["mailFault"] == "ok"
                    return reply(200 if ready or path.endswith("live") else 503, {"status": "up" if ready else "unavailable"})
                except sqlite3.Error:
                    return reply(503, {"status": "unavailable"})
            if method == "GET" and path == "/admin/state":
                return reply(200, {**self.store.state, "messageCount": self.store.probe(),
                                   "smtpConnections": len(self.smtp_sessions)})
            if method == "GET" and path == "/admin/inventory":
                return reply(200, self.inventory)
            if method == "GET" and path == "/admin/messages":
                return reply(200, self.store.messages())
            if method == "POST" and path in {"/admin/reset", "/admin/control"}:
                try:
                    value = json.loads(body)
                except (ValueError, UnicodeError):
                    return reply(400, {"error": "invalid JSON"})
                if not isinstance(value, dict) or value.get("instance") != self.store.state["instance"]:
                    return reply(409, {"error": "wrong owner"})
                if path.endswith("reset"):
                    if set(value) != {"instance"}:
                        return reply(400, {"error": "invalid reset"})
                    self.reset()
                else:
                    if not set(value) <= {"instance", "versions", "sourceFault", "mailFault"}:
                        return reply(400, {"error": "invalid control"})
                    candidate = {**self.store.state, **value}
                    try:
                        validate_controls(candidate)
                    except (ValueError, TypeError, KeyError, AttributeError):
                        return reply(400, {"error": "invalid control"})
                    with self.store.db:
                        self.store.db.execute("INSERT OR REPLACE INTO settings VALUES (1, ?)", (encoded(candidate).decode(),))
                    self.store.state = candidate
                return reply(200, {**self.store.state, "messageCount": self.store.probe()})
            return reply(404, {"error": "not found"})

        if method not in {"GET", "HEAD"}:
            return reply(405, {"error": "method not allowed"})
        # Strict allowlist, not a filesystem server. No percent decoding, query,
        # directory listing, Host-based URL rewriting or generic /admin fallback.
        match = re.fullmatch(r"/(prism|quilt)/(?:v([12])/)?(.+)", path)
        if not match:
            return reply(404, {"error": "not found"})
        dataset, version, name = match.groups()
        version = int(version) if version else self.store.state["versions"][dataset]
        if re.fullmatch(r"scryfall/cards/" + UUID + r"(?:\.json)?", name):
            name = name if name.endswith(".json") else name + ".json"
        elif name not in CATALOGS and not re.fullmatch(r"art/" + UUID + r"\.png", name):
            return reply(404, {"error": "not found"})
        fault = self.store.state["sourceFault"]
        if fault == "unavailable":
            return 503, "application/json", encoded({"error": "source unavailable"}), "Retry-After: 1\r\n"
        raw = self.files.get(f"{dataset}/v{version}/{name}")
        if raw is None:
            return reply(404, {"error": "not found"})
        if name.endswith(".png"):
            mime = "image/png"
        elif name.endswith(".gz"):
            mime = "application/gzip"
        elif name.endswith(".sha256"):
            mime = "text/plain; charset=utf-8"
        else:
            mime = "application/json"
        extra = 'ETag: "' + digest(raw) + '"\r\n'
        if name == "AllPrintings.json.gz":
            if fault == "corrupt-gzip":
                raw = b"not a gzip stream\n"
            elif fault == "truncate-gzip":
                # Advertise full length but close after half to exercise interrupted
                # transfer, not a smaller successful download with a changed digest.
                return 200, mime, raw, extra
        return 200, mime, raw, extra

    async def smtp(self, reader, writer):
        self.smtp_sessions.add(writer)
        generation = self.store.state["generation"]

        async def respond(message):
            writer.write(message.encode("ascii") + b"\r\n")
            await writer.drain()

        sender, recipients, greeted = None, [], False
        try:
            async with asyncio.timeout(120):
                await respond("220 synthetic.invalid ESMTP sink")
                while not writer.is_closing():
                    raw = await self.line(reader)
                    command, _, argument = raw[:-2].decode("ascii").partition(" ")
                    command = command.upper()
                    if command in {"EHLO", "HELO"} and argument:
                        greeted, sender, recipients = True, None, []
                        await respond("250-synthetic.invalid\r\n250-8BITMIME\r\n250 SIZE " + str(MAX_MAIL))
                    elif command == "QUIT":
                        await respond("221 bye")
                        break
                    elif command == "NOOP":
                        await respond("250 ok")
                    elif command == "RSET":
                        sender, recipients = None, []
                        await respond("250 ok")
                    elif command == "MAIL" and greeted:
                        match = re.fullmatch(r"FROM:<([^<>\s]*)>(?: (?:SIZE=[0-9]+|BODY=8BITMIME|BODY=7BIT))*", argument, re.I)
                        sender, recipients = (match[1] if match else None), []
                        await respond("250 ok" if match else "501 invalid envelope")
                    elif command == "RCPT" and sender is not None:
                        match = re.fullmatch(r"TO:<([^<>\s]+@[^<>\s]+)>", argument, re.I)
                        if match and len(recipients) < 100:
                            recipients.append(match[1])
                            await respond("250 captured locally only")
                        else:
                            await respond("501 invalid recipient")
                    elif command == "DATA" and sender is not None and recipients:
                        fault = self.store.state["mailFault"]
                        if fault != "ok":
                            await respond("451 temporary sink failure" if fault == "temporary" else "550 permanent sink failure")
                            sender, recipients = None, []
                            continue
                        await respond("354 end with dot")
                        data = bytearray()
                        while True:
                            line = await self.line(reader, 65536)
                            if line == b".\r\n":
                                break
                            data.extend(line[1:] if line.startswith(b"..") else line)
                            if len(data) > MAX_MAIL:
                                await respond("552 message too large")
                                return
                        if generation != self.store.state["generation"] or writer.is_closing():
                            return
                        with self.store.db:
                            self.store.db.execute("INSERT INTO messages(sender, recipients, body) VALUES (?, ?, ?)",
                                                  (sender, json.dumps(recipients), bytes(data)))
                        await respond("250 stored locally")
                        sender, recipients = None, []
                    else:
                        # AUTH/STARTTLS/VRFY/EXPN and unknown commands cannot relay,
                        # authenticate, resolve DNS or expose captured deliveries.
                        await respond("502 unsupported command or sequence")
        except (ValueError, UnicodeError, OSError, EOFError, TimeoutError, sqlite3.Error):
            pass
        finally:
            self.smtp_sessions.discard(writer)
            writer.close()
            await writer.wait_closed()


def allowed_fixture_paths(manifest):
    result = set()
    for entry in manifest["datasets"]:
        base = entry["base"]
        if not re.fullmatch(r"(?:prism|quilt)/v[12]", base):
            raise ValueError("unsafe base")
        result |= {base + "/" + p for p in CATALOGS | {"import-cases.json"}}
        result |= {p for p in manifest["files"] if re.fullmatch(re.escape(base) + r"/(?:art/" + UUID + r"\.png|scryfall/cards/" + UUID + r"\.json)", p)}
    return result


async def serve(args):
    token = args.token_file.read_text().strip()
    store = Store(args.state_dir, args.instance)
    app = Adapter(args.fixtures, args.origin, store, token)
    loop = asyncio.get_running_loop()
    loop.set_exception_handler(lambda *_: print("adapter connection failed", file=sys.stderr, flush=True))
    stopped = asyncio.Event()
    for signum in (signal.SIGINT, signal.SIGTERM):
        loop.add_signal_handler(signum, stopped.set)
    try:
        app.listeners.append(await asyncio.start_server(app.http, args.bind, args.http_port, limit=65536))
        app.listeners.append(await asyncio.start_server(lambda r, w: app.http(r, w, True), args.admin_bind, args.admin_port, limit=65536))
        app.listeners.append(await asyncio.start_server(app.smtp, args.smtp_bind, args.smtp_port, limit=65536))
        store.probe()
        print("synthetic adapters started", flush=True)
        await stopped.wait()
    finally:
        for server in app.listeners:
            server.close()
            await server.wait_closed()
        for writer in app.public_sessions | app.smtp_sessions:
            writer.close()
        store.close()
        print("synthetic adapters stopped", flush=True)


def main():
    os.umask(0o077)
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fixtures", type=Path, required=True)
    parser.add_argument("--origin", required=True)
    parser.add_argument("--state-dir", type=Path, required=True)
    parser.add_argument("--instance", required=True)
    parser.add_argument("--token-file", type=Path, required=True)
    parser.add_argument("--bind", default="127.0.0.1")
    parser.add_argument("--admin-bind", default="127.0.0.1")
    parser.add_argument("--smtp-bind", default="127.0.0.1")
    parser.add_argument("--http-port", type=int, default=8080)
    parser.add_argument("--admin-port", type=int, default=8081)
    parser.add_argument("--smtp-port", type=int, default=2525)
    args = parser.parse_args()
    try:
        if not re.fullmatch(r"[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}", args.instance):
            raise ValueError("invalid instance")
        asyncio.run(serve(args))
    except Exception:
        # Never print exception text: it could contain input paths/token/mail data.
        print("synthetic adapters failed", file=sys.stderr)
        raise SystemExit(1)


if __name__ == "__main__":
    main()
