#!/usr/bin/env python3
"""Static, seed-free validator for the Phase-29 sampled-pilot design."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GATE = ROOT / "v09-phase29-sampled-pilot-design-gate.md"

REQUIRED = [
    "Disposition: `V09_PHASE29_SAMPLED_PILOT_DESIGN_GATE_OPEN`",
    "exactly 12 games for the first pilot",
    "6 Izzet play / 6 Izzet draw",
    "no adaptive sample-size increase",
    "fresh unique nonzero signed 64-bit values",
    "operating-system cryptographic",
    "no rerolls, regeneration, outcome-conditioned replacement, or reuse",
    "game result",
    "Capsize buyback cast count",
    "first meaningful Capsize interaction turn",
    "primary combo assembled",
    "primary combo attempt",
    "deterministic lethal opportunity",
    "commander damage received",
    "descriptive analysis only",
    "no promotion/rejection of cards from this 12-game pilot",
    "illegal action",
    "hidden-information leak",
    "seed mismatch/reuse",
    "non-deterministic replay from same seed and frozen runner",
    "missing event ledger",
    "terminal-state accounting defect",
    "expose outcomes only through the canonical artifact",
    "generating experimental seeds",
    "executing games",
    "outcome exposure",
    "runner construction and deterministic seed-free validation",
    "Seed generation remains separately gated",
]

FORBIDDEN_EXECUTION_MARKERS = [
    "secrets.SystemRandom(",
    "random.SystemRandom(",
    "os.urandom(",
    "subprocess.run(",
    "execute_game(",
    "run_matchup(",
]

def main():
    text = GATE.read_text()
    normalized = " ".join(text.split()).lower()
    missing = [item for item in REQUIRED if " ".join(item.split()).lower() not in normalized]
    assert not missing, f"missing required design clauses: {missing}"

    # Freeze exact pilot cardinalities and prevent accidental widening.
    assert text.count("exactly 12 games for the first pilot") == 1
    assert text.count("6 Izzet play / 6 Izzet draw") == 1

    for marker in FORBIDDEN_EXECUTION_MARKERS:
        assert marker not in text, f"execution surface present in design gate: {marker}"

    # Parent provenance must remain explicit.
    for phase in ("Phase 25", "Phase 26", "Phase 27", "Phase 28"):
        assert phase in text

    assert "experimental seeds generated: 0" in text
    assert "experimental seeds consumed: 0" in text
    assert "sampled games: 0" in text
    assert "outcome exposure: 0" in text
    assert "card changes: 0" in text

    print("V09_PHASE29_SAMPLED_PILOT_DESIGN_VALIDATION_PASS")

if __name__ == "__main__":
    main()
