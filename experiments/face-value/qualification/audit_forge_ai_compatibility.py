#!/usr/bin/env python3
"""Fail-closed static audit of frozen decks against a pinned Forge card corpus."""

import argparse
import json
import re
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parent


def folded(value: str) -> str:
    value = unicodedata.normalize("NFKD", value)
    return "".join(ch for ch in value if not unicodedata.combining(ch)).casefold()


def deck_cards(path: Path) -> list[dict]:
    section = None
    rows = []
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if line.startswith("[") and line.endswith("]"):
            section = line[1:-1].lower()
            continue
        match = re.match(r"^(\d+)\s+(.+)$", line)
        if match and section in {"main", "sideboard"}:
            rows.append({"section": section, "quantity": int(match.group(1)), "name": match.group(2)})
    return rows


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--forge-root", required=True, type=Path)
    parser.add_argument("--output", default="forge-ai-compatibility-report.json", type=Path)
    args = parser.parse_args()

    scripts = args.forge_root / "forge-gui/res/cardsfolder"
    exact = {}
    normalized = {}
    for path in scripts.rglob("*.txt"):
        text = path.read_text(encoding="utf-8", errors="replace")
        match = re.search(r"^Name:(.+)$", text, re.M)
        if not match:
            continue
        name = match.group(1).strip()
        exact[name] = (path, text)
        normalized.setdefault(folded(name), []).append(name)

    manifest = json.loads((ROOT / "metagame-snapshot-2026-09-20.json").read_text())
    decks = [("control", ROOT / "control/fv-temur-chrysalis-e-final-forge-75.dck")]
    decks.extend((row["id"], ROOT / row["list_file"]) for row in manifest["archetypes"])
    report = {"classification": "PINNED_FORGE_STATIC_AI_COMPATIBILITY_AUDIT", "decks": {}, "blocking_findings": []}
    for deck_id, path in decks:
        findings = []
        for card in deck_cards(path):
            name = card["name"]
            if name not in exact:
                candidates = normalized.get(folded(name), [])
                finding = {**card, "finding": "EXACT_CARD_IDENTITY_NOT_FOUND", "canonical_candidates": candidates}
                findings.append(finding)
                report["blocking_findings"].append({"deck": deck_id, **finding})
                continue
            script, text = exact[name]
            annotations = re.findall(r"^AI:RemoveDeck:(All|Random)$", text, re.M)
            if annotations:
                finding = {**card, "finding": "FORGE_AI_REMOVE_DECK_ANNOTATION", "annotations": annotations, "script": str(script.relative_to(args.forge_root))}
                findings.append(finding)
                if "All" in annotations:
                    report["blocking_findings"].append({"deck": deck_id, **finding})
        report["decks"][deck_id] = {"path": str(path.relative_to(ROOT)), "findings": findings}

    report["status"] = "BLOCKED" if report["blocking_findings"] else "GREEN"
    args.output.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n")
    print(json.dumps({"status": report["status"], "blocking_findings": len(report["blocking_findings"])}, indent=2))
    if report["blocking_findings"]:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
