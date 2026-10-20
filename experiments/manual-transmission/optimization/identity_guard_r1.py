#!/usr/bin/env python3
"""Manual Transmission identity guard R1: static, read-only, zero gameplay."""
import argparse
import hashlib
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
CONTROL = Path("experiments/manual-transmission/control/v0.7.decklist.txt")
PROVENANCE = Path("experiments/manual-transmission/PROVENANCE_IMPORT.md")
CONCLUSION = Path("experiments/manual-transmission/results/FINAL_CONCLUSION_2026_09_24.md")
FROZEN_BLOB = "c350bdc86eb2d37e5da4eb712e8ee2fa729b7e89"
INHERITED_ID = "6c28f0629d8ff0a859784f8c7dc0a47054b298d17c6b8e041a4a0a1d8744f111"
EXPECTED_SECTIONS = {"Commander": 1, "Creatures": 52, "Noncreatures": 15, "Lands": 32}
MODE_ANCHORS = {
    "Cruise": (
        "Llanowar Elves", "Birds of Paradise", "Sakura-Tribe Elder",
        "Elvish Visionary", "Beast Whisperer", "Soul of the Harvest", "Garruk’s Uprising",
    ),
    "Sport": (
        "World Breaker", "Foundation Breaker", "Bane of Progress",
        "Amphin Mutineer", "Spellskite", "Beast Within", "Reality Shift",
    ),
    "Race": (
        "Animar, Soul of Elements", "Ancestral Statue", "Walking Ballista",
        "Purphoros, God of the Forge", "Imperial Recruiter", "Fauna Shaman",
    ),
    "Combat": ("Decimator of the Provinces",),
}
DEFERRED = (
    "Food Chain", "Worldly Tutor", "Force of Will", "Force of Negation",
    "Fierce Guardianship", "Deflecting Swat", "Swan Song", "Destiny Spinner",
    "Elvish Spirit Guide", "Bloom Tender",
)

def git_blob_sha(data):
    return hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data).hexdigest()

def scan(data):
    if git_blob_sha(data) != FROZEN_BLOB:
        raise ValueError("Wrong/modified frozen v0.7 Git blob")
    sections, cards, category = {}, [], None
    for number, raw in enumerate(data.decode("utf-8").splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if line in EXPECTED_SECTIONS:
            category = line
            continue
        match = re.fullmatch(r"([1-9]\d*) (.+)", line)
        if category is None or match is None:
            raise ValueError("Invalid deck line %s: %s" % (number, line))
        quantity = int(match.group(1))
        sections[category] = sections.get(category, 0) + quantity
        cards.extend([match.group(2)] * quantity)
    if sections != EXPECTED_SECTIONS:
        raise ValueError("Section counts changed: %r" % sections)
    if len(cards) != 100 or len(set(cards)) != 93:
        raise ValueError("Control is not exact 100-card / 93-name baseline")
    if cards.count("Animar, Soul of Elements") != 1:
        raise ValueError("Commander is not exactly one Animar")
    missing = {
        mode: [name for name in names if cards.count(name) != 1]
        for mode, names in MODE_ANCHORS.items()
    }
    if any(missing.values()):
        raise ValueError("Original gear/combat role anchors lost: %r" % missing)
    return {
        "gitBlobSha": git_blob_sha(data),
        "inheritedExperimentId": INHERITED_ID,
        "inheritedIdIsNotPlaintextHash": True,
        "cardCount": len(cards),
        "uniqueCardNames": len(set(cards)),
        "sections": sections,
        "modeAnchorCounts": {k: len(v) for k, v in MODE_ANCHORS.items()},
        "staticAnchorsAreNotGameplayProof": True,
    }

def audit(root):
    deck = scan((root / CONTROL).read_bytes())
    provenance = (root / PROVENANCE).read_text(encoding="utf-8")
    conclusion = (root / CONCLUSION).read_text(encoding="utf-8")
    for label, content, markers in [
        ("import", provenance, [
            "Game Changers: 0", "KEEP_V07", "No v0.8",
            "Bloom Tender", "Worldly Tutor", "Fierce Guardianship",
        ]),
        ("conclusion", conclusion, [
            "PASS_BOUNDED_POLICY_ELASTICITY_KEEP_V07",
            "Card changes authorized by the final gate: **0**",
            "Cruise", "Sport", "Race", "does **not** establish real-game",
        ]),
    ]:
        missing = [x for x in markers if x not in content]
        if missing:
            raise ValueError("Accepted %s evidence mismatch: %r" % (label, missing))
    return {
        "status": "PASS_STATIC_IDENTITY_GUARD",
        "scope": "READ_ONLY_NO_GAMEPLAY",
        "qualifiedFullGameWinRate": False,
        "candidateHardwarePromoted": False,
        "newGameplaySamples": 0,
        "acceptedBaselineGameChangers": 0,
        "deferredHardwareRequiresFreshStructuralEvidence": list(DEFERRED),
        "control": deck,
    }

def self_test(root):
    original = (root / CONTROL).read_bytes()
    scan(original)
    changes = [
        original.replace(b"1 Ancestral Statue", b"1 Island", 1),
        original.replace(b"1 Animar, Soul of Elements", b"2 Animar, Soul of Elements", 1),
        original + b"\n1 Sol Ring\n",
    ]
    if any(x == original for x in changes):
        raise AssertionError("Negative case did not mutate the control")
    for altered in changes:
        try:
            scan(altered)
        except ValueError:
            pass
        else:
            raise AssertionError("Modified hardware was accepted")
    print("MANUAL_IDENTITY_R1_NEGATIVE_CASES_REJECTED=3; NO_GAMES")

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--self-test", action="store_true")
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        self_test(ROOT)
    receipt = audit(ROOT)
    if args.json:
        print(json.dumps(receipt, sort_keys=True, indent=2))
    elif not args.self_test:
        print("MANUAL_IDENTITY_R1_PASS; 100_CARDS; ZERO_CHANGED; NO_GAMES")

if __name__ == "__main__":
    main()
