"""Prospective Phase 2 telemetry recorder and auditor; never executes or authorizes games.

This component accepts observations from a future qualified engine adapter. It verifies
their structure, ordering and provenance, not that an observation is a true account of
engine behavior. Exact-deck adapter qualification remains a separate required gate.
"""
from __future__ import annotations

from copy import deepcopy
import hashlib
import json
import os
from pathlib import Path
import re


SCHEMA = "manual-transmission-phase2-observation-ledger-v1"
PROTOCOL = "MT_V07_PHASE2_CEDH_MULTIPLAYER_R1_2026_09_24"
HARDWARE_BYTES = "2a9f47d2edcfbdb54a9ea15eadc719f85b36f74da9eb682233b85bd56f034da1"
GEARS = ("Cruise", "Sport", "Race")
PODS = ("P01", "P02", "P03")
REQUIRED_METRICS = (
    "winner_or_rules_draw", "elimination_order", "turn_and_personal_round", "seat",
    "mulligans", "commander_casts_by_identity", "animar_removals",
    "successful_and_failed_win_attempts", "opposing_win_attempts_faced_and_stopped",
    "interaction_used", "interaction_held_but_unusable", "mana_stranded",
    "cards_stranded_by_color", "tutor_lines", "resource_conversion",
    "recovery_after_interaction", "recovery_after_commander_removal", "gear_decisions",
    "no_meaningful_decision_window", "loss_reason_with_evidence",
    "ordered_accepted_actions", "engine_pilot_and_input_hashes",
)
COUNTERS = {
    "mulligans": "MULLIGAN_TAKEN",
    "commander_casts_by_identity": "COMMANDER_CAST",
    "animar_removals": "ANIMAR_REMOVED",
}
COLLECTIONS = {"elimination_order", "ordered_accepted_actions"}
COMPLETE_COLLECTORS = frozenset(COUNTERS) | COLLECTIONS
MANDATORY_COLLECTORS = frozenset(COLLECTIONS)
DERIVED = frozenset(COUNTERS) | {
    "winner_or_rules_draw", "elimination_order", "turn_and_personal_round", "seat",
    "ordered_accepted_actions", "engine_pilot_and_input_hashes",
}
EVENT_TYPES = {
    "WINDOW", "ACTION_ACCEPTED", "MULLIGAN_TAKEN", "COMMANDER_CAST", "ANIMAR_REMOVED",
    "PLAYER_ELIMINATED", "GAME_WON", "RULES_DRAW", "RESOURCE_CAP", "TIMEOUT",
    "INTEGRITY_FAILURE", "OBSERVATION",
}
END_TYPES = {"GAME_WON", "RULES_DRAW", "RESOURCE_CAP", "TIMEOUT", "INTEGRITY_FAILURE"}
STATUS = {
    "GAME_WON": "WIN", "RULES_DRAW": "RULES_DRAW", "RESOURCE_CAP": "UNRESOLVED",
    "TIMEOUT": "UNRESOLVED", "INTEGRITY_FAILURE": "INVALID",
}
DEFINITIONS = {
    "winner_or_rules_draw": "Engine terminal event only; caps/timeouts are unresolved and integrity failure is invalid. No synthetic winner.",
    "elimination_order": "Ordered unique PLAYER_ELIMINATED events; no inferred elimination from life totals or cap status.",
    "turn_and_personal_round": "Last observed global turn and four per-player turn counts from the engine adapter; no conversion by division by four.",
    "seat": "Manual seat and ordered four-player roster bound in the input manifest.",
    "mulligans": "Mulligan decisions actually taken per player, including free mulligans; zero only with a complete qualified collector.",
    "commander_casts_by_identity": "Accepted commander cast observations grouped by player and exact identity; paired commanders stay distinct.",
    "animar_removals": "Observed battlefield departures of Manual's Animar, irrespective of destination; not attempts countered before entry.",
    "successful_and_failed_win_attempts": "Explicitly identified Manual win lines with start, resolution, outcome and action references; an unproved line remains unresolved.",
    "opposing_win_attempts_faced_and_stopped": "Observed opposing win lines, legal response windows and resolution evidence; no hidden-hand or eventual-outcome inference.",
    "interaction_used": "Accepted interactive actions identified by the qualified adapter, with actor, target and resolution references; casting is not proof of success.",
    "interaction_held_but_unusable": "Own-hand interaction evaluated at an observed decision window with the legal-action set and a recorded prohibition or payment reason.",
    "mana_stranded": "Observed unspent mana at a defined decision-window boundary; preserve color/restrictions and avoid treating held mana as an error by default.",
    "cards_stranded_by_color": "Own-hand cards with otherwise legal lines blocked by colored payment at the specified window; cite the payment check.",
    "tutor_lines": "Actual search choices and destinations from accepted actions/reveals; no assumption about unobserved cards.",
    "resource_conversion": "Observed resources paid and objects/cards/mana produced by a linked action sequence; no invented counterfactual yield.",
    "recovery_after_interaction": "Observed qualifying progress after a documented interaction event, with elapsed personal turns; unresolved follow-up is censored, not zero recovery time.",
    "recovery_after_commander_removal": "Observed qualifying progress after a linked Animar departure, with elapsed personal turns and recasts; caps censor follow-up.",
    "gear_decisions": "Actual Cruise/Sport/Race branch selected, observable inputs, legal alternatives and selected action, bound to the fixed gear definition.",
    "no_meaningful_decision_window": "Requires evidence covering all relevant response windows and legal actions; not inferred from a fast loss alone.",
    "loss_reason_with_evidence": "Audited explanation linked to observed events; distinguish engine defects, pilot limitations and possible hardware constraints. Unavailable without supporting observations.",
    "ordered_accepted_actions": "Every accepted action in engine order, preserving actor, action payload and engine state/trace reference; rejected attempts are not accepted actions.",
    "engine_pilot_and_input_hashes": "Immutable engine Git SHA, four pilot digests, four deck-byte digests, effective rules digest and sealed input-manifest digest.",
}


