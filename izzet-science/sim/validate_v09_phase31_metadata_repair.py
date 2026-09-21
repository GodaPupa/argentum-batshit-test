#!/usr/bin/env python3
"""Seed-free audit of Phase-31 metadata repair.

This validates repository-bound identities and the corrected freeze binding without
loading or exposing the quarantined raw seed vector.
"""
from pathlib import Path
import hashlib, re

ROOT=Path(__file__).resolve().parents[1]
REPAIR=ROOT/"v09-phase31-seed-freeze-metadata-repair.md"
REG=ROOT/"seed-registry-v1.json"
CONTROL=ROOT/"v0.7-control.md"
RUNNER=ROOT/"sim"/"sampled_pilot_runner.py"

EXPECTED={
    "registry":"cc1d9777837dbb255d4bffc92b1da611ad2db52d259ab725918487639ad76565",
    "control":"726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01",
    "runner":"864d46745c7ffd1c1e9c8eef48c5a0ad14b34938aa0a6066d94ab7a394516fc3",
    "vector":"5d8f9f758ed87286efb0ca07b74cec652a44304158e867f4c1aa6fc8a3bb824f",
    "assignment":"b2da95af015d1acd84bebd6794acad5c8a81530ccae74f41e6f925ef301e5be3",
    "quarantine_zip":"83c0c75363ff0d9030cd1ed6f3441a201514ddb8920562aa2791b04fc4651f6a",
    "public_zip":"2efb59ee5f1be34c0e989b84b20c800b9dc7a24cc54b3eea0d97fe32faf75b9a",
}

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    text=REPAIR.read_text()
    assert "V09_PHASE31_FREEZE_METADATA_REPAIRED_VECTOR_UNCHANGED" in text
    assert sha(REG)==EXPECTED["registry"]
    assert sha(CONTROL)==EXPECTED["control"]
    assert sha(RUNNER)==EXPECTED["runner"]

    for key in ("vector","assignment","quarantine_zip","public_zip"):
        assert EXPECTED[key] in text, f"missing {key} binding"

    assert "generated/consumed: 12/0" in text
    assert "games initialized: 0" in text
    assert "outcome exposure: 0/12" in text
    assert "Do not regenerate it." in text
    assert "raw seed values remain quarantined" in text

    # Repair must mention both obsolete and corrected registry digests and state
    # that only the metadata field is superseded.
    obsolete="c235f9cd89206490ec5c9105255c187a0f3d845cfb5877fbe4110c926e3dfb2f"
    assert obsolete in text and EXPECTED["registry"] in text
    assert "superseded only for its" in text

    # Raw values must not appear as a 12-value decimal vector in repair prose.
    nums=re.findall(r"(?<![0-9])[-]?[0-9]{15,20}(?![0-9])", text)
    assert len(nums)==0, "repair prose unexpectedly exposes large decimal seed-like values"

    print("V09_PHASE31_METADATA_REPAIR_AUDIT_PASS")

if __name__=="__main__":
    main()
