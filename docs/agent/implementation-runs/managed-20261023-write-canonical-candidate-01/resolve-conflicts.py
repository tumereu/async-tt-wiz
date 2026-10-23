"""Resolve only inspected import conflicts to the exact pinned runtime bytes."""
import hashlib
import json
from pathlib import Path
import re
import subprocess

root = Path(__file__).parent
pin = "0caedcbea775d9dca5128ddac1e677d9f4de6fd4"
expected = [
    "androidApp/src/main/kotlin/org/asyncttwiz/android/GameSession.kt",
    "androidApp/src/main/kotlin/org/asyncttwiz/android/LobbyController.kt",
    "protocol/src/commonMain/kotlin/org/asyncttwiz/protocol/GameWire.kt",
    "server/src/main/kotlin/org/asyncttwiz/server/game/GameHub.kt",
    "server/src/main/kotlin/org/asyncttwiz/server/game/GameService.kt",
    "server/src/test/kotlin/org/asyncttwiz/server/game/GameSocketTest.kt",
]
actual = subprocess.check_output(["git", "diff", "--name-only", "--diff-filter=U"], text=True).splitlines()
assert actual == expected
records = []
for name in actual:
    original = Path(name).read_text()
    pattern = r"^<<<<<<< ours\n(.*?)^=======\n(.*?)^>>>>>>> theirs\n"
    conflicts = [{"canonical": m[1], "pinnedRuntime": m[2]} for m in re.finditer(pattern, original, re.M | re.S)]
    resolved = re.sub(pattern, lambda m: m[2], original, flags=re.M | re.S).encode()
    assert conflicts and resolved == subprocess.check_output(["git", "show", pin + ":" + name])
    Path(name).write_bytes(resolved)
    records.append({"path": name, "sha256": hashlib.sha256(resolved).hexdigest(), "conflicts": conflicts})
subprocess.run(["git", "add", "--", *actual], check=True)
(root / "conflicts.json").write_text(json.dumps(records, indent=2) + "\n")
print(f"Resolved {len(records)} inspected files exactly to {pin}; no novel source changes.")