class ContractError(ValueError):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ContractError(message)


def _json(value) -> bytes:
    try:
        return (json.dumps(value, sort_keys=True, separators=(",", ":"), allow_nan=False) + "\n").encode()
    except (TypeError, ValueError) as error:
        raise ContractError("Noncanonical JSON observation") from error


def _digest(value) -> str:
    return hashlib.sha256(_json(value)).hexdigest()


def _hash(value, size=64) -> None:
    require(isinstance(value, str) and re.fullmatch(r"[0-9a-f]{%d}" % size, value) is not None,
            "Missing or invalid immutable digest")


def _text(value) -> None:
    require(isinstance(value, str) and bool(value.strip()), "Missing observation text")


def _natural(value) -> None:
    require(type(value) is int and value >= 0, "Expected a nonnegative integer, not a Boolean")


def validate_bindings(bindings: dict) -> None:
    require(set(bindings) == {"protocol_id", "allocation_id", "pod_id", "gear", "manual_seat",
        "player_ids", "deck_sha256_by_seat", "pilot_sha256_by_seat", "engine_sha",
        "rules_sha256", "input_manifest_sha256", "gear_definition_sha256"}, "Binding fields mismatch")
    require(bindings["protocol_id"] == PROTOCOL, "Wrong Phase 2 protocol")
    _text(bindings["allocation_id"])
    require(bindings["pod_id"] in PODS and bindings["gear"] in GEARS, "Unknown pod or gear")
    require(type(bindings["manual_seat"]) is int and bindings["manual_seat"] in range(4), "Invalid Manual seat")
    players = bindings["player_ids"]
    require(isinstance(players, list) and len(players) == 4 and len(set(players)) == 4, "Four distinct ordered players required")
    for player in players:
        _text(player)
    for field in ("deck_sha256_by_seat", "pilot_sha256_by_seat"):
        require(isinstance(bindings[field], list) and len(bindings[field]) == 4, "Four seat-bound digests required")
        for value in bindings[field]:
            _hash(value)
    require(bindings["deck_sha256_by_seat"][bindings["manual_seat"]] == HARDWARE_BYTES,
            "v0.7 exact plaintext bytes differ")
    _hash(bindings["engine_sha"], 40)
    for field in ("rules_sha256", "input_manifest_sha256", "gear_definition_sha256"):
        _hash(bindings[field])


