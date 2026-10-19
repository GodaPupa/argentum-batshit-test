#!/usr/bin/env python3
"""Offline archive/source binding verification; does not rerun games."""
import hashlib
import json
from pathlib import Path
from zipfile import ZipFile
from xml.etree import ElementTree

root = Path(__file__).resolve().parents[2]
receipts = json.loads((Path(__file__).parent / "artifact-receipts.json").read_text())
for receipt in receipts:
    archive = root / receipt["localPath"]
    data = archive.read_bytes()
    assert len(data) == receipt["size_in_bytes"], archive
    assert "sha256:" + hashlib.sha256(data).hexdigest() == receipt["digest"], archive
    with ZipFile(archive) as z:
        sources = [n for n in z.namelist() if n.endswith("source-sha.txt")]
        assert len(sources) == 1, (archive, sources)
        assert z.read(sources[0]).decode().strip() == receipt["workflow_run"]["head_sha"], archive
        if receipt["workflow_run"]["head_sha"] == "004ec925b3edc94f972d0141d1edb1e294e641b5":
            prefix = "r4-output/smoke/seed-202610191401/"
            summary = json.loads(z.read(prefix + "summary.json"))
            initial = json.loads(z.read(prefix + "initial-state.json"))
            final = json.loads(z.read(prefix + "final-state.json"))
            rows = [json.loads(line) for line in z.read(prefix + "actions.jsonl").decode().splitlines()]
            submissions = [r for r in rows if r["record"] == "submit"]
            results = [r for r in rows if r["record"] == "result"]
            assert summary["status"] == "COMPLETE" and summary["error"] is None
            assert len(submissions) == len(results) == summary["accepted"] == summary["submitted"] == 1943
            assert [r["index"] for r in results] == list(range(1, 1944))
            assert all(r["error"] is None for r in results)
            assert final["gameOver"] and results[-1]["gameOver"]
            assert final["winnerId"] == summary["winner"] == results[-1]["winner"] == "manual-mirror-3"
            assert len(initial["turnOrder"]) == 4
            zones = initial["zones"]
            zone_map = {(zones[i]["ownerId"], zones[i]["zoneType"]): zones[i + 1] for i in range(0, len(zones), 2)}
            for player in initial["turnOrder"]:
                assert len(zone_map[(player, "Command")]) == 1
                assert len(zone_map[(player, "Library")]) + len(zone_map[(player, "Hand")]) == 99
                life = initial["entities"][player]["com.wingedsheep.engine.state.components.identity.LifeTotalComponent"]
                assert life["life"] == 40
            suites = [ElementTree.fromstring(z.read(n)) for n in z.namelist()
                      if n.startswith("r4-output/regression-junit/TEST-") and n.endswith(".xml")]
            assert sum(int(s.attrib["tests"]) for s in suites) == 121
            assert all(int(s.attrib[k]) == 0 for s in suites for k in ("failures", "errors", "skipped"))
    print(f"VERIFIED {archive.name}: {receipt['workflow_run']['head_sha']}")
