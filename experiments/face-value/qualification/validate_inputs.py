#!/usr/bin/env python3
import hashlib
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent
CONTROL = ROOT / "control/fv-temur-chrysalis-e-final-forge-75.dck"
MANIFEST = ROOT / "metagame-snapshot-2026-09-20.json"
IDENTITIES = ROOT / "input-identities.sha256"
EXPECTED_ARCHETYPES = {
    "mono-red-madness",
    "mono-blue-terror",
    "grixis-affinity",
    "monster-tron",
    "jund-wildfire",
    "elves",
    "mono-red-rally",
    "mono-blue-faeries",
}


def parse_deck(path: Path) -> tuple[int, int, str]:
    section = None
    totals = {"main": 0, "sideboard": 0}
    name = None
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line:
            continue
        if line.startswith("[") and line.endswith("]"):
            section = line[1:-1].lower()
            continue
        if line.startswith("Name="):
            name = line.split("=", 1)[1]
        match = re.match(r"^(\d+)\s+\S", line)
        if match and section in totals:
            totals[section] += int(match.group(1))
    assert name, f"missing Name metadata: {path}"
    assert totals == {"main": 60, "sideboard": 15}, (path, totals)
    return totals["main"], totals["sideboard"], name


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main() -> None:
    data = json.loads(MANIFEST.read_text(encoding="utf-8"))
    rows = data["archetypes"]
    ids = {row["id"] for row in rows}
    assert len(rows) == 8, len(rows)
    assert ids == EXPECTED_ARCHETYPES, ids
    assert all(row["primary_tier"] == "A" for row in rows)
    assert all(row["list_url"].startswith("https://www.mtggoldfish.com/deck/") for row in rows)
    assert all(row["download_url"].startswith("https://www.mtggoldfish.com/deck/download/") for row in rows)

    _, _, control_name = parse_deck(CONTROL)
    seen_names = {control_name}
    report = {
        "status": "IMMUTABLE_INPUTS_VALIDATED",
        "control": {"path": str(CONTROL.relative_to(ROOT)), "sha256": sha256(CONTROL)},
        "opponents": [],
    }
    for row in rows:
        path = ROOT / row["list_file"]
        assert path.is_file(), path
        _, _, name = parse_deck(path)
        assert name not in seen_names, name
        seen_names.add(name)
        report["opponents"].append(
            {"id": row["id"], "name": name, "path": row["list_file"], "sha256": sha256(path)}
        )

    expected = {}
    for line in IDENTITIES.read_text(encoding="utf-8").splitlines():
        digest, relpath = line.split(maxsplit=1)
        expected[relpath] = digest
    actual = {report["control"]["path"]: report["control"]["sha256"]}
    actual.update({row["path"]: row["sha256"] for row in report["opponents"]})
    assert actual == expected, {"expected": expected, "actual": actual}

    out = ROOT / "input-validation-report.json"
    out.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
