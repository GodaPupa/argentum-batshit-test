#!/usr/bin/env python3
"""Freeze fresh official postboard Stage 1 assignments before outcome exposure."""

import hashlib
import json
from pathlib import Path


ROOT = Path(__file__).parent
MATCHUPS = {
    "elves": "opponents/elves-mogged-2026-09-19.dck",
    "grixis-affinity": "opponents/grixis-affinity-brunoramos93-2026-09-19.dck",
    "jund-wildfire": "opponents/jund-wildfire-blaze66-2026-09-19.dck",
    "mono-blue-terror": "opponents/mono-blue-terror-luminati-2026-09-19.dck",
    "mono-red-madness": "opponents/mono-red-madness-john-marek-2026-09-19.dck",
    "mono-red-rally": "opponents/mono-red-rally-monturulez-2026-09-20.dck",
    "monster-tron": "opponents/monster-tron-discovern-2026-09-19.dck",
}


def seed_for(index: int) -> int:
    raw = hashlib.sha256(f"face-value-postboard-stage1-map-v2|{index}".encode()).digest()
    return int.from_bytes(raw[:8], "big") & ((1 << 63) - 1) or 1


def main():
    rows, used = [], set()
    index = 1
    for replicate in range(1, 17):
        for matchup, opponent in MATCHUPS.items():
            for seat in (1, 2):
                seed = seed_for(index)
                assert seed not in used and seed not in range(710001, 730075)
                used.add(seed)
                rows.append({"run_index": index, "replicate": replicate, "matchup": matchup, "opponent_file": opponent, "face_value_seat": seat, "seed": seed})
                index += 1
    assert len(rows) == len(used) == 224
    data = {
        "classification": "OFFICIAL_QUALIFICATION_POSTBOARD_STAGE1_MAP_V2",
        "created_before_outcome_exposure": True,
        "control_sha256": "c57d727eab6299f4835e0fc42768c62ac2029c3c2789052b3ef18e249946d760",
        "postboard_map_v1_sha256": "2a3570db4a4e739044db2078e9250d6344e75c78db0ecfa1f383e60710508627",
        "postboard_map_v2_sha256": "8ef7e154d785a38cba854ca21ba1f12421a27901bf1b9f2218391b6f8c1f23de",
        "forge_profile": "FACE_VALUE_QUALIFICATION_FORGE_PROFILE_V4",
        "design": {"matchups": 7, "games_per_matchup": 32, "games_per_seat_per_matchup": 16, "total_games": 224, "full_gauntlet_aggregate_permitted": False, "outcomes_exposed_before_freeze": 0},
        "human_only_matchups": ["mono-blue-faeries"],
        "assignments": rows,
    }
    out = ROOT / "postboard-stage1-seed-registry.json"
    out.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    digest = hashlib.sha256(out.read_bytes()).hexdigest()
    (ROOT / "postboard-stage1-seed-registry.sha256").write_text(f"{digest}  postboard-stage1-seed-registry.json\n", encoding="utf-8")


if __name__ == "__main__":
    main()
