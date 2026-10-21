#!/usr/bin/env python3
"""Independently validate fixture structure/relationships/defaults/art and updates.

This is fixture qualification only. I05/I06/I01e still own the real downloader,
import resolver, HTTP adapters and Android decoder paths; none are executed here.
"""
import argparse
import gzip
import hashlib
import json
from pathlib import Path
import struct
from urllib.parse import urlsplit
import uuid
import zlib

ROOT = Path(__file__).resolve().parent.parent


def require(condition, message):
    if not condition:
        raise ValueError(message)


def decode_png(data):
    require(data[:8] == b"\x89PNG\r\n\x1a\n", "invalid PNG signature")
    position, compressed, header, ended = 8, bytearray(), None, False
    while position < len(data):
        require(position + 12 <= len(data), "truncated PNG chunk")
        length = struct.unpack(">I", data[position:position + 4])[0]
        kind = data[position + 4:position + 8]
        payload = data[position + 8:position + 8 + length]
        crc = data[position + 8 + length:position + 12 + length]
        require(len(crc) == 4 and struct.unpack(">I", crc)[0] == zlib.crc32(kind + payload), "PNG CRC mismatch")
        if kind == b"IHDR":
            header = struct.unpack(">IIBBBBB", payload)
        elif kind == b"IDAT":
            compressed.extend(payload)
        elif kind == b"IEND":
            ended = True
            require(position + 12 + length == len(data), "PNG trailing data")
        position += 12 + length
    require(ended and header is not None, "incomplete PNG")
    width, height, depth, color, compression, filtering, interlace = header
    require((width, height, depth, color, compression, filtering, interlace) == (96, 128, 8, 2, 0, 0, 0), "unexpected original-art format")
    pixels = zlib.decompress(compressed)
    require(len(pixels) == height * (width * 3 + 1), "PNG pixel length mismatch")
    require(all(pixels[y * (width * 3 + 1)] == 0 for y in range(height)), "unexpected PNG filter")
    require(len(set(pixels)) > 8, "original mosaic palette missing")
    return hashlib.sha256(pixels).hexdigest()


def defaults(record):
    if "power" in record and "toughness" in record:
        return record["power"] + "/" + record["toughness"]
    return record.get("loyalty", record.get("defense", ""))


