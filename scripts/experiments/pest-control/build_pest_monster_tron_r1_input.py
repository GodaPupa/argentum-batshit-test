#!/usr/bin/env python3
from __future__ import annotations

import argparse
import csv
import hashlib
import io
from pathlib import Path
import subprocess
import zipfile

FREEZE = "41716f6918ec559ee60b14815b5b0f23d3aaf38a"
ROOT = "docs/experiments/pest-control/r1-production-freeze-20261005"
PROTOCOL = "PEST_CONTROL_V10_VS_MEHANSKE_MONSTER_TRON_2026_09_21_PREBOARD_V1"
R1_BLOCK = PROTOCOL + "_NONEXPERIMENTAL_REPLACEMENT_SMOKE_4_R1"
TRANSPORT_BLOCK = R1_BLOCK
PEST = "7be61a66e2c7654428043d56b411afb4d406f02dfcc4eb7f15a62295d4e906f5"
TRON = "79ffc53ac331beafeb1ef4510ce174d01fb2685d963a4c1485f04edbf47c064f"
RUNNER = "9829ee98869343cd48dceaa9a27c56ed27c6b3bc"
VECTOR_SHA = "8cdba4018aac3f423d92981581b628a8647e15be1f916a32c81ca1aef6ccff89"
R1_ASSIGNMENTS_SHA = "edd4d831ed0e9cd319ce908e71cca117203a1f5970e00441f3c9310f10e412c7"
INPUT_ASSIGNMENTS_SHA = "c1a9b63d3ffd5279d663bcbdde7d4595557bc189aa45e29a87fc5db2148b180d"
FREEZE_MANIFEST_SHA = "8c678ac397016c1e68046e1101869967d9c9544594ae9af642f035bd11738e2a"
QUARANTINE_SHA = "c59ef02660abdc99a298bbb3aec9010d29ee745bcf8bfd48ccfe684f3787896b"
INPUT_INVENTORY_SHA = "1cbe37abfe55b858e9cc27b791c41d12a30a5f41dfd5a4c1e8fa37f7a6c31a2a"
ARCHIVE_SHA = "e9ddafe0a7aec28cb07950f2f1d5908a31af677ff58415e809117575f46d6817"

def sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()

def git_show(root: Path, name: str) -> bytes:
    return subprocess.check_output(
        ["git", "show", f"{FREEZE}:{ROOT}/{name}"], cwd=root
    )

def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--repo-root", type=Path, default=Path("."))
    ap.add_argument("--output", type=Path, required=True)
    args = ap.parse_args()
    root = args.repo_root.resolve()

    ordered = git_show(root, "ordered-members.txt")
    authoritative = git_show(root, "assignments.csv")
    freeze_manifest = git_show(root, "freeze-manifest.json")
    quarantine = git_show(root, "quarantine.json")

    assert sha(ordered) == VECTOR_SHA
    assert sha(authoritative) == R1_ASSIGNMENTS_SHA
    assert sha(freeze_manifest) == FREEZE_MANIFEST_SHA
    assert sha(quarantine) == QUARANTINE_SHA

    text = authoritative.decode("utf-8")
    assert text.endswith("\n") and "\r" not in text
    rows = list(csv.DictReader(io.StringIO(text)))
    assert len(rows) == 4
    assert [int(r["game_number"]) for r in rows] == [1, 2, 3, 4]
    assert all(r["r1_block"] == R1_BLOCK for r in rows)
    assert all(r["pest_main_sha256"] == PEST and r["monster_tron_main_sha256"] == TRON for r in rows)

    old_header = (
        "protocol_id,block_id,game_number,seed_decimal,seed_hex,pest_seat,monster_tron_seat,"
        "starting_deck,pest_play_draw,pest_main_sha256,monster_tron_main_sha256,qualified_runner"
    )
    out = [old_header]
    seat_names = {"0": "SEAT_ZERO", "1": "SEAT_ONE"}
    for row in rows:
        out.append(",".join([
            PROTOCOL, TRANSPORT_BLOCK, row["game_number"], row["seed_decimal"], row["seed_hex"],
            seat_names[row["pest_seat"]], seat_names[row["monster_tron_seat"]],
            row["starting_deck"], row["pest_play_draw"], PEST, TRON, RUNNER,
        ]))
    compat = ("\n".join(out) + "\n").encode("utf-8")
    assert sha(compat) == INPUT_ASSIGNMENTS_SHA

    inventory = (
        f"{VECTOR_SHA}  ordered-seeds.txt\n"
        f"{INPUT_ASSIGNMENTS_SHA}  assignments.csv\n"
        f"{FREEZE_MANIFEST_SHA}  freeze-manifest.json\n"
        f"{QUARANTINE_SHA}  quarantined-vector.json\n"
    ).encode("utf-8")
    assert sha(inventory) == INPUT_INVENTORY_SHA

    members = [
        ("ordered-seeds.txt", ordered),
        ("assignments.csv", compat),
        ("freeze-manifest.json", freeze_manifest),
        ("quarantined-vector.json", quarantine),
        ("artifacts.sha256", inventory),
    ]
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w", compression=zipfile.ZIP_STORED) as zf:
        for name, raw in members:
            info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_STORED
            info.external_attr = 0o100644 << 16
            info.create_system = 3
            zf.writestr(info, raw)
    archive = buffer.getvalue()
    assert sha(archive) == ARCHIVE_SHA

    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("xb") as fh:
        fh.write(archive)
        fh.flush()
    print(f"sha256:{sha(archive)}")

if __name__ == "__main__":
    main()
