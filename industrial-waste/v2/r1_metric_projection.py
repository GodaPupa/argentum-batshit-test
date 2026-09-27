"""Project exact typed R1 telemetry onto the four frozen decision-rule metrics.

No initialization, sampling, policy choice, candidate ranking or execution authority occurs here.
Inputs are the serialized real-engine status, original-copy event collector, derived first-per-turn
view and complete quiet-observation stream. Faults expose no Boolean decision metrics; valid caps
with no observed event are false. Completeness and checkpoint semantics require real-engine replay.
"""
from __future__ import annotations

import json
import re
from typing import Any

METRICS = (
    "loop_ready_by_t8", "conversion_by_t8", "colored_mana_failure", "total_mana_stranded_at_t4",
)
VALID_TERMINALS = {"REAL_TERMINAL", "TURN_CAP", "ACTION_CAP", "DRAW"}
INVALID_TERMINALS = {"EXCEPTION", "REJECTED_ACTION", "UNRESOLVED_TELEMETRY"}
CARD_STATUSES = {
    "EXECUTABLE", "INSUFFICIENT_TOTAL_MANA", "UNAVAILABLE_COLORED_PAYMENT", "NO_LEGAL_TARGET",
    "TIMING_OR_OTHER_LEGALITY", "UNRESOLVED_PAYMENT_SHAPE",
}


def _integer(value: Any, name: str, low: int, high: int) -> int:
    if type(value) is not int or not low <= value <= high:
        raise ValueError(f"{name} must be an integer in {low}..{high}")
    return value


def _boolean(value: Any, name: str) -> bool:
    if type(value) is not bool:
        raise ValueError(f"{name} must be a Boolean")
    return value


def _turn(value: Any, name: str) -> int | None:
    return None if value is None else _integer(value, name, 1, 8)


def _invalid(reason: str) -> dict[str, Any]:
    return {"validity": "INVALID", "invalid_reason": reason, **{key: None for key in METRICS}}


