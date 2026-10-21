"""I01b regression checks; no app features or provider/network access required."""
import contextlib
import copy
import importlib.util
import io
import json
from pathlib import Path
import shutil
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


def load(name, path):
    spec = importlib.util.spec_from_file_location(name, ROOT / path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


ledger = load("coverage_ledger", "scripts/coverage-ledger.py")
fixtures = load("synthetic_catalogs", "scripts/synthetic-catalogs.py")
catalog_check = load("check_synthetic_catalogs", "scripts/check-synthetic-catalogs.py")


class CoverageLedgerTest(unittest.TestCase):
    def setUp(self):
        self.data = json.loads((ROOT / ledger.LEDGER).read_text())

    def rejects(self, mutation):
        mutation(self.data)
        with self.assertRaises(ValueError):
            ledger.validate(self.data)

    def test_actual_sources_and_all_875_ids(self):
        self.assertEqual(ledger.validate(self.data), {"P": 72, "T": 715, "F": 88})

    def test_missing_id(self):
        self.rejects(lambda d: d["items"].pop(77))

    def test_duplicate_id(self):
        self.rejects(lambda d: d["items"].append(copy.deepcopy(d["items"][0])))

    def test_unknown_id(self):
        self.rejects(lambda d: d["items"][0].update(id="P-999"))

    def test_unexplained_disposition(self):
        self.rejects(lambda d: d["items"][0].update(disposition="excluded", rationale=""))

    def test_retired_scenario_cannot_be_revived(self):
        self.rejects(lambda d: next(i for i in d["items"] if i["id"] == "T-296").update(disposition="current"))

    def test_conditional_t610_and_six_global_supersessions(self):
        item = next(i for i in self.data["items"] if i["id"] == "T-610")
        self.assertEqual(item["disposition"], "conditional")
        self.assertEqual({p["decision"] for p in self.data["policies"]}, {"D-278", "D-285", "D-286", "D-287", "D-288", "D-289"})
        self.assertTrue(all(p["references"] and p["rule"] for p in self.data["policies"]))

    def test_unknown_task_and_missing_check_dependency(self):
        self.rejects(lambda d: d["items"][0].update(implementationDependencies=["I99"]))
        self.setUp()
        self.rejects(lambda d: d["items"][0]["assertions"].clear())

    def test_assigned_is_not_executed(self):
        self.rejects(lambda d: d["items"][0].update(completion="passed"))

    def test_partial_pass_is_not_full_scenario(self):
        cross_task = next(i for i in self.data["items"] if i["id"] == "T-662")
        self.assertGreater(len(cross_task["assertions"]), 1)
        self.rejects(lambda d: next(i for i in d["items"] if i["id"] == "T-662")["assertions"][0].update(status="passed"))

    def test_false_execution_claim_is_rejected(self):
        self.rejects(lambda d: d["executions"].append({"status": "passed", "command": "not executed", "evidence": "missing.txt"}))

    def test_supersession_provenance_is_not_dropped(self):
        self.rejects(lambda d: next(i for i in d["items"] if i["id"] == "F68")["partialSupersededBy"].clear())

    def test_actual_source_missing_duplicate_unknown_and_changed_row(self):
        with tempfile.TemporaryDirectory(dir="/tmp/opencode", prefix="coverage-test-") as temp:
            root = Path(temp)
            for path, _ in ledger.SOURCES.values():
                target = root / path
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(ROOT / path, target)
            plan = root / "docs/agent/implementation-plan.md"
            plan.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(ROOT / "docs/agent/implementation-plan.md", plan)
            path = root / ledger.SOURCES["P"][0]
            original = path.read_text()
            row = next(line for line in original.splitlines() if line.startswith("| P-001 |"))
            for replacement in [original.replace(row + "\n", ""), original + row + "\n", original + row.replace("P-001", "P-999") + "\n"]:
                path.write_text(replacement)
                with self.assertRaises(ValueError):
                    ledger.definitions(root)
            path.write_text(original.replace(row, row.replace("native Android", "different platform")))
            self.assertNotEqual(ledger.definitions(root)["P-001"]["sha256"], self.data["items"][0]["source"]["sha256"])
            with self.assertRaises(ValueError):
                ledger.validate(self.data, root)

    def test_plan_tasks_are_pinned_and_missing_or_duplicate_rejected(self):
        with tempfile.TemporaryDirectory(dir="/tmp/opencode", prefix="coverage-plan-") as temp:
            root = Path(temp)
            path = root / "docs/agent/implementation-plan.md"
            path.parent.mkdir(parents=True)
            original = (ROOT / "docs/agent/implementation-plan.md").read_text()
            row = next(line for line in original.splitlines() if line.startswith("| I05 |"))
            path.write_text(original)
            self.assertEqual(len(ledger.task_sources(root)), 36)
            for replacement in [original.replace(row + "\n", ""), original + row + "\n"]:
                path.write_text(replacement)
                with self.assertRaises(ValueError):
                    ledger.task_sources(root)


class SyntheticCatalogTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.generated = fixtures.generate()

    def test_repeated_generation_and_checked_in_bytes(self):
        self.assertEqual(self.generated, fixtures.generate())
        for path, data in self.generated.items():
            self.assertEqual((fixtures.DESTINATION / path).read_bytes(), data, path)

    def test_independent_fixture_validation(self):
        with contextlib.redirect_stdout(io.StringIO()):
            catalog_check.validate(fixtures.DESTINATION)

    def write_copy(self, directory, values):
        for path, data in values.items():
            target = directory / path
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)

    def test_tampered_asset_and_catalog_fail_hash_validation(self):
        with tempfile.TemporaryDirectory(dir="/tmp/opencode", prefix="catalog-test-") as temp:
            directory = Path(temp)
            self.write_copy(directory, self.generated)
            for path in ["prism/v1/AllPrintings.json", next(p for p in self.generated if p.endswith(".png"))]:
                target = directory / path
                original = target.read_bytes()
                target.write_bytes(original + b"corrupt")
                with self.assertRaisesRegex(ValueError, "hash mismatch"):
                    catalog_check.validate(directory)
                target.write_bytes(original)

    def test_png_crc_and_corrupt_bytes_are_rejected(self):
        data = next(data for path, data in self.generated.items() if path.endswith(".png") and data.startswith(b"\x89PNG"))
        self.assertTrue(catalog_check.decode_png(data))
        altered = bytearray(data)
        altered[45] ^= 1
        with self.assertRaises(ValueError):
            catalog_check.decode_png(bytes(altered))
        with self.assertRaises(ValueError):
            catalog_check.decode_png(b"not an image")

    def test_arbitrary_configured_origin_needs_no_app_rebuild_or_id_changes(self):
        values = fixtures.generate("http://127.0.0.1:18081")
        old_manifest = json.loads(self.generated["manifest.json"])
        new_manifest = json.loads(values["manifest.json"])
        self.assertEqual([e["roles"] for e in old_manifest["datasets"]], [e["roles"] for e in new_manifest["datasets"]])
        self.assertEqual(values["prism/v1/AllPrintings.json"], self.generated["prism/v1/AllPrintings.json"])
        self.assertNotEqual(values["manifest.json"], self.generated["manifest.json"])
        with tempfile.TemporaryDirectory(dir="/tmp/opencode", prefix="catalog-origin-") as temp:
            directory = Path(temp)
            self.write_copy(directory, values)
            with contextlib.redirect_stdout(io.StringIO()):
                catalog_check.validate(directory)

    def test_invalid_origin(self):
        for origin in ["file:///etc/passwd", "https://user:password@host.invalid", "https://host.invalid/path", "https://host.invalid?token=secret"]:
            with self.assertRaises(ValueError):
                fixtures.generate(origin)

    def test_relationship_corruption_not_just_content_hash(self):
        values = dict(self.generated)
        path = "prism/v1/AllPrintings.json"
        catalog = json.loads(values[path])
        front = next(card for edition in catalog["data"].values() for card in edition["cards"] if card.get("side") == "a")
        front["otherFaceIds"] = [front["uuid"]]
        values[path] = fixtures.json_bytes(catalog)
        # Rehash all bytes, so a mere manifest-integrity check would pass.
        import gzip
        values["prism/v1/AllPrintings.json.gz"] = gzip.compress(values[path], compresslevel=9, mtime=0)
        packed_hash = fixtures.sha(values["prism/v1/AllPrintings.json.gz"])
        values["prism/v1/AllPrintings.json.gz.sha256"] = (packed_hash + "\n").encode()
        manifest = json.loads(values["manifest.json"])
        entry = next(e for e in manifest["datasets"] if e["base"] == "prism/v1")
        entry.update(catalogSha256=fixtures.sha(values[path]), gzipSha256=packed_hash)
        manifest["files"] = {p: fixtures.sha(data) for p, data in values.items() if p != "manifest.json"}
        values["manifest.json"] = fixtures.json_bytes(manifest)
        with tempfile.TemporaryDirectory(dir="/tmp/opencode", prefix="catalog-face-") as temp:
            directory = Path(temp)
            self.write_copy(directory, values)
            with self.assertRaisesRegex(ValueError, "reciprocal face"):
                catalog_check.validate(directory)


if __name__ == "__main__":
    unittest.main(verbosity=2)