def validate(directory):
    manifest = json.loads((directory / "manifest.json").read_text())
    require(manifest["formatVersion"] == 1 and manifest["fixtureVersion"] == "1", "unsupported fixture version")
    require(manifest["license"] == "CC0-1.0" and "no copied" in manifest["provenance"], "original-art provenance absent")
    require(manifest["artAlgorithm"] == "original-mosaic-v1", "unknown art recipe")
    files = manifest["files"]
    actual = {p.relative_to(directory).as_posix() for p in directory.rglob("*") if p.is_file() and p.name not in {"README.md", "manifest.json"}}
    require(actual == set(files), "unexpected/missing fixture files")
    for path, digest in files.items():
        require(not Path(path).is_absolute() and ".." not in Path(path).parts, "unsafe manifest path")
        require(hashlib.sha256((directory / path).read_bytes()).hexdigest() == digest, f"content hash mismatch: {path}")
    inventories = manifest["datasets"]
    require(len(inventories) == 4, "need two datasets x two versions")
    records_by_version = {}
    image_pixels = {}
    for entry in inventories:
        dataset, version, base = entry["dataset"], entry["version"], entry["base"]
        require(base == f"{dataset}/v{version}", "version/base mismatch")
        require((dataset, version) not in records_by_version, "duplicate dataset version")
        raw = (directory / base / "AllPrintings.json").read_bytes()
        packed = (directory / base / "AllPrintings.json.gz").read_bytes()
        require(gzip.decompress(packed) == raw, "gzip/plain mismatch")
        require(hashlib.sha256(raw).hexdigest() == entry["catalogSha256"], "catalog digest mismatch")
        require(hashlib.sha256(packed).hexdigest() == entry["gzipSha256"], "gzip digest mismatch")
        require((directory / base / "AllPrintings.json.gz.sha256").read_text() == entry["gzipSha256"] + "\n", "published checksum mismatch")
        catalog = json.loads(raw)
        metadata = json.loads((directory / base / "Meta.json").read_text())
        require(metadata["data"] == metadata["meta"] == catalog["meta"], "startup metadata mismatch")
        require(catalog["meta"]["version"].startswith("5."), "not MTGJSON v5 metadata")
        records, tokens = {}, set()
        for code, edition in catalog["data"].items():
            require(edition["code"] == code and edition["cards"] and "tokens" in edition, "invalid MTGJSON set shape")
            for kind in ["cards", "tokens"]:
                for record in edition[kind]:
                    rid = record["uuid"]
                    require(str(uuid.UUID(rid)) == rid and rid not in records, "duplicate/noncanonical record UUID")
                    require(record["setCode"] == code and record["name"] and record["number"], "record/set/name/number mismatch")
                    require(record["language"] == "English" and isinstance(record["types"], list), "invalid ordinary provider fields")
                    require(not {"fixtureRole", "expected", "defaultCorner", "testId"} & record.keys(), "test-only provider field")
                    sid = record["identifiers"]["scryfallId"]
                    require(str(uuid.UUID(sid)) == sid, "invalid Scryfall printing UUID")
                    obj = json.loads((directory / base / "scryfall/cards" / f"{sid}.json").read_text())
                    require(obj["object"] == "card" and obj["id"] == sid and obj["name"] == record["name"], "Scryfall object identity mismatch")
                    if record.get("side"):
                        require(record["layout"] == "transform" and len(obj["card_faces"]) == 2, "multiface shape mismatch")
                        face = next(f for f in obj["card_faces"] if f["name"] == record["faceName"])
                    else:
                        face = obj
                    image = face["image_uris"]["normal"]
                    require(image.startswith(manifest["origin"] + "/" + base + "/art/"), "external/cross-version image origin")
                    require(not urlsplit(image).username, "credential-bearing image URL")
                    records[rid] = record
                    if kind == "tokens":
                        tokens.add(rid)
        roles = entry["roles"]
        require(set(roles.values()) == set(records) and len(set(roles.values())) == len(roles), "roles do not partition provider records")
        require({"creature", "printing", "artifact", "enchantment", "instant", "sorcery", "land", "loyalty", "defense",
                 "absent-default", "near-name", "punctuated-name", "ambiguous-name", "token", "blank", "face-a", "face-b"} <= roles.keys(), "missing coverage role")
        require(set(entry["artCases"]) == set(roles), "missing art cases")
        for role, expectation in entry["defaultExpectations"].items():
            require(defaults(records[roles[role]]) == expectation, f"wrong retained default: {role}")
        front, back = [records[roles[role]] for role in ["face-a", "face-b"]]
        require(front["otherFaceIds"] == [back["uuid"]] and back["otherFaceIds"] == [front["uuid"]], "broken reciprocal face IDs")
        require(front["identifiers"] == back["identifiers"] and front["faceName"] != back["faceName"], "face printing/identity mismatch")
        require(roles["blank"] in tokens and records[roles["blank"]]["name"] == "Blank", "Blank must be a normal token record")
        ordinary, printing = [records[roles[r]] for r in ["creature", "printing"]]
        require(ordinary["name"] == printing["name"] and ordinary["identifiers"]["scryfallOracleId"] == printing["identifiers"]["scryfallOracleId"] and ordinary["setCode"] != printing["setCode"], "missing alternate printing")
        near, ambiguous = [records[roles[r]] for r in ["near-name", "ambiguous-name"]]
        require(near["name"] == ambiguous["name"] and near["identifiers"]["scryfallOracleId"] != ambiguous["identifiers"]["scryfallOracleId"], "no genuinely ambiguous definition names")
        require(records[roles["punctuated-name"]]["name"] == near["name"] + "!", "missing similar-name distinction")
        for role, art in entry["artCases"].items():
            path = directory / art["path"]
            if art["state"] == "ok":
                pixels = decode_png(path.read_bytes())
                image_pixels[(dataset, version, role)] = pixels
            elif art["state"] == "missing":
                require(not path.exists(), "missing-art URL unexpectedly exists")
            elif art["state"] == "broken":
                require(path.exists() and not path.read_bytes().startswith(b"\x89PNG"), "broken-art case is not broken")
            else:
                raise ValueError("unknown art disposition")
        require({v["state"] for v in entry["artCases"].values()} == {"ok", "missing", "broken"}, "missing image-failure cases")
        face_paths = [entry["artCases"][r]["path"] for r in ["face-a", "face-b"]]
        require(face_paths[0] != face_paths[1], "face art substituted")
        cases = json.loads((directory / base / "import-cases.json").read_text())
        require({case["id"] for case in cases} == {"plain", "sections", "sb-prefix", "blank-separator", "explicit-printing", "unknown", "ambiguous-name", "missing-printing"}, "missing import cases")
        require(next(c for c in cases if c["id"] == "ambiguous-name")["candidates"] == [roles["near-name"], roles["ambiguous-name"]], "ambiguity candidates mismatch")
        records_by_version[(dataset, version)] = records
        print(f"PASS {base}: {len(records)} records, {len(tokens)} tokens, reciprocal multiface/printing/defaults/import/art roles; sha256={entry['catalogSha256']}")
    datasets = sorted({entry["dataset"] for entry in inventories})
    require(len(datasets) == 2, "second dataset absent")
    first_ids, second_ids = [{rid for (d, _), records in records_by_version.items() if d == dataset for rid in records} for dataset in datasets]
    require(not first_ids & second_ids, "datasets share record IDs")
    names = [{record["name"] for (d, _), records in records_by_version.items() if d == dataset for record in records.values()} - {"Blank"} for dataset in datasets]
    require(not names[0] & names[1], "datasets share non-Blank names")
    artwork = [{pixels for (d, _, _), pixels in image_pixels.items() if d == dataset} for dataset in datasets]
    require(not artwork[0] & artwork[1], "datasets share original artwork")
    for dataset in datasets:
        old, new = [records_by_version[(dataset, v)] for v in [1, 2]]
        require(set(old) < set(new) and len(new) == len(old) + 1, "version update must preserve identities and add token")
        changed = [rid for rid in old if old[rid] != new[rid]]
        require(len(changed) == 1 and defaults(old[changed[0]]) == "2/3" and defaults(new[changed[0]]) == "5/3", "changed-definition fixture mismatch")
        require(image_pixels[(dataset, 1, "creature")] != image_pixels[(dataset, 2, "creature")], "changed-art fixture mismatch")
        require(image_pixels[(dataset, 1, "artifact")] == image_pixels[(dataset, 2, "artifact")], "unchanged cache-reuse fixture mismatch")
    print("PASS identity-distinct names/UUIDs/art, stable version identities, changed defaults/art/new token and unchanged art reuse")
    print("PASS original PNG CRC/pixel decode and checksums; this is NOT normal-app downloader/import/decoder or E2E acceptance")
    return manifest


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--directory", type=Path, default=ROOT / "tests/fixtures/catalogs")
    validate(parser.parse_args().directory)
