"""Bind typed Argentum adapter output to the accepted Phase 2 observation contract.

The Kotlin adapter independently re-executes its transcript. This importer validates the
wire/ledger boundary, not game semantics or source admission. Raw engine traces are
private evidence; neither this module nor the Kotlin adapter supplies a pilot view.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

from metrics_contract import (
    COMPLETE_COLLECTORS, Recorder, _hash, _json, require,
    validate_bindings, write_record_new,
)

TRACE_SCHEMA = "manual-transmission-phase2-engine-telemetry-v1"
TRACE_FIELDS = {
    "schema", "engineSourceSha", "manualSeat", "playerIds", "initialState",
    "initialStateSha256", "initialObservations", "steps", "stopObservation",
    "executionAuthorizedByThisComponent",
}
STEP_FIELDS = {
    "sequence", "action", "beforeStateSha256", "afterStateSha256", "accepted",
    "error", "engineEvents", "failurePhase", "observations",
}
END_KINDS = {"GAME_WON", "RULES_DRAW", "RESOURCE_CAP", "TIMEOUT", "INTEGRITY_FAILURE"}


def import_trace(bindings: dict, trace: dict) -> dict:
    """Derive an audited record; execution and adapter admission remain independently required."""
    validate_bindings(bindings)
    # Detach the exact input before validation to prevent caller mutation between checks and export.
    trace = json.loads(_json(trace))
    require(isinstance(trace, dict) and set(trace) == TRACE_FIELDS, "Engine trace fields mismatch")
    require(trace["schema"] == TRACE_SCHEMA and trace["executionAuthorizedByThisComponent"] is False,
            "Unsupported or self-authorizing engine trace")
    require(trace["engineSourceSha"] == bindings["engine_sha"], "Wrong engine source binding")
    require(type(trace["manualSeat"]) is int and trace["manualSeat"] == bindings["manual_seat"] and
            trace["playerIds"] == bindings["player_ids"], "Wrong engine seat/roster binding")
    require(isinstance(trace["initialState"], dict) and trace["initialState"], "Missing private initial engine state")
    _hash(trace["initialStateSha256"])
    require(isinstance(trace["initialObservations"], list) and len(trace["initialObservations"]) == 1 and
            isinstance(trace["initialObservations"][0], dict) and
            trace["initialObservations"][0].get("kind") == "WINDOW", "Exact initial clock observation required")
    require(isinstance(trace["steps"], list), "Engine steps must be ordered")
    require(all(isinstance(step, dict) for step in trace["steps"]), "Malformed engine step")
    extraction_incomplete = any(step.get("failurePhase") is not None for step in trace["steps"])
    recorder = Recorder(bindings, () if extraction_incomplete else COMPLETE_COLLECTORS)
    terminal = False

    def append(observation: dict) -> None:
        nonlocal terminal
        require(not terminal, "Engine observation after terminal boundary")
        require(isinstance(observation, dict) and set(observation) == {"kind", "data"}, "Typed observation fields mismatch")
        recorder.append(observation["kind"], observation["data"])
        terminal = observation["kind"] in END_KINDS

    append(trace["initialObservations"][0])
    recorder.append("OBSERVATION", {
        "engine_adapter_schema": TRACE_SCHEMA,
        "engine_trace_sha256": hashlib.sha256(_json(trace)).hexdigest(),
        "engine_replay_required_for_admission": True,
    })
    prior_state_sha = trace["initialStateSha256"]
    for sequence, step in enumerate(trace["steps"], 1):
        require(not terminal and isinstance(step, dict) and set(step) == STEP_FIELDS, "Invalid engine step")
        require(type(step["sequence"]) is int and step["sequence"] == sequence, "Missing/reordered engine step")
        failure_phase = step["failurePhase"]
        require(failure_phase in (None, "ENGINE", "COLLECTOR"), "Unknown engine/extraction failure phase")
        require(type(step["accepted"]) is bool or (step["accepted"] is None and failure_phase == "ENGINE"),
                "Unknown acceptance allowed only when the engine threw without a result")
        require(step["beforeStateSha256"] == prior_state_sha, "Disconnected engine state chain")
        if step["afterStateSha256"] is not None:
            _hash(step["afterStateSha256"])
        else:
            require(failure_phase is not None, "Missing result state hash")
        require(isinstance(step["action"], dict) and step["action"].get("playerId") in bindings["player_ids"],
                "Missing typed engine action actor")
        require((isinstance(step["engineEvents"], list) or (step["engineEvents"] is None and failure_phase is not None)) and
                isinstance(step["observations"], list) and step["observations"] and
                all(isinstance(item, dict) for item in step["observations"]),
                "Missing engine events or typed extraction")
        accepted = [item for item in step["observations"] if item.get("kind") == "ACTION_ACCEPTED"]
        if failure_phase is not None:
            require(isinstance(step["error"], str) and step["error"].strip(), "Missing preserved exception")
            require(step["observations"][-1]["kind"] == "INTEGRITY_FAILURE" and
                    all(item["kind"] in {"ACTION_ACCEPTED", "INTEGRITY_FAILURE"} for item in step["observations"]),
                    "Exception cannot claim complete extraction or a game outcome")
            if failure_phase == "ENGINE":
                require(step["accepted"] is None and step["afterStateSha256"] is None and step["engineEvents"] is None,
                        "Engine exception cannot invent a result")
        if step["accepted"] is True:
            require((step["error"] is None or failure_phase == "COLLECTOR") and len(accepted) == 1 and
                    step["observations"][0] == accepted[0],
                    "Accepted engine step/extraction mismatch")
            data = accepted[0]["data"]
            require(data == {"player_id": step["action"]["playerId"], "action": step["action"],
                             "engine_trace_ref": f"engine-step:{sequence}"}, "Changed accepted action payload or reference")
        elif failure_phase is not None:
            require(not accepted, "Unknown or rejected acceptance cannot count as accepted")
        else:
            require(isinstance(step["error"], str) and step["error"].strip() and not accepted and not step["engineEvents"] and
                    step["beforeStateSha256"] == step["afterStateSha256"], "Rejected action changed state or counted as accepted")
            require(len(step["observations"]) == 1 and step["observations"][0]["kind"] == "INTEGRITY_FAILURE",
                    "Rejected action must retain an invalid attempt")
        for observation in step["observations"]:
            append(observation)
        prior_state_sha = step["afterStateSha256"]
    if trace["stopObservation"] is not None:
        require(trace["stopObservation"].get("kind") in {"RESOURCE_CAP", "TIMEOUT", "INTEGRITY_FAILURE"},
                "External stop cannot synthesize a winner or draw")
        append(trace["stopObservation"])
    require(terminal, "Unfinished engine trace")
    return recorder.finish()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bindings", type=Path, required=True)
    parser.add_argument("--trace", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    record = import_trace(json.loads(args.bindings.read_bytes()), json.loads(args.trace.read_bytes()))
    write_record_new(args.output, record)
    print(json.dumps({"status": "TYPED_ENGINE_TRACE_IMPORTED_ADMISSION_STILL_REQUIRED",
        "record_sha256": record["record_sha256"], "execution_allowed": False}, sort_keys=True))


if __name__ == "__main__":
    main()