def validate_event(kind: str, data: dict, bindings: dict) -> None:
    require(kind in EVENT_TYPES and isinstance(data, dict), "Unknown event or malformed payload")
    _json(data)
    players = bindings["player_ids"]
    if kind in {"ACTION_ACCEPTED", "MULLIGAN_TAKEN", "COMMANDER_CAST", "ANIMAR_REMOVED", "PLAYER_ELIMINATED"}:
        require(data.get("player_id") in players, "Event actor absent from admitted roster")
    if kind == "WINDOW":
        _natural(data.get("turn"))
        rounds = data.get("personal_rounds")
        require(isinstance(rounds, dict) and set(rounds) == set(players), "All four personal round counters required")
        for count in rounds.values():
            _natural(count)
    elif kind == "ACTION_ACCEPTED":
        require(isinstance(data.get("action"), dict) and bool(data["action"]), "Actual accepted action payload required")
        _text(data.get("engine_trace_ref"))
    elif kind == "MULLIGAN_TAKEN":
        require(type(data.get("free")) is bool, "Free-mulligan flag required")
    elif kind == "COMMANDER_CAST":
        _text(data.get("commander_identity"))
    elif kind == "ANIMAR_REMOVED":
        require(data["player_id"] == players[bindings["manual_seat"]], "Animar removal assigned to wrong player")
        require(data.get("commander_identity") == "Animar, Soul of Elements", "Wrong commander removal identity")
        _text(data.get("destination"))
    elif kind == "GAME_WON":
        require(data.get("winner") in players, "Terminal winner absent from roster")
        _text(data.get("engine_trace_ref"))
    elif kind == "RULES_DRAW":
        require(data.get("winner") is None, "Rules draw cannot have a winner")
        _text(data.get("rule_basis"))
        _text(data.get("engine_trace_ref"))
    elif kind in {"RESOURCE_CAP", "TIMEOUT", "INTEGRITY_FAILURE"}:
        require(data.get("winner") is None and data.get("rules_draw") in (None, False), "Cap or defect cannot invent a winner/draw")
        _text(data.get("reason"))
    elif kind == "PLAYER_ELIMINATED":
        _text(data.get("reason"))


def _measurement(value, refs: list[str], reason=None) -> dict:
    return {"value": value, "evidence_refs": refs, "unavailable_reason": reason}


