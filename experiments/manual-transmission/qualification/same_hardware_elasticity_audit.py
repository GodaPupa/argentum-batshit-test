from __future__ import annotations
import argparse, hashlib, json, re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

AXES = {
    "hashaton": "hashaton",
    "magda": "magda",
    "blue-farm": "blue-farm",
    "shorikai": "shorikai",
    "sisay": "sisay",
    "rogsi": "rogsi",
    "kinnan-basalt": "kinnan-basalt",
}

def numeric_error_sum(summary: dict, prefix: str) -> int:
    total = 0
    for key, value in summary.items():
        if not key.startswith(prefix):
            continue
        if isinstance(value, bool):
            continue
        if not isinstance(value, (int, float)):
            continue
        if any(token in key for token in ("false", "error", "illegal", "overcount")):
            total += int(value)
    return total

def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--summary-root", required=True)
    ap.add_argument("--out", required=True)
    args = ap.parse_args()

    protocol = json.loads((ROOT / "protocols/same-hardware-elasticity-r1.json").read_text())
    assert protocol["protocol"] == "MT_SAME_HARDWARE_ELASTICITY_R1_2026_09_24"
    assert protocol["hardware"]["sha256"] == "6c28f0629d8ff0a859784f8c7dc0a47054b298d17c6b8e041a4a0a1d8744f111"
    assert protocol["hardware"]["card_changes_authorized"] is False

    deck = (ROOT / "control/v0.7.decklist.txt").read_text()
    assert f"# Declared frozen deck SHA-256: {protocol['hardware']['sha256']}" in deck
    count = 0
    for raw in deck.splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        n, _ = line.split(" ", 1)
        count += int(n)
    assert count == 100

    imported_path = ROOT / "imports/README_D2P.md"
    imported_bytes = imported_path.read_bytes()
    imported_sha = hashlib.sha256(imported_bytes).hexdigest()
    assert imported_sha == protocol["low_gear_import"]["sha256"], (imported_sha, protocol["low_gear_import"]["sha256"])
    imported_text = imported_bytes.decode()
    assert "Manual Transmission **v0.7 remains frozen**" in imported_text
    assert "Cruise/Sport T7 mean draw loss" in imported_text
    assert "Cruise T7 draw/cast deltas" in imported_text
    assert "Sport -0.275/-0.133" in imported_text

    modes = protocol["mode_definitions"]
    assert len(modes["Cruise-R1"]["generic_layers"]) < len(modes["Sport-R1"]["generic_layers"])
    assert modes["Cruise-R1"]["opponent_overlays"] == []
    assert modes["Sport-R1"]["opponent_overlays"] == []
    assert len(modes["Race-R1"]["opponent_overlays"]) == 7
    assert modes["Race-R1"]["generic_layers"] == modes["Sport-R1"]["generic_layers"]

    final_race = (ROOT / "policies/RACE-FINAL-R1.md").read_text()
    assert "Status: **ACCEPTED**" in final_race
    for overlay in modes["Race-R1"]["opponent_overlays"]:
        assert overlay in final_race

    summary_root = Path(args.summary_root)
    axis_results = {}
    for axis, dirname in AXES.items():
        summary = json.loads((summary_root / dirname / "summary.json").read_text())
        assert summary.get("pass") is True, (axis, summary)
        sport_errors = numeric_error_sum(summary, "reference_")
        race_errors = numeric_error_sum(summary, "candidate_")
        assert sport_errors > 0, (axis, "reference must expose a real gap", summary)
        assert race_errors == 0, (axis, "Race candidate regression", summary)
        axis_results[axis] = {
            "rows": summary["rows"],
            "sport_reference_errors": sport_errors,
            "race_candidate_errors": race_errors,
            "rows_sha256": summary.get("rows_sha256"),
        }

    result = {
        "protocol": protocol["protocol"],
        "status": "PASS_BOUNDED_POLICY_ELASTICITY_KEEP_V07",
        "hardware_sha256": protocol["hardware"]["sha256"],
        "hardware_card_count": count,
        "low_gear_import_sha256": imported_sha,
        "mode_policy_surface_counts": {
            "Cruise-R1": len(modes["Cruise-R1"]["generic_layers"]),
            "Sport-R1": len(modes["Sport-R1"]["generic_layers"]),
            "Race-R1": len(modes["Race-R1"]["generic_layers"]) + len(modes["Race-R1"]["opponent_overlays"]),
        },
        "axes": axis_results,
        "sport_reference_errors_total": sum(x["sport_reference_errors"] for x in axis_results.values()),
        "race_candidate_errors_total": sum(x["race_candidate_errors"] for x in axis_results.values()),
        "same_hardware": True,
        "card_changes_authorized": False,
        "win_rate_gradient_claimed": False,
        "cruise_vs_sport_quantitative_superiority_claimed": False,
        "disposition": "KEEP_V07",
    }
    Path(args.out).write_text(json.dumps(result, indent=2, sort_keys=True) + "\n")
    print(json.dumps(result, indent=2, sort_keys=True))

if __name__ == "__main__":
    main()
