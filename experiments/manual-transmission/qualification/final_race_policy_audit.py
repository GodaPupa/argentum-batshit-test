from __future__ import annotations
import hashlib, json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def main():
    protocol = json.loads((ROOT / "protocols/final-race-policy-r1.json").read_text())
    assert protocol["protocol"] == "MT_FINAL_RACE_POLICY_R1_2026_09_24"
    assert protocol["hardware"]["sha256"] == "6c28f0629d8ff0a859784f8c7dc0a47054b298d17c6b8e041a4a0a1d8744f111"
    assert protocol["hardware"]["game_changers"] == 0
    assert protocol["hardware"]["disposition"] == "KEEP_V07"
    assert protocol["acceptance"]["card_changes_authorized"] is False
    assert protocol["acceptance"]["win_rate_claim_authorized"] is False

    deck = ROOT / "control/v0.7.decklist.txt"
    deck_text = deck.read_text()
    # The v0.7 SHA is an inherited experiment identifier, not a digest of this plaintext
    # serialization. PROVENANCE_IMPORT.md records that distinction explicitly. Verify that
    # the frozen control declares the inherited identifier and still contains exactly 100 cards;
    # do not silently redefine the experiment identity as a file-byte hash.
    declared = f"# Declared frozen deck SHA-256: {protocol['hardware']['sha256']}"
    assert declared in deck_text
    card_count = 0
    for raw in deck_text.splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        parts = line.split(" ", 1)
        if len(parts) == 2 and parts[0].isdigit():
            card_count += int(parts[0])
    assert card_count == 100, f"frozen v0.7 card count drift: {card_count}"

    provenance = (ROOT / "PROVENANCE_IMPORT.md").read_text()
    assert "The declared frozen SHA is an inherited experiment identifier." in provenance
    assert "not represented as the serialization that originally produced the inherited SHA" in provenance

    policy_root = ROOT / "policies"
    for name in protocol["acceptance"]["policy_files_required"]:
        text = (policy_root / name).read_text()
        assert "ACCEPTED" in text, f"{name} is not accepted"

    final_policy = (policy_root / "RACE-FINAL-R1.md").read_text()
    for overlay in protocol["opponent_overlays"].values():
        assert overlay in final_policy
    assert "changes **zero cards**" in final_policy
    assert "not cEDH win rates" in final_policy

    out = {
        "protocol": protocol["protocol"],
        "status": "PASS",
        "hardware_sha256": protocol["hardware"]["sha256"],
        "overlay_count": len(protocol["opponent_overlays"]),
        "generic_layer_count": len(protocol["generic_layers"]),
        "card_changes_authorized": False,
        "win_rate_claim_authorized": False,
    }
    print(json.dumps(out, indent=2, sort_keys=True))

if __name__ == "__main__":
    main()