class Recorder:
    """Append-only hash-linked observation ledger for one exact seat/gear allocation.

    `complete_collectors` is an explicit adapter claim, separately qualified at integration.
    Its absence produces null, even when no counter events were observed. A recorder does
    not inspect hidden game state, infer legal actions, execute games or issue permission.
    """
    def __init__(self, bindings: dict, complete_collectors=()):
        validate_bindings(bindings)
        self.bindings = deepcopy(bindings)
        self.complete_collectors = frozenset(complete_collectors)
        require(self.complete_collectors <= COMPLETE_COLLECTORS, "Unknown complete collector")
        self.events: list[dict] = []
        self.measurements: dict[str, dict] = {}
        self._finished = False

    def append(self, kind: str, data: dict) -> str:
        require(not self._finished and not (self.events and self.events[-1]["kind"] in END_TYPES),
                "No observations may follow a terminal/cap/integrity event")
        validate_event(kind, data, self.bindings)
        eliminated = {e["data"]["player_id"] for e in self.events if e["kind"] == "PLAYER_ELIMINATED"}
        if kind in {"ACTION_ACCEPTED", "MULLIGAN_TAKEN", "COMMANDER_CAST"}:
            require(data["player_id"] not in eliminated, "Eliminated player cannot act or cast a commander")
        if kind == "GAME_WON":
            require(data["winner"] not in eliminated, "Eliminated player cannot win the game")
        if kind == "PLAYER_ELIMINATED":
            require(not any(e["kind"] == kind and e["data"]["player_id"] == data["player_id"] for e in self.events),
                    "Player eliminated twice")
        if kind == "WINDOW":
            previous = next((e["data"] for e in reversed(self.events) if e["kind"] == kind), None)
            require(previous is None or (data["turn"] >= previous["turn"] and all(
                data["personal_rounds"][p] >= previous["personal_rounds"][p] for p in self.bindings["player_ids"])),
                "Engine clock moved backwards")
        entry = {"sequence": len(self.events) + 1, "event_id": f"e{len(self.events) + 1:08d}",
                 "kind": kind, "data": deepcopy(data),
                 "previous_sha256": self.events[-1]["sha256"] if self.events else _digest(self.bindings)}
        entry["sha256"] = _digest(entry)
        self.events.append(entry)
        return entry["event_id"]

    def observe_metric(self, name: str, value, evidence_refs: list[str]) -> None:
        require(not self._finished, "Finished record is immutable")
        require(name in REQUIRED_METRICS and name not in DERIVED, "Unknown or engine-derived metric")
        # A descriptive definition and an arbitrary JSON value are not executable
        # operationalization. These metrics need typed exact-engine collectors and
        # qualified evidence-kind checks before they may be marked as measured.
        raise ContractError("No typed collector qualified for this metric; record null with a reason")

    def mark_unavailable(self, name: str, reason: str) -> None:
        require(not self._finished, "Finished record is immutable")
        require(name in REQUIRED_METRICS and name not in DERIVED, "Unknown or engine-derived metric")
        _text(reason)
        self.measurements[name] = _measurement(None, [], reason)

    def finish(self) -> dict:
        require(not self._finished, "Record already sealed")
        require(self.events and self.events[-1]["kind"] in END_TYPES, "Observed terminal/cap/integrity event required")
        windows = [e for e in self.events if e["kind"] == "WINDOW"]
        require(bool(windows), "Missing mandatory engine turn/personal-round observation")
        last = self.events[-1]
        players = self.bindings["player_ids"]
        loss = self.measurements.get("loss_reason_with_evidence", {}).get("value")
        require(loss is None or (last["kind"] == "GAME_WON" and
                last["data"]["winner"] != players[self.bindings["manual_seat"]]),
                "A draw, cap, defect or Manual win cannot be labeled a Manual loss")
        metrics = {name: deepcopy(self.measurements.get(name, _measurement(None, [], "COLLECTOR_NOT_QUALIFIED_OR_NOT_AVAILABLE")))
                   for name in REQUIRED_METRICS}
        metrics["winner_or_rules_draw"] = _measurement({"status": STATUS[last["kind"]],
            "winner": last["data"].get("winner") if last["kind"] == "GAME_WON" else None,
            "rules_draw": last["kind"] == "RULES_DRAW", "reason": last["data"].get("reason")}, [last["event_id"]])
        eliminated = [e for e in self.events if e["kind"] == "PLAYER_ELIMINATED"]
        if "elimination_order" in self.complete_collectors:
            metrics["elimination_order"] = _measurement([e["data"]["player_id"] for e in eliminated],
                ["collector:elimination_order"] + [e["event_id"] for e in eliminated])
        metrics["turn_and_personal_round"] = _measurement(windows[-1]["data"], [windows[-1]["event_id"]])
        metrics["seat"] = _measurement({"manual_seat": self.bindings["manual_seat"], "player_ids": players}, ["bindings"])
        metrics["engine_pilot_and_input_hashes"] = _measurement(deepcopy(self.bindings), ["bindings"])
        accepted = [e for e in self.events if e["kind"] == "ACTION_ACCEPTED"]
        if "ordered_accepted_actions" in self.complete_collectors:
            metrics["ordered_accepted_actions"] = _measurement([e["data"] for e in accepted],
                ["collector:ordered_accepted_actions"] + [e["event_id"] for e in accepted])
        for name, kind in COUNTERS.items():
            if name not in self.complete_collectors:
                continue
            events = [e for e in self.events if e["kind"] == kind]
            if name == "mulligans":
                value = {p: sum(e["data"]["player_id"] == p for e in events) for p in players}
            elif name == "commander_casts_by_identity":
                value = {p: {} for p in players}
                for e in events:
                    counts = value[e["data"]["player_id"]]
                    identity = e["data"]["commander_identity"]
                    counts[identity] = counts.get(identity, 0) + 1
            else:
                value = len(events)
            metrics[name] = _measurement(value, ["collector:" + name] + [e["event_id"] for e in events])
        record = {"schema": SCHEMA, "bindings": deepcopy(self.bindings),
            "complete_collectors": sorted(self.complete_collectors), "events": deepcopy(self.events),
            "metrics": metrics, "ledger_head_sha256": last["sha256"],
            "execution_authorized_by_this_component": False, "exact_deck_adapter_qualified_by_this_component": False}
        record["record_sha256"] = _digest(record)
        self._finished = True
        return record


