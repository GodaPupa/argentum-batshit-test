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
    assert hashlib.sha256(deck.read_bytes()).hexdigest() == protocol["hardware"]["sha256"]

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
