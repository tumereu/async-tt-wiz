#!/usr/bin/env python3
"""Generate original test data in ordinary MTGJSON v5/Scryfall response shapes.

No network, external images/fonts, real card records or application imports.
Fixture roles/expected outcomes live OUTSIDE the provider responses.
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
DESTINATION = ROOT / "tests/fixtures/catalogs"
ORIGIN = "http://synthetic-source:8080"
NAMESPACE = uuid.UUID("6b6c439a-42ac-54b8-b142-5daab7bb9213")
DATASETS = {
    "prism": {"codes": ["ZPA", "ZPB"], "names": [
        "Coppercloud Surveyor", "Velvet Prism Engine", "Amberglass Chorus",
        "Quiet Lattice Bolt", "Folded Lantern Road", "Mauve Window Expanse",
        "Threadglass Envoy", "Prismgate Bulwark", "Feltwing Atlas",
        "Coppercloud Echo", "Coppercloud Echo!", "Prismseed Mote", "Prismseed Visitor",
        "Dayglass Loom", "Nightglass Weave"]},
    "quilt": {"codes": ["ZQA", "ZQB"], "names": [
        "Silverquilt Wayfinder", "Indigo Spindle Array", "Soft Horizon Weft",
        "Hushed Bobbin Spark", "Unfurled Ribbon Path", "Woven Pebble Reach",
        "Patchwork Emissary", "Thimblecloud Rampart", "Buttonstar Map",
        "Silverquilt Ripple", "Silverquilt Ripple!", "Quiltgrain Wisp", "Quiltgrain Arrival",
        "Dawnstitch Frame", "Duskstitch Cloth"]},
}


def identifier(dataset, role):
    return str(uuid.uuid5(NAMESPACE, dataset + "/" + role))


def json_bytes(value):
    return (json.dumps(value, sort_keys=True, indent=2, ensure_ascii=False) + "\n").encode()


def sha(value):
    return hashlib.sha256(value).hexdigest()


def png(dataset, key):
    """Original abstract tile mosaic, not a card frame/layout or borrowed artwork.

    A SHA256-derived palette and diagonal/checker cells make distinct images
    recognizable; fixed integer RGB pixels + PNG/zlib give reproducible bytes.
    """
    palette = hashlib.sha256(("original-mosaic-v1/" + dataset + "/" + key).encode()).digest()
    width, height = 96, 128
    rows = bytearray()
    for y in range(height):
        rows.append(0)
        for x in range(width):
            color = ((x // 12) + (y // 16) * 3 + (x + y) // 24) % 8
            rows.extend(48 + palette[(color * 3 + c) % 32] * 3 // 4 for c in range(3))

    def chunk(kind, payload):
        return struct.pack(">I", len(payload)) + kind + payload + struct.pack(">I", zlib.crc32(kind + payload))

    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(bytes(rows), 9)) + chunk(b"IEND", b""))


def generate(origin=ORIGIN):
    parsed = urlsplit(origin)
    if parsed.scheme not in {"http", "https"} or not parsed.hostname or parsed.username or parsed.password or parsed.path or parsed.query or parsed.fragment:
        raise ValueError("Use an explicit synthetic HTTP(S) origin without credentials/path/query/fragment")
    files = {}
    inventory = []
    for dataset, recipe in DATASETS.items():
        names = recipe["names"]
        for version in [1, 2]:
            base = f"{dataset}/v{version}"
            date = f"2026-10-{10 + version:02}"
            meta = {"date": date, "version": "5.2.2"}
            roles = {}
            cards = []
            tokens = []
            scryfall = {}
            art_cases = {}

            def record(role, name, types, number, fields=None, art="ok", token=False, set_index=0):
                mtg_id = identifier(dataset, "mtgjson/" + role)
                scry_id = identifier(dataset, "scryfall/" + role)
                code = recipe["codes"][set_index]
                value = {"uuid": mtg_id, "name": name, "number": str(number), "setCode": code,
                         "language": "English", "layout": "token" if token else "normal",
                         "type": " ".join(types), "types": types, "subtypes": [], "supertypes": [],
                         "text": "Original synthetic descriptive text. No executable behavior.",
                         "identifiers": {"scryfallId": scry_id,
                                         "scryfallOracleId": identifier(dataset, "oracle/" + ("creature" if role == "printing" else role))},
                         "availability": ["paper"], "colors": [], "colorIdentity": [], "manaValue": 0.0,
                         "convertedManaCost": 0.0, "finishes": ["nonfoil"], "frameVersion": "2015",
                         "borderColor": "black", "hasFoil": False, "hasNonFoil": True, "isOnlineOnly": False,
                         "rarity": "common", "artist": "Original synthetic mosaic generator",
                         "printings": [code], "rulings": [], "legalities": {}, "purchaseUrls": {}}
                value.update(fields or {})
                (tokens if token else cards).append(value)
                roles[role] = mtg_id
                image_path = f"{base}/art/{scry_id}.png"
                image_uri = origin + "/" + image_path
                if art == "ok":
                    # Only the changed ordinary definition gets new version art.
                    key = role + (f"/v{version}" if role == "creature" else "")
                    files[image_path] = png(dataset, key)
                elif art == "broken":
                    files[image_path] = b"Deliberately invalid image bytes for text-fallback tests.\n"
                elif art != "missing":
                    raise ValueError(art)
                obj = {"object": "card", "id": scry_id, "oracle_id": value["identifiers"]["scryfallOracleId"],
                       "name": name, "lang": "en", "layout": value["layout"],
                       "set": code.lower(), "collector_number": str(number), "type_line": value["type"],
                       "oracle_text": value["text"], "image_status": "highres_scan", "image_uris": {
                           "small": image_uri, "normal": image_uri, "large": image_uri, "png": image_uri}}
                scryfall[scry_id] = obj
                art_cases[role] = {"state": art, "path": image_path}
                return value

            record("creature", names[0], ["Creature"], 1,
                   {"power": "2" if version == 1 else "5", "toughness": "3",
                    "text": f"Original synthetic survey text, revision {version}."})
            record("printing", names[0], ["Creature"], 101, {"power": "2", "toughness": "3"}, set_index=1)
            record("artifact", names[1], ["Artifact"], 2)
            record("enchantment", names[2], ["Enchantment"], 3)
            record("instant", names[3], ["Instant"], 4)
            record("sorcery", names[4], ["Sorcery"], 5)
            record("land", names[5], ["Land"], 6)
            record("loyalty", names[6], ["Planeswalker"], 7, {"loyalty": "4"})
            record("defense", names[7], ["Battle"], 8, {"defense": "6"})
            record("absent-default", names[8], [], 9, art="missing")
            record("near-name", names[9], ["Artifact", "Creature"], 10, {"power": "*", "toughness": "*"})
            record("punctuated-name", names[10], ["Creature"], 11, {"power": "2", "toughness": "*"}, art="broken")
            # A deliberately non-authentic catalog can contain two distinct
            # definitions with identical names. Explicit printing disambiguates;
            # name-only resolution must not choose a different definition silently.
            record("ambiguous-name", names[9], ["Creature"], 13, {"power": "7", "toughness": "2"})
            record("token", names[11], ["Creature"], "T1", {"power": "1", "toughness": "1"}, token=True)
            record("blank", "Blank", [], "T2", token=True, art="missing")
            # Two face records are related by UUID, not by matching names.
            combined = names[13] + " // " + names[14]
            front = record("face-a", combined, ["Creature"], 12,
                           {"faceName": names[13], "side": "a", "layout": "transform", "power": "1", "toughness": "4"})
            back = record("face-b", combined, ["Creature"], 12,
                          {"faceName": names[14], "side": "b", "layout": "transform", "power": "4", "toughness": "1"})
            front["otherFaceIds"], back["otherFaceIds"] = [back["uuid"]], [front["uuid"]]
            # Both faces belong to the same Scryfall printing. Keep each face's
            # distinct art URL; no guessed first-face substitution is necessary.
            front_sid, back_sid = front["identifiers"]["scryfallId"], back["identifiers"]["scryfallId"]
            face_objects = []
            for value, sid in [(front, front_sid), (back, back_sid)]:
                obj = scryfall[sid]
                face_objects.append({"object": "card_face", "name": value["faceName"], "type_line": value["type"],
                                     "oracle_text": value["text"], "power": value["power"], "toughness": value["toughness"],
                                     "image_uris": obj["image_uris"]})
            scryfall[front_sid].pop("image_uris")
            scryfall[front_sid]["card_faces"] = face_objects
            back["identifiers"]["scryfallId"] = front_sid
            back["identifiers"]["scryfallOracleId"] = front["identifiers"]["scryfallOracleId"]
            del scryfall[back_sid]
            if version == 2:
                record("new-token", names[12], ["Creature"], "T3", {"power": "3", "toughness": "2"}, token=True)
            sets = {}
            for index, code in enumerate(recipe["codes"]):
                sets[code] = {"code": code, "name": f"Original {dataset} synthetic edition {index + 1}",
                              "releaseDate": "2026-10-01", "type": "expansion",
                              "baseSetSize": len({c['number'] for c in cards if c['setCode'] == code}),
                              "totalSetSize": len({c['number'] for c in cards if c['setCode'] == code}), "isOnlineOnly": False,
                              "cards": [c for c in cards if c["setCode"] == code],
                              "tokens": [t for t in tokens if t["setCode"] == code]}
            plain = json_bytes({"meta": meta, "data": sets})
            compressed = gzip.compress(plain, compresslevel=9, mtime=0)
            files[f"{base}/AllPrintings.json"] = plain
            files[f"{base}/AllPrintings.json.gz"] = compressed
            files[f"{base}/AllPrintings.json.gz.sha256"] = (sha(compressed) + "\n").encode()
            files[f"{base}/Meta.json"] = json_bytes({"meta": meta, "data": meta})
            for sid, obj in scryfall.items():
                files[f"{base}/scryfall/cards/{sid}.json"] = json_bytes(obj)

            main_name = f"{names[0]} ({recipe['codes'][0]}) 1"
            side_name = f"{names[1]} ({recipe['codes'][0]}) 2"
            cases = [
                {"id": "plain", "text": f"4 {main_name}\n2 {names[3]}\n", "expected": "resolved", "mainQuantity": 6, "sideQuantity": 0},
                {"id": "sections", "text": f"Deck\n4 {main_name}\n\nSideboard\n2 {side_name}\n", "expected": "resolved", "mainQuantity": 4, "sideQuantity": 2},
                {"id": "sb-prefix", "text": f"4 {main_name}\nSB: 2 {side_name}\n", "expected": "resolved", "mainQuantity": 4, "sideQuantity": 2},
                {"id": "blank-separator", "text": f"4 {main_name}\n\n2 {side_name}\n", "expected": "resolved", "mainQuantity": 4, "sideQuantity": 2},
                {"id": "explicit-printing", "text": f"1 {names[0]} ({recipe['codes'][1]}) 101\n", "expected": "resolved", "printing": roles["printing"]},
                {"id": "unknown", "text": f"1 Uncatalogued {dataset} Object\n", "expected": "unknown"},
                {"id": "ambiguous-name", "text": f"1 {names[9]}\n", "expected": "ambiguous", "candidates": [roles["near-name"], roles["ambiguous-name"]]},
                {"id": "missing-printing", "text": f"1 {names[0]} ({recipe['codes'][0]}) 999\n", "expected": "unresolved-printing"},
            ]
            files[f"{base}/import-cases.json"] = json_bytes(cases)
            inventory.append({"dataset": dataset, "version": version, "base": base, "roles": roles, "artCases": art_cases,
                              "catalogSha256": sha(plain), "gzipSha256": sha(compressed),
                              "defaultExpectations": {"creature": "2/3" if version == 1 else "5/3", "loyalty": "4", "defense": "6",
                                                      "absent-default": "", "artifact": "", "near-name": "*/*", "punctuated-name": "2/*",
                                                      "face-a": "1/4", "face-b": "4/1", "blank": ""}})
    files["manifest.json"] = json_bytes({"formatVersion": 1, "fixtureVersion": "1", "origin": origin,
                                        "generator": "scripts/synthetic-catalogs.py", "artAlgorithm": "original-mosaic-v1",
                                        "provenance": "All names, descriptive text and abstract RGB tile artwork were created for this repository; no copied card layout, logo or provider content.",
                                        "license": "CC0-1.0", "datasets": inventory,
                                        "files": {path: sha(content) for path, content in sorted(files.items())}})
    return files


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--write", action="store_true")
    parser.add_argument("--output", type=Path, default=DESTINATION)
    parser.add_argument("--origin", default=ORIGIN)
    args = parser.parse_args()
    files = generate(args.origin)
    if args.write:
        for relative, content in files.items():
            path = args.output / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(content)
    for relative, content in files.items():
        if (args.output / relative).read_bytes() != content:
            raise ValueError(f"Fixture is not deterministic/current: {relative}")
    # Do not remove unfamiliar files. Unexpected provider assets must be investigated.
    actual = {p.relative_to(args.output).as_posix() for p in args.output.rglob("*") if p.is_file() and p.name != "README.md"}
    if actual != set(files):
        raise ValueError(f"Unexpected/missing fixture paths: {actual ^ set(files)}")
    print(f"PASS deterministic synthetic fixture bytes/hashes: {len(files)} files, {len(DATASETS)} identity-distinct datasets x 2 versions")
    print("PASS generation is stdlib-only, offline and independent of app/backend builds; no product/import/image acceptance claimed")


if __name__ == "__main__":
    main()
