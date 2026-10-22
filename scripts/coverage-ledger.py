#!/usr/bin/env python3
"""Build/check source-pinned R1 traceability, not a product-test pass generator.

All IDs come from primary Markdown table definitions, never reference matches.
Ownership is explicitly enumerated below; regeneration cannot infer product scope.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent.parent
LEDGER = "docs/agent/coverage/ledger.json"
SOURCES = {
    "P": ("docs/design/03-contracts/product.md", 72),
    "T": ("docs/design/05-verification/acceptance.md", 715),
    "F": ("docs/design/02-experience/ui/feedback.md", 88),
}
# Each scope is a future assertion/check obligation in the approved parent task.
# Exact commands/source symbols are bound when that feature exists, not invented now.
SCOPES = {
    1: ("foundation", "Reproducible modules/builds, storage/migrations, service health, synthetic data, reset/isolation and one-device normal-APK adapter; D-307 defers broad repeated qualification to I34/I01f3", ["build", "integration", "device"]),
    2: ("credentials", "Normal registration/login, protected persistent revocable token, exact passwords, privacy and authorization failures", ["unit", "integration", "normal-app-ui"]),
    3: ("shell", "Menu/Games/navigation, native Back/rotation/restart, local Logout cleanup and old-account callback fencing", ["component", "normal-app-ui"]),
    4: ("reset", "Email/deep-link reset success/failure, session revocation for new requests and ordinary same-account recovery", ["integration", "normal-app-ui"]),
    5: ("catalog", "Normal staged MTGJSON downloads/startup checks, safe lazy printing/face images, source failure and retained definitions", ["unit", "integration", "normal-app-ui"]),
    6: ("import", "Common quantity/name/printing/section parsing, ambiguity/unknown errors, preserved input and atomic named save", ["unit", "normal-app-ui"]),
    7: ("collection", "Collection/details/rename/in-deck cover/delete/art and synchronized account-owned decks with selected-copy isolation", ["integration", "normal-app-ui"]),
    8: ("definitions", "Overwrite/additive Merge per section, original HTTP outcomes, complete private definition POST and immutable content identity", ["unit", "integration", "normal-app-ui"]),
    9: ("invitations", "Contact search/add, atomic named create+invite, Accept/Decline/Reinvite to exact game with durable original outcomes", ["integration", "normal-app-ui"]),
    10: ("preparation", "Draft picker/one confirmation, immutable prepared copy and captured one/all tap/drag transfers with Ready clearing/feedback", ["unit", "component", "normal-app-ui"]),
    11: ("start", "Shared initial definitions/containers, starter/Ready and incarnation boundaries, independent recorded start shuffles and random golden vectors", ["golden", "integration", "normal-app-ui"]),
    12: ("lobby", "Two-player lobby synchronization/start/cancel ordering, preparation-round fences, retained outcomes and source-copy isolation", ["integration", "normal-app-ui"]),
    13: ("processor", "One shared deterministic mutation processor, relevant transitions/inverses, canonical bytes, durable FIFO inbox/dedup/transactions/outbox and limited guards", ["unit", "property", "golden", "integration"]),
    14: ("prediction", "Accepted replica/volatile predictions/UI separation, ordered in-flight recovery, exact identities, crash loss and difference-only reconciliation", ["unit", "integration", "normal-app-ui"]),
    15: ("scene", "Common-scale Canvas geometry/drawing/hit/accessibility, captured gestures, pan/zoom/selection/hold arbitration and animated bounds", ["component", "normal-app-ui"]),
    16: ("basic-table", "Normal two-player basic moves/orientation/life/piles and connected-only controls, delayed acknowledgements/reconnect/restart", ["integration", "normal-app-ui"]),
    17: ("values", "Direct card/player corner/counter/life/note/owner/face controls, numeric grammar, trimming, menu retention and captured scope", ["unit", "component", "normal-app-ui"]),
    18: ("library", "Ordered contents/top/bottom/subset transfers, logged inspection/View/Reveal fixed references, shuffle and captured-hand mulligans", ["unit", "integration", "normal-app-ui"]),
    19: ("tokens-links", "Catalog/Blank/Clone tokens, disappearance, generic undirected Links/removal and invariant-closed endpoint cleanup", ["unit", "integration", "normal-app-ui"]),
    20: ("tracking", "Phase/step/turn picker and relative progression, Table actions/die/coin, full quick catalog and separate constituent paths", ["unit", "component", "normal-app-ui"]),
    21: ("combat-stack", "Combat pair insertion/reflow/cleanup, live source write-through, independent Stack effects/guards and last-live token snapshots", ["unit", "integration", "normal-app-ui"]),
    22: ("playback", "Category/prefix grouping matrix, event comments/pings, exact centered timeline and live/grouped endpoint playback", ["unit", "property", "component", "normal-app-ui"]),
    23: ("undo-redo", "Server-owned latest-own selective removal/last-undone-first insertion, exact target/suffix tuples/N/forks/exposure and shared stacks", ["unit", "property", "integration", "normal-app-ui"]),
    24: ("branching", "Explicit continuation/first-success fork, compact group tree/exact captured activation, retained alternatives and concurrent growth", ["unit", "integration", "normal-app-ui"]),
    25: ("archive", "Hidden-path filter, reversible branch archive/restore/return and metadata recovery; switching alone does not archive", ["integration", "normal-app-ui"]),
    26: ("full-recovery", "Complete retained branched-tree automatic/coherent full download, incremental recovery, deleted-cursor ancestors and offline read-only review", ["unit", "integration", "normal-app-ui"]),
    27: ("chat", "Direct chat/composer/local unread presentation, account/incarnation binding and no sent-message Edit/Delete/Undo/receipts", ["integration", "normal-app-ui"]),
    28: ("rematch", "Singleton result suggestions, final-board-before-dialog, other-player acceptance, prepared-lobby restoration and fresh incarnation", ["integration", "normal-app-ui"]),
    29: ("deletion", "Either-participant confirmed active shared-game removal, captured incarnation/order, original-outcome recovery and access invalidation", ["integration", "normal-app-ui"]),
    30: ("notifications", "Invitations/activity channels, synthetic deliveries, optional FCM/non-GMS polling, local suppression/coalescing/permissions and account fences", ["integration", "device", "normal-app-ui"]),
    31: ("integrated-journeys", "Normal two-player/three-instance B1-B7 journeys across lifecycle, communication, restart/relogin/background and cross-task recovery", ["normal-app-ui"]),
    32: ("usability", "Both-orientation whole-game usability/accessibility/keyboard/overflow/tooltip arbitration and cumulative F01-F88 constraints", ["component", "normal-app-ui"]),
    33: ("performance", "Measured realistic core/server/sync/scene workloads, bounded memory/replay and physical reference-phone frames distinct from emulator timing", ["performance", "device"]),
    34: ("coverage", "Final full-suite real full-stack journeys, deferred I01f3 multi-device/reproducibility assertions and required lower-layer coverage, with affected rechecks after fixes; assignments are not passes", ["unit", "property", "integration", "normal-app-ui"]),
    35: ("compatibility", "API29/current/tablet/non-GMS, real signed upgrades/migrations/mixed-version negotiation and amd64/arm64 image/config/health qualification", ["integration", "device", "normal-app-ui"]),
    36: ("delivery", "Whole-R1 scope/evidence/operator interface reconciliation and complete D-303 both-orientation mock/unpictured interface inventory for independent B8 review", ["document", "normal-app-ui", "independent-review"]),
}

# Consecutive ranges are deliberate assignments, not keyword guesses. Overrides
# below retain cross-capability portions. A source change forces human reconciliation.
T_RANGES = """
1-6:22,23,24,25;7-8:17,18;9-10:26,14;11-12:13,24;13:18,23;14:24,22;15:1,14;16:30;17-18:13,22;19:25;20:17,23;21:17,19;22:32;23:5;24:2,13;25:28,23;26:14,16;27:23,14;28:24;29:14;30:22,30;31:14,30;32:32;33-35:13,14,24;
36-80:15,16;81-101:18;102-135:17;136-144:19;145-156:22;157-167:19;168-171:20;172-186:23;187-209:20;210-213:21;214:36;215-218:21;219:22;220:21,19;221:20;222-286:21;287-296:23;297-326:22;327-329:19,21;330-336:22;337-340:13;341-345:22,24;346:21;347:18;348:22;349-357:13,24;358-382:14;383-388:17,13;389-395:21;
396:2;397:3;398:9;399:7;400-405:10,11;406:11;407-413:18;414-418:9;419-420:2;421:4;422:2;423-427:6;428-429:7;430-432:8;433-439:11;440-445:10;446-447:11;448-454:12;455-463:12;464-473:28;474:26;475-479:16,14;480-482:3,14;483:7;484:9;485:26;486-490:30;491-492:7;493-500:24,25;501-533:23;534:23,22;535-537:26,23;538-539:28;540:27;541:22,23;542:7,12;543:3;544:36;545-548:21;549-550:5;551:1;552:34;553:31;554-555:16;556-557:21;558:23;559-561:18,23;562-563:23;564-565:22;566-569:21;570-571:17;572:2,4;573:17,2,6,27;574-575:22;576-577:28,14;578:33,23;579-580:24,21;581-582:12,28;583-584:22;585-587:18,23;588:17;589:19;590-593:18;594-596:19,18;597-598:20;599:18;600-601:3,18;602:22;603-604:28,22;605-607:25,23;608:14,10,27;609:23,14;610:14;611:13;612-613:20,14;614-618:26;619-620:15;621:2,3,4;622-624:30;625-626:5;627:5,8,19,26;628:8,6,5;629:5;630:1,35;631-633:35;634:11,18;635:11;636-639:13,14,9,12;640-641:26;642-644:14;645-646:22,15;647:14,3;648-649:14;650:13,19;651:23,24,25;652:33,23;653:26;654:13;655:11;656:4;657:2,4;658:30;659:5;660:36,29;661:33,35;662:1,35,13;663:22;664:14;665:18,14;666:4,3;667:8,9;668:21,13,33;669:12;670:18;671:24,14;672:26;673:13;
674:16,23;675:24,23;676:3,12;677:3,9,29;678:29;679:8;680:10,7;681:9;682:3,7,10,32;683-684:10;685:9,7,16;686:15,17;687:21,15;688:17,13,23;689:22,19;690:9,7,19,18;691:15,21;692:22;693-694:17;695:24,22;696:2,9,11;697:15;698:21;699:17;700:22;701:27;702:17;703:20,15;704:21,15;705:17;706:20;707:17;708:24,25;709:20,21,18;710:17;711:20;712:32,15,23,24;713-715:24,22
"""
P_RANGES = """
1:1,35;2-3:22;4-6:24;7:25;8:23,25;9:13,17;10:18;11:11,31;12:16,26;13:36;14:17,18,19,20,21;15:13,14;16:15,32;17:13,24;18-19:15,16;20:15;21:16,18;22:22,27;23:22;24-26:13,20,21;27:20;28-30:21;31:36;32-33:21;34:22,19;35:18,20,21;36-39:21;40:13,23;41:19;42:22;43:2,3,9,29;44:6,7,10;45:9,10,11,12;46:11,18;47:2,3,4,14;48:6;49:7,8;50-51:11;52-53:10,11;54:11;55:30;56:5,8;57:22,28;58:18;59:10,13,14,23,27;60:35;61:11,18;62:9,13,14;63:26;64:14,22,15;65:3,14;66:4;67:5;68:30;69:29,36;70:14;71:18,14;72:9,12
"""
F_RANGES = """
1-2:36;3:3,32;4-6:2,4;7-10:3;11:2,3;12:3;13-14:3,9;15:32;16:9,29;17:29;18-19:7,8;20:10;21:7,10;22:32,36;23-24:9;25-28:3,32;29:7;30-32:10,12;33:11,32;34-36:10;37:9;38:7;39:16;40:15;41-43:15,21,18;44:17;45:17,15;46:17,19,22;47:22;48:9,7,18,19;49-50:15,21;51:22;52:15,18,21;53-54:17;55:22;56-57:24;58:2;59:9,7,18,19;60:11,12;61-62:15;63:21;64:17;65:22;66:27;67-68:17;69:15;70:20;71-72:21,15;73:17;74:20;75:24,25;76-79:17;80-82:20;83:17;84:20;85:32,15;86-88:24,25,22
"""


def assignments(spec, count):
    result = {}
    for clause in spec.replace("\n", "").split(";"):
        if not clause.strip():
            continue
        numbers, owners = clause.split(":")
        ends = list(map(int, numbers.split("-")))
        for number in range(ends[0], ends[-1] + 1):
            if number in result:
                raise ValueError(f"duplicate assignment {number}")
            result[number] = [f"I{int(n):02}" for n in owners.split(",")]
    if set(result) != set(range(1, count + 1)):
        raise ValueError("missing/unknown ownership numbers")
    return result


def definitions(root=ROOT):
    result = {}
    for prefix, (path, count) in SOURCES.items():
        rows = {}
        for line_no, line in enumerate((root / path).read_text().splitlines(), 1):
            match = re.match(r"^\| (" + prefix + r"-?\d+) \| (.*) \|$", line)
            if not match:
                continue
            identifier = match[1]
            if identifier in rows:
                raise ValueError(f"duplicate source ID {identifier}")
            rows[identifier] = {"path": path, "line": line_no, "sha256": hashlib.sha256(line.encode()).hexdigest(),
                                "cells": match[2].split(" | ")}
        expected = {f"{prefix}{'-' if prefix != 'F' else ''}{n:0{2 if prefix == 'F' else 3}}" for n in range(1, count + 1)}
        if set(rows) != expected:
            raise ValueError(f"source {path}: missing={expected - rows.keys()} unknown={rows.keys() - expected}")
        result.update(rows)
    return result


def task_sources(root=ROOT):
    path = "docs/agent/implementation-plan.md"
    rows = {}
    for line_no, line in enumerate((root / path).read_text().splitlines(), 1):
        match = re.match(r"^\| (I\d{2}) \| (.*) \|$", line)
        if match:
            if match[1] in rows:
                raise ValueError(f"duplicate plan task {match[1]}")
            rows[match[1]] = {"path": path, "line": line_no,
                              "sha256": hashlib.sha256(line.encode()).hexdigest(), "outcome": match[2]}
    if set(rows) != {f"I{n:02}" for n in range(1, 37)}:
        raise ValueError("missing/unknown primary implementation-plan tasks")
    return rows


# Full retirement versus partial supersession are intentionally different.
DISPOSITIONS = {
    "P-013": ("phase-only", "D-304 authorizes stage 4; this is a delivery-order obligation, not an app feature.", ["D-304"]),
    "P-031": ("phase-only", "D-115/D-304 govern completeness/order, retained through I36 delivery reconciliation.", ["D-115", "D-304"]),
    "T-198": ("superseded", "Former priority-holder/Pass-cycle fixture is wholly retired; current absence checks remain T-199/T-200.", ["D-105", "T-199", "T-200"]),
    "T-214": ("phase-only", "Stage ordering/authorization is checked as delivery bookkeeping, not product acceptance.", ["D-115", "D-304"]),
    "T-296": ("superseded", "Offline Undo/continuation queue is wholly retired; only already-sent online recovery remains.", ["D-182", "D-246", "T-532", "T-636"]),
    "T-494": ("superseded", "Remembered-head pointer Redo fixture is retired; selective removal/reinsertion has separate current scenarios.", ["D-232", "T-522", "T-523"]),
    "T-610": ("conditional", "Optional revision-checksum chaining proposal, not approval of an event/Merkle chain. Retain model/protocol evaluation if selected; accepted-revision/base and checkpoint recovery are independently required by T-614/T-615.", ["D-259", "T-614", "T-615"]),
    "T-473": ("excluded", "Finished-session archive/review is post-R1. Check its absence does not remove current-game history or imply data erasure.", ["D-214", "T-474"]),
    "T-489": ("excluded", "In-app mute settings are post-R1; retain OS-channel behavior and required notification paths.", ["D-220", "T-490", "T-622"]),
    "T-544": ("excluded", "D-241 excludes broad authoring/coordination extras, not the listed confirmed toolkit; retain the negative scope audit.", ["D-241", "P-014", "P-056"]),
    "F01": ("phase-only", "Full same-URL publication is required when UI documents change; no UI-document edit occurs in I01b.", ["D-292"]),
    "F02": ("phase-only", "Cumulative feedback/provenance governs later revisions; not a runtime behavior test.", ["D-292"]),
    "F22": ("phase-only", "Copy audit produces proposals, not permission to apply every suggested deletion.", ["D-293"]),
    "F33": ("phase-only", "G00 mock retirement is a document obligation; retain actual table/reconnect backdrop checks in I11/I32.", ["D-294"]),
    "F46": ("phase-only", "Requested mocks exist; implementation must still test actions/tokens/pings in their owning tasks.", ["D-295"]),
}

# These global corrections constrain EVERY older example; row snapshots are the
# current corrected primary oracles, not copies of old decision-log proposals.
POLICIES = [
    ("crash-loss", "D-278", "No crash-durable outgoing/exposure journal or disk-before-display gate; startup syncs server; admitted work stays durable.", "docs/design/03-contracts/product.md", ["P-065", "T-647"]),
    ("fifo", "D-285", "Simple FIFO/TCP and server admission order, no missing-client-number gate or routine acknowledgement waits.", "docs/design/03-contracts/product.md", ["P-070", "T-664"]),
    ("revealing-prediction", "D-286", "Ordinary reconciliation accepts extra glimpses; no separate disclosure report/barrier or old-context reconstruction.", "docs/design/03-contracts/product.md", ["P-071", "T-665"]),
    ("cancel-reinvite", "D-287", "Existing-game reinvite never revives/replaces a cancelled game; original creation outcome is retained.", "docs/design/03-contracts/product.md", ["P-072", "T-669"]),
    ("runtime-repairs", "D-288", "Keep compact canonical Stack guard, bounded queues/streaming and actual token destination anchors; D-289 supersedes stronger auth/upload machinery.", "docs/design/02-experience/gaps.md", ["T-668", "T-670"]),
    ("ordinary-auth-http", "D-289", "New requests respect revocation; already-authorized work may finish. No special reset/commit/rehash races or durable Logout queue; definitions use one ordinary private JSON POST, no upload sessions. Node UUIDs remain locally known.", "docs/design/02-experience/gaps.md", ["T-656", "T-666", "T-667", "T-671"]),
]
PARTIAL = {
    "F03": ["F11", "F25"], "F11": ["F25"],
    "F20": ["F32", "F34"], "F28": ["F76"], "F32": ["F34"],
    "F41": ["F49"], "F53": ["F83"],
    "F63": ["F71"], "F68": ["F76"], "F70": ["F82"],
    "F75": ["F86", "F88"],
}


def build(root=ROOT):
    sources = definitions(root)
    tasks = task_sources(root)
    maps = {"P": assignments(P_RANGES, 72), "T": assignments(T_RANGES, 715), "F": assignments(F_RANGES, 88)}
    checks = [{"id": f"I{n:02}.{tag}", "task": f"I{n:02}", "scope": scope, "layers": layers,
               "execution": "pending", "source": tasks[f"I{n:02}"]}
              for n, (tag, scope, layers) in SCOPES.items()]
    check_by_task = {c["task"]: c for c in checks}
    items = []
    for identifier, source in sources.items():
        prefix = identifier[0]
        owners = maps[prefix][int(identifier.lstrip("PTF-"))]
        disposition, rationale, provenance = DISPOSITIONS.get(identifier, (
            "current", "Current primary-source oracle; implement only its approved R1 portion under the listed task scopes and global supersessions.", []))
        assertions = [{"id": f"{identifier}:{task}", "check": check_by_task[task]["id"],
                       "portion": check_by_task[task]["scope"], "status": "pending"} for task in owners]
        items.append({"id": identifier, "source": source, "disposition": disposition, "rationale": rationale,
                      "provenance": provenance, "partialSupersededBy": PARTIAL.get(identifier, []),
                      "implementationDependencies": owners, "assertions": assertions,
                      "completionCheck": "I36.delivery" if disposition in {"phase-only", "superseded", "excluded"} else "I34.coverage",
                      "completion": "unexecuted"})
    return {"formatVersion": 1, "sourceBaseline": "57226f9fc103c1a9d9ec3544c03110656d57daed",
            "owner": "I01b", "policies": [{"id": i, "decision": d, "rule": rule, "source": path, "references": refs}
                                             for i, d, rule, path, refs in POLICIES],
            "checkPlans": checks, "items": items, "executions": []}


def validate(data, root=ROOT):
    expected = build(root)
    # The foundation ledger has no product test executions. Later tasks extend
    # execution accounting with real command/evidence/portion bindings, not by
    # changing pending into passed or equating task status with scenario success.
    if data != expected:
        raise ValueError("Ledger differs from source-pinned ownership/disposition/assertion inventory; regenerate after explicit source reconciliation. No executed-pass claims are authorized by this foundation ledger.")
    state = json.loads((root / "docs/agent/implementation-state.json").read_text())
    known_tasks = {t["id"] for t in state["tasks"]}
    if not {c["task"] for c in data["checkPlans"]} <= known_tasks:
        raise ValueError("Unknown implementation task")
    decisions = (root / "docs/agent/decisions.md").read_text()
    identifiers = {item["id"] for item in data["items"]}
    for item in data["items"]:
        for ref in item["provenance"] + item["partialSupersededBy"]:
            if ref.startswith("D-"):
                if not re.search(r"\b" + re.escape(ref) + r"\b", decisions):
                    raise ValueError(f"Unknown decision {ref}")
            elif ref not in identifiers:
                raise ValueError(f"Unknown source ID {ref}")
    for policy in data["policies"]:
        if policy["decision"] not in (root / policy["source"]).read_text():
            raise ValueError(f"Missing supersession source {policy['id']}")
    return {prefix: sum(item["id"].startswith(prefix) for item in data["items"]) for prefix in SOURCES}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--write", action="store_true", help="Regenerate the explicit source-pinned inventory; does not execute product checks")
    args = parser.parse_args()
    path = ROOT / LEDGER
    if args.write:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(build(), ensure_ascii=False, indent=2) + "\n")
    counts = validate(json.loads(path.read_text()))
    print("PASS primary source definitions/rows/hashes:", counts, "total=", sum(counts.values()))
    print("PASS explicit I01-I36 ownership, pending partial assertions and completion dependencies")
    print("PASS current/superseded/conditional/phase-only/excluded dispositions and supersession provenance")
    print("PASS no product execution/pass claim; T-610 conditional and D-278/D-285-D-289 corrections retained")


if __name__ == "__main__":
    main()
