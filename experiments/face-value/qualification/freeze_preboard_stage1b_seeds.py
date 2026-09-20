#!/usr/bin/env python3
"""One-shot fail-closed seed freezer for seven-matchup preboard Stage 1B."""

import hashlib
import json
import re
import secrets
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
QUAL = Path(__file__).resolve().parent
OUT = QUAL / "preboard-stage1b-seed-registry.json"
MATCHUPS = [
    ("mono-red-madness", "opponents/mono-red-madness-john-marek-2026-09-19.dck"),
    ("mono-blue-terror", "opponents/mono-blue-terror-luminati-2026-09-19.dck"),
    ("grixis-affinity", "opponents/grixis-affinity-brunoramos93-2026-09-19.dck"),
    ("monster-tron", "opponents/monster-tron-discovern-2026-09-19.dck"),
    ("jund-wildfire", "opponents/jund-wildfire-blaze66-2026-09-19.dck"),
    ("elves", "opponents/elves-mogged-2026-09-19.dck"),
    ("mono-red-rally", "opponents/mono-red-rally-monturulez-2026-09-20.dck"),
]
DIAGNOSTIC_SEEDS = set(range(710001, 710009)) | set(range(720001, 720177))


def historical_integer_tokens() -> tuple[set[int], str, int]:
    paths = []
    for base in (ROOT / "experiments/face-value", ROOT / ".github/workflows"):
        for path in base.rglob("*"):
            if not path.is_file() or path == OUT:
                continue
            if base.name == "workflows" and not path.name.startswith("face-value"):
                continue
            paths.append(path)
    tokens: set[int] = set()
    corpus = hashlib.sha256()
    readable = 0
    for path in sorted(paths):
        try:
            raw = path.read_bytes()
            text = raw.decode("utf-8")
        except (OSError, UnicodeDecodeError):
            continue
        readable += 1
        rel = path.relative_to(ROOT).as_posix().encode()
        corpus.update(rel + b"\0" + hashlib.sha256(raw).digest())
        tokens.update(int(x) for x in re.findall(r"(?<![A-Za-z0-9])-?\d+", text))
    return tokens, corpus.hexdigest(), readable


def main() -> None:
    if OUT.exists():
        raise SystemExit(f"refusing to overwrite permanent registry: {OUT}")
    history, corpus_digest, files_scanned = historical_integer_tokens()
    forbidden = history | DIAGNOSTIC_SEEDS | {0}
    assignments = []
    issued: set[int] = set()
    run_index = 0
    for round_index in range(1, 17):
        for matchup, opponent_file in MATCHUPS:
            for seat in (1, 2):
                seed = 0
                while seed in forbidden or seed in issued:
                    seed = secrets.randbelow(2**63 - 1) + 1
                issued.add(seed)
                run_index += 1
                assignments.append({
                    "run_index": run_index,
                    "round": round_index,
                    "matchup": matchup,
                    "opponent_file": opponent_file,
                    "face_value_seat": seat,
                    "seed": seed,
                })
    payload = {
        "classification": "OFFICIAL_QUALIFICATION_PREBOARD_STAGE1B_HYBRID_SCOPE",
        "generated_at_utc": datetime.now(timezone.utc).replace(microsecond=0).isoformat(),
        "generator": "Python secrets.randbelow using the operating-system cryptographic source",
        "range": "positive signed 64-bit integers",
        "history_audit": {
            "scope": "all readable files under experiments/face-value plus face-value* GitHub workflows",
            "files_scanned": files_scanned,
            "distinct_integer_tokens": len(history),
            "corpus_digest_sha256": corpus_digest,
            "collision_count": 0,
        },
        "authorization": {
            "forge_commit": "f387ede550e336315637a8b765f0927ac768c113",
            "forge_profile": "FACE_VALUE_QUALIFICATION_FORGE_PROFILE_V2",
            "rally_replication_run": "https://github.com/GodaPupa/argentum-batshit-test/actions/runs/35536776074",
            "rally_replication_artifact_sha256": "d49406bd0741b9bba4c44c199ea2068ce401e3574c4ed1a6b83643f843443150",
            "faeries_channel": "HUMAN_ONLY_REQUIRED",
            "diagnostic_seeds_permanently_excluded": sorted(DIAGNOSTIC_SEEDS),
            "original_stage1_registry_ineligible": "preboard-stage1-seed-registry.json",
        },
        "design": {
            "matchups": len(MATCHUPS),
            "games_per_matchup": 32,
            "games_per_seat_per_matchup": 16,
            "total_games": len(assignments),
            "outcomes_exposed_before_freeze": 0,
            "full_gauntlet_aggregate_permitted": False,
        },
        "assignments": assignments,
    }
    OUT.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"wrote {OUT} with {len(assignments)} unique fresh assignments")


if __name__ == "__main__":
    main()