def project_metrics(
    status: dict[str, Any], events: dict[str, Any], checkpoints: list[dict[str, Any]],
    quiet_observations: list[dict[str, Any]],
) -> dict[str, Any]:
    """Reject missing/inconsistent telemetry; project only a complete admitted allocation."""
    terminal = status.get("status")
    if terminal not in VALID_TERMINALS | INVALID_TERMINALS:
        raise ValueError("allocation must have an admitted stopped execution status")
    submitted = _integer(status.get("submittedActions"), "submittedActions", 0, 4000)
    accepted = _integer(status.get("acceptedActions"), "acceptedActions", 0, submitted)
    started = _integer(status.get("ownTurnsStarted"), "ownTurnsStarted", 0, 8)
    completed = _integer(status.get("ownTurnsCompleted"), "ownTurnsCompleted", 0, started)

    if terminal in INVALID_TERMINALS:
        diagnostic = status.get("diagnostic")
        if not isinstance(diagnostic, str) or not diagnostic.strip():
            raise ValueError("fault status must preserve its diagnostic")
        return _invalid(f"{terminal}: {diagnostic}")
    if accepted != submitted:
        raise ValueError("valid evidence cannot include an unaccepted action")
    if status.get("diagnostic") is not None:
        raise ValueError("valid evidence cannot carry a fault diagnostic")
    if terminal == "TURN_CAP" and completed != 8:
        raise ValueError("TURN_CAP requires eight completed own turns")
    if terminal == "ACTION_CAP" and submitted != 4000:
        raise ValueError("ACTION_CAP requires exactly 4000 submitted actions")
    if terminal in {"REAL_TERMINAL", "DRAW"} and status.get("engineGameOver") is not True:
        raise ValueError("terminal evidence requires a real engine terminal")
    required_event_fields = {"acceptedTransitions", "demonstratedLoopReadyTurn", "demonstratedLoopCycles",
        "actualLethalTurn", "certifiedFutureConversionTurn", "deterministicConversionTurn"}
    if not required_event_fields.issubset(events):
        raise ValueError("required event projection fields are missing")
    if _integer(events["acceptedTransitions"], "acceptedTransitions", 0, 4000) != accepted:
        raise ValueError("event collector does not cover every accepted action")

    loop_turn = _turn(events.get("demonstratedLoopReadyTurn"), "demonstratedLoopReadyTurn")
    cycles = events.get("demonstratedLoopCycles")
    if not isinstance(cycles, list):
        raise ValueError("demonstrated loop cycle ledger is missing")
    cycle_turns: list[int] = []
    for cycle in cycles:
        if not isinstance(cycle, dict):
            raise ValueError("loop cycle must be an object")
        indices = [_integer(cycle.get(key), key, 1, accepted) for key in (
            "sacrificeTransition", "returnTransition", "castTransition", "entryTransition",
        )]
        if not all(left < right for left, right in zip(indices, indices[1:])):
            raise ValueError("loop proof transitions are not strictly ordered")
        cycle_turns.append(_integer(cycle.get("ownTurn"), "loop ownTurn", 1, 8))
        if not isinstance(cycle.get("sacrificedRetriever"), str) or not isinstance(cycle.get("returnedRetriever"), str):
            raise ValueError("loop proof lacks original Retriever identities")
        if cycle["sacrificedRetriever"] == cycle["returnedRetriever"]:
            raise ValueError("loop proof cannot return its own sacrificed Retriever")
        for key in ("manaBefore", "manaAfter"):
            values = cycle.get(key)
            if not isinstance(values, list) or len(values) != 6 or any(type(v) is not int or v < 0 for v in values):
                raise ValueError("loop proof lacks a complete nonnegative mana vector")
        if cycle["manaBefore"] != cycle["manaAfter"]:
            raise ValueError("loop certificate must restore its mana resources")
    if loop_turn != min(cycle_turns, default=None):
        raise ValueError("loop-ready turn is not backed by the demonstrated cycle ledger")

    lethal = _turn(events.get("actualLethalTurn"), "actualLethalTurn")
    future = _turn(events.get("certifiedFutureConversionTurn"), "certifiedFutureConversionTurn")
    conversion = _turn(events.get("deterministicConversionTurn"), "deterministicConversionTurn")
    expected_conversion = min((turn for turn in (lethal, future) if turn is not None), default=None)
    if conversion != expected_conversion:
        raise ValueError("conversion turn disagrees with the accepted collector's two proof paths")
    if lethal is not None and terminal != "REAL_TERMINAL":
        raise ValueError("actual lethal cannot appear without a real terminal status")
    if future is not None and (loop_turn is None or future < loop_turn):
        raise ValueError("future conversion requires its demonstrated neutral loop")
    if any(turn > started for turn in (loop_turn, lethal, future, conversion) if turn is not None):
        raise ValueError("metric event occurs after the allocation stopped")

    if not isinstance(checkpoints, list) or not isinstance(quiet_observations, list):
        raise ValueError("derived checkpoint view and complete quiet-observation stream must be lists")
    indexed: dict[int, dict[str, Any]] = {}
    last_action = -1
    last_turn = 0
    colored_failure = False
    for observation in quiet_observations:
        if not isinstance(observation, dict) or set(observation) != {"acceptedActions", "stateSha256", "checkpoint"}:
            raise ValueError("quiet observation must bind its action index, exact state digest and checkpoint")
        action = _integer(observation["acceptedActions"], "observation acceptedActions", 0, accepted)
        if action <= last_action:
            raise ValueError("quiet-observation action indices must be unique and strictly increasing")
        last_action = action
        digest = observation["stateSha256"]
        if not isinstance(digest, str) or re.fullmatch(r"[0-9a-f]{64}", digest) is None:
            raise ValueError("quiet observation lacks an exact serialized-state digest")
        checkpoint = observation["checkpoint"]
        if not isinstance(checkpoint, dict):
            raise ValueError("checkpoint is not an object")
        turn = _integer(checkpoint.get("ownTurn"), "checkpoint ownTurn", 1, started)
        if turn < last_turn:
            raise ValueError("quiet observations move backward in own-turn order")
        last_turn = turn
        if checkpoint.get("activatedAbilityCoverageComplete") is not True:
            raise ValueError("checkpoint omits the frozen relevant activated-ability surface")
        cards = checkpoint.get("cards")
        activations = checkpoint.get("relevantActivatedAbilities")
        graveyard_spells = checkpoint.get("relevantGraveyardSpells")
        if not all(isinstance(items, list) for items in (cards, activations, graveyard_spells)):
            raise ValueError("checkpoint needs hand-copy, relevant activation and graveyard-spell ledgers")
        if any(not isinstance(item, dict) for item in cards + activations + graveyard_spells):
            raise ValueError("checkpoint card and activation entries must be objects")
        identities: set[str] = set()
        for card in cards + graveyard_spells:
            identity = card.get("originalCopy")
            if not isinstance(identity, str) or not identity or identity in identities:
                raise ValueError("checkpoint hand/graveyard-copy identities are missing or duplicated")
            identities.add(identity)
        statuses = [item.get("status") for item in cards + activations + graveyard_spells]
        if any(value not in CARD_STATUSES for value in statuses):
            raise ValueError("checkpoint has an unknown card/activation status")
        unresolved = "UNRESOLVED_PAYMENT_SHAPE" in statuses
        if _boolean(checkpoint.get("unresolved"), "checkpoint unresolved") != unresolved:
            raise ValueError("checkpoint unresolved summary disagrees with its ledger")
        if unresolved:
            raise ValueError("unresolved payment telemetry cannot produce valid metrics")
        colored = "UNAVAILABLE_COLORED_PAYMENT" in statuses
        stranded = any(item["status"] == "INSUFFICIENT_TOTAL_MANA" for item in cards)
        if _boolean(checkpoint.get("coloredManaFailure"), "coloredManaFailure") != colored:
            raise ValueError("colored failure summary disagrees with hand and activation ledgers")
        if _boolean(checkpoint.get("totalManaStranded"), "totalManaStranded") != stranded:
            raise ValueError("hand-copy stranding summary disagrees with its ledger")
        colored_failure = colored_failure or colored
        # The accepted prospective clarification selects the first eligible T4 state only.
        # Later same-turn states remain required observations and can still cause color failure.
        indexed.setdefault(turn, checkpoint)
    if json.dumps(checkpoints, sort_keys=True, allow_nan=False) != json.dumps(
            list(indexed.values()), sort_keys=True, allow_nan=False):
        raise ValueError("legacy checkpoint view differs from the first observation of each own turn")
    if not set(range(1, completed + 1)).issubset(indexed):
        raise ValueError("a completed own turn is missing its quiet-precombat checkpoint")

    return {
        "validity": "VALID", "invalid_reason": None,
        "loop_ready_by_t8": loop_turn is not None,
        "conversion_by_t8": conversion is not None,
        "colored_mana_failure": colored_failure,
        # A valid early terminal/action cap never fabricates an unvisited T4 checkpoint.
        "total_mana_stranded_at_t4": indexed.get(4, {}).get("totalManaStranded", False),
    }
