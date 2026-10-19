#!/usr/bin/env python3
"""Summarize preserved real action streams without inferring unobserved combo wins."""
import json
from pathlib import Path
import sys

for folder in map(Path, sys.argv[1:]):
    summary = json.loads((folder / "summary.json").read_text())
    submitted = {}
    results = {}
    for line in (folder / "actions.jsonl").read_text().splitlines():
        row = json.loads(line)
        if row.get("record") == "submit" or ("action" in row and "events" in row):
            submitted[row["index"]] = row
        if row.get("record") == "result" or "events" in row:
            results[row["index"]] = row
    rejected = [i for i, row in results.items() if row.get("error") is not None]
    relevant = []
    for index, row in submitted.items():
        action = json.loads(row["action"]) if isinstance(row["action"], str) else row["action"]
        if row.get("sourceName") in {"Animar, Soul of Elements", "Ancestral Statue", "Walking Ballista", "Purphoros, God of the Forge"} or row.get("decisionSource") == "Ancestral Statue":
            result = results.get(index, {})
            relevant.append({"index": index, "turn": row.get("turn"), "actor": row.get("actor"),
                             "source": row.get("sourceName"), "decisionSource": row.get("decisionSource"),
                             "selectedCardNames": row.get("selectedCardNames"), "action": action,
                             "error": result.get("error"),
                             "events": [e for e in result.get("events", []) if e.startswith(("SpellCastEvent(", "ManaSpentEvent(", "AbilityActivatedEvent(", "GameEndedEvent("))]})
    report = {"folder": str(folder), "summary": summary, "submittedRows": len(submitted),
              "resultRows": len(results), "acceptedRows": len(results) - len(rejected),
              "rejectedIndices": rejected, "relevantActions": relevant}
    target = folder / "trace-audit.json"
    target.write_text(json.dumps(report, indent=2) + "\n")
    print(target, "accepted", report["acceptedRows"], "rejected", rejected)
