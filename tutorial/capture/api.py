"""Minimal dev-server API client for capture setup (the bot side of a draft)."""
import json
import urllib.error
import urllib.request
import uuid

API = "http://127.0.0.1:8080"


def call(method, path, body=None, token=None):
    req = urllib.request.Request(API + path, method=method, data=None if body is None else json.dumps(body).encode(),
        headers={"Content-Type": "application/json; charset=UTF-8", **({"Authorization": "Bearer " + token} if token else {})})
    try:
        with urllib.request.urlopen(req) as r:
            return r.status, json.loads(r.read() or b"null")
    except urllib.error.HTTPError as e:
        raw = e.read()
        try:
            return e.code, json.loads(raw or b"null")
        except ValueError:
            return e.code, raw


def login(email, password="Tutorial123!"):
    s, b = call("POST", "/sessions", {"email": email, "password": password})
    if s not in (200, 201):
        raise SystemExit(f"login failed {s} {b}")
    return b["token"]


def request_id():
    return str(uuid.uuid4())
