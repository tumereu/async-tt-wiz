#!/usr/bin/env python3
"""Host-only administration of isolated adapters; never used by product clients."""
import argparse
import http.client
import json
from pathlib import Path
import sys
import time
from urllib.parse import urlsplit


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--url", required=True, help="private harness HTTP origin, not the source origin")
    parser.add_argument("--token-file", required=True, type=Path)
    parser.add_argument("--instance", help="required exact owner for reset/control")
    parser.add_argument("--timeout", type=float, default=15)
    parser.add_argument("command", choices=["wait", "state", "inventory", "messages", "reset", "control"])
    parser.add_argument("--prism-version", type=int, choices=[1, 2])
    parser.add_argument("--quilt-version", type=int, choices=[1, 2])
    parser.add_argument("--source-fault", choices=["ok", "unavailable", "corrupt-gzip", "truncate-gzip"])
    parser.add_argument("--mail-fault", choices=["ok", "temporary", "permanent"])
    args = parser.parse_args()
    try:
        target = urlsplit(args.url)
        if (target.scheme != "http" or not target.hostname or target.username is not None or target.password is not None
                or target.path or target.query or target.fragment or not 0 < args.timeout <= 120):
            raise ValueError("invalid private admin origin")
        token = args.token_file.read_text().strip()
        body = None
        if args.command in {"reset", "control"}:
            if not args.instance:
                raise ValueError("owner required")
            body = {"instance": args.instance}
            if args.command == "control":
                if (args.prism_version is None) != (args.quilt_version is None):
                    raise ValueError("both dataset versions required")
                if args.prism_version is not None:
                    body["versions"] = {"prism": args.prism_version, "quilt": args.quilt_version}
                for name in ["source", "mail"]:
                    value = getattr(args, name + "_fault")
                    if value is not None:
                        body[name + "Fault"] = value
        path = "/health/ready" if args.command == "wait" else "/admin/" + args.command
        deadline = time.monotonic() + args.timeout
        while True:
            conn = http.client.HTTPConnection(target.hostname, target.port or 80,
                                              timeout=max(.01, min(2, deadline - time.monotonic())))
            try:
                conn.request("GET" if body is None else "POST", path,
                             body=None if body is None else json.dumps(body),
                             headers={"Authorization": "Bearer " + token})
                response = conn.getresponse()
                raw = response.read()
                if response.status == 200:
                    print("ready" if args.command == "wait" else json.dumps(json.loads(raw), indent=2))
                    return
                if response.status in {403, 409}:
                    raise ValueError("authorization/owner refused")
                if args.command != "wait":
                    raise ValueError("administration refused")
            except (OSError, http.client.HTTPException):
                if args.command != "wait":
                    raise
            finally:
                conn.close()
            if time.monotonic() >= deadline:
                raise TimeoutError("readiness deadline")
            time.sleep(min(.1, max(0, deadline - time.monotonic())))
    except Exception:
        print("adapter administration failed (configuration, authorization, readiness or request)", file=sys.stderr)
        raise SystemExit(1)


if __name__ == "__main__":
    main()
