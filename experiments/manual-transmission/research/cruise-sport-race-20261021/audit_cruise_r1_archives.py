#!/usr/bin/env python3
"""Retrospective exhaustive R5 decision-log inventory. NO GAME EXECUTION.

This is not a preregistered blind heldout: the historical R8 evidence has
already been inspected during the design of CRUISE R1. Count ALL decision
windows in the exact historical trace, not a success-biased subset. Report
absence of an archived pass->Elf opportunity rather than claiming a win rate.
"""
import hashlib
import json
import pathlib
import sys
import zipfile

R8_SHA = "4f823fb61ac29ecc84582b4fd974200d2ba1d89664d906df802013d823f026a9"
R15_SHA = "251dc4cd30d955bbee3fef6172ca285664df78923859efa258299e5cd3fe7a5f"
R8_ARTIFACT_ID = 12228089328
R15_ARTIFACT_ID = 12239496615
SOURCE_R8 = "d226f1a56eebab1800b85a06bf70617ba439a00b"
PROFILE_R5 = "manual-transmission-r5-survival-opt-in"
PREFIX = "r8-output/r5-seed-202610191612/"


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def audit(r8_file, r15_file, output_file):
    assert sha(r8_file) == R8_SHA, "R8 original artifact integrity FAILED"
    assert sha(r15_file) == R15_SHA, "R15 original artifact integrity FAILED"
    with zipfile.ZipFile(r8_file) as z:
        assert z.read("r8-output/source-sha.txt").decode().strip() == SOURCE_R8
        summary = json.loads(z.read(PREFIX + "summary.json"))
        assert summary["profile"] == PROFILE_R5 and summary["seed"] == 202610191612
        assert summary["status"] == "COMPLETE" and summary["error"] is None
        assert summary["accepted"] == 3490 and summary["submitted"] == 3490
        decisions = [
            json.loads(line) for line in
            z.read(PREFIX + "decisions.jsonl").decode().splitlines() if line.strip()
        ]
        assert len(decisions) == 735, "Unexpected R8 decision count"
        elf_options = []
        animar_options = []
        for row_index, row in enumerate(decisions):
            options = row.get("options", [])
            llanowar = [o for o in options if o.get("cardName") == "Llanowar Elves"]
            animar = [o for o in options if o.get("cardName") == "Animar, Soul of Elements"]
            if animar:
                animar_options.append(row_index)
            for o in llanowar:
                elf_options.append({
                    "decision_index_zero_based": row_index,
                    "player": row["playerId"],
                    "global_turn": row["turnNumber"],
                    "step": row.get("step"),
                    "option_label": o.get("label"),
                    "option_chosen": o.get("chosen") is True,
                    "chosen_label": row.get("chosenLabel"),
                    "animar_option_present": bool(animar),
                    "historical_choice_replaced_by_candidate": False,
                    "reason": "No paired R5-vs-CRUISE action evaluation on this archived state",
                })
        assert len(elf_options) == 1, "All archived Llanowar opportunities must be disclosed"
        assert elf_options[0]["option_chosen"], "Unexpected R8 Llanowar choice"
    with zipfile.ZipFile(r15_file) as z:
        historic_snapshots = []
        for index in (1336, 1497, 1998):
            item = json.loads(z.read(f"r15-output/insight-before-{index}.json"))
            assert item["playerId"] == "manual-mirror-0"
            offered = [o.get("cardName") for o in item.get("options", [])]
            historic_snapshots.append({
                "action_index": index,
                "player": item["playerId"],
                "global_turn": item["turnNumber"],
                "original_choice": item.get("chosenLabel"),
                "original_elf_option_present": "Llanowar Elves" in offered,
                "snapshot_sha256": hashlib.sha256(
                    z.read(f"r15-output/state-before-{index}.json")
                ).hexdigest(),
            })
            assert "Llanowar Elves" not in offered, "Negative control was not an Elf-free window"
    result = {
        "schema": "ARGENTUM_CRUISE_R1_RETROSPECTIVE_ARCHIVE_AUDIT_V1",
        "method": "All original priority decision options, no cherry-picked sampling",
        "blinding": "NOT BLINDED, inspected previously; not an independent prospective heldout",
        "execution": "ORIGINAL_ARCHIVE_READ_ONLY_NO_REPLAY_NO_FULL_GAMES",
        "r8_artifact_id": R8_ARTIFACT_ID,
        "r8_zip_sha256": R8_SHA,
        "r8_source_sha": SOURCE_R8,
        "r8_original_profile": PROFILE_R5,
        "r8_seed": 202610191612,
        "r8_accepted_actions": 3490,
        "r8_original_decision_records": len(decisions),
        "r8_original_animar_offered_decisions": len(animar_options),
        "r8_llanowar_offered_decisions": len(elf_options),
        "r8_llanowar_observed_passes": sum(
            1 for item in elf_options if not item["option_chosen"]
        ),
        "r8_llanowar_observed_casts": sum(
            1 for item in elf_options if item["option_chosen"]
        ),
        "r8_llanowar_decision_evidence": elf_options,
        "r15_artifact_id": R15_ARTIFACT_ID,
        "r15_zip_sha256": R15_SHA,
        "r15_negative_snapshots": historic_snapshots,
        "qualifying_original_R5_pass_over_legal_Llanowar": 0,
        "cruise_policy_efficacy_in_historical_games": "INCONCLUSIVE_NO_ARCHIVED_POSITIVE_COUNTERFACTUAL",
        "synthetic_C_RAMP_01_green": True,
        "synthetic_is_not_win_rate_evidence": True,
        "full_games_consumed_in_this_audit": 0,
    }
    output_file.parent.mkdir(parents=True, exist_ok=True)
    output_file.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n")
    print(
        "CRUISE_RETROSPECTIVE_COMPLETE decisions=735 "
        "elf_offered=1 elf_chosen=1 elf_passes=0; NO FULL GAMES"
    )


if __name__ == "__main__":
    if len(sys.argv) != 4:
        raise SystemExit(
            "Usage: audit_cruise_r1_archives.py R8_original.zip R15_original.zip evidence.json"
        )
    audit(*(pathlib.Path(v) for v in sys.argv[1:]))