def audit_record(record: dict) -> dict:
    """Rebuild all derived telemetry; reject edits, omissions, reordering and false outcomes."""
    expected_fields = {"schema", "bindings", "complete_collectors", "events", "metrics", "ledger_head_sha256",
                       "execution_authorized_by_this_component", "exact_deck_adapter_qualified_by_this_component", "record_sha256"}
    require(isinstance(record, dict) and set(record) == expected_fields and record["schema"] == SCHEMA, "Artifact schema mismatch")
    body = {k: v for k, v in record.items() if k != "record_sha256"}
    require(_digest(body) == record["record_sha256"], "Record byte digest mismatch")
    require(record["execution_authorized_by_this_component"] is False and record["exact_deck_adapter_qualified_by_this_component"] is False,
            "Telemetry component cannot self-authorize or qualify an adapter")
    require(set(record["metrics"]) == set(REQUIRED_METRICS), "Required telemetry fields missing or added")
    replay = Recorder(record["bindings"], record["complete_collectors"])
    for entry in record["events"]:
        replay.append(entry["kind"], entry["data"])
        require(replay.events[-1] == entry, "Ledger order, linkage or payload mismatch")
    for name in set(REQUIRED_METRICS) - DERIVED:
        measure = record["metrics"][name]
        require(isinstance(measure, dict) and set(measure) == {"value", "evidence_refs", "unavailable_reason"}, "Measurement schema mismatch")
        if measure["value"] is None:
            require(measure["evidence_refs"] == [], "Unavailable observation cannot claim measured evidence")
            replay.mark_unavailable(name, measure["unavailable_reason"])
        else:
            require(measure["unavailable_reason"] is None, "Measured value cannot carry an unavailable reason")
            replay.observe_metric(name, measure["value"], measure["evidence_refs"])
    require(replay.finish() == record, "Derived metrics, outcome or final ledger binding changed")
    return {"status": "TELEMETRY_CONTRACT_VALID_ADAPTER_QUALIFICATION_STILL_REQUIRED",
            "record_sha256": record["record_sha256"], "events": len(record["events"]),
            "outcome_status": record["metrics"]["winner_or_rules_draw"]["value"]["status"],
            "missing_mandatory_integrity": sorted(MANDATORY_COLLECTORS - replay.complete_collectors),
            "execution_allowed": False}


def write_record_new(path: Path, record: dict) -> None:
    """No-clobber export of an audited record; an attempt journal is still separate."""
    # Detach before auditing: a concurrent caller mutation after the audit must
    # never substitute different bytes into the create-only evidence export.
    raw = _json(record)
    audit_record(json.loads(raw))
    with path.open("xb") as stream:
        stream.write(raw)
        stream.flush()
        os.fsync(stream.fileno())
