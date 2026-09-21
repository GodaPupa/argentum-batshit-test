#!/usr/bin/env python3
"""Seed-free event classification and deterministic Capsize response policy.

The caller declares event severity from public game state. This module assigns no
opponent deck, event frequency, tempo value, survival probability, or win value.
"""
from __future__ import annotations

from dataclasses import dataclass
import re


EVENT_CLASSES=(
    "imminent_loss",
    "guildmage_combo_removal",
    "next_main_lock",
    "tempo_only",
)
TARGET_CLASSES=("own_guildmage","opposing_permanent","opposing_commander")
ACTIONABLE_PRIORITY={
    "imminent_loss":0,
    "guildmage_combo_removal":1,
    "next_main_lock":2,
}


@dataclass(frozen=True)
class ResponseEvent:
    event_id: str
    event_class: str
    target_class: str
    legal_target: bool = True


@dataclass(frozen=True)
class ResponseDecision:
    action: str
    selected_event_id: str | None
    target_class: str | None
    buyback: bool
    reason: str


def _validate_event(event: ResponseEvent) -> None:
    if not isinstance(event,ResponseEvent):
        raise ValueError("response events must be ResponseEvent values")
    if not isinstance(event.event_id,str) or not re.fullmatch(
            r"[A-Za-z0-9][A-Za-z0-9._-]{0,63}",event.event_id):
        raise ValueError("event_id must be stable 1-64 character ASCII identity")
    if event.event_class not in EVENT_CLASSES:
        raise ValueError("unknown event class")
    if event.target_class not in TARGET_CLASSES:
        raise ValueError("unknown target class")
    if type(event.legal_target) is not bool:
        raise ValueError("legal_target must be Boolean")
    if ((event.event_class=="guildmage_combo_removal") !=
            (event.target_class=="own_guildmage")):
        raise ValueError("Guildmage-removal class and own-Guildmage target must coincide")


def select_capsize_response(
    events,
    *,
    one_shot_ready: bool,
    buyback_ready: bool,
    guildmage_battlefield: bool,
    combo_lethal_now: bool,
) -> ResponseDecision:
    """Select at most one response with a stable severity/identity ordering.

    Buyback is preferred only inside this frozen one-response window when it is
    already affordable. No value is assigned to later recasts or unused mana.
    """
    for name,value in (
            ("one_shot_ready",one_shot_ready),("buyback_ready",buyback_ready),
            ("guildmage_battlefield",guildmage_battlefield),
            ("combo_lethal_now",combo_lethal_now)):
        if type(value) is not bool:
            raise ValueError(f"{name} must be Boolean")
    if buyback_ready and not one_shot_ready:
        raise ValueError("buyback readiness must be a subset of one-shot readiness")
    if not isinstance(events,(list,tuple)):
        raise ValueError("events must be an ordered list or tuple")
    seen=set()
    for event in events:
        _validate_event(event)
        if event.event_id in seen:
            raise ValueError("duplicate event identity")
        seen.add(event.event_id)

    if not one_shot_ready:
        return ResponseDecision("pass",None,None,False,"capsize_not_ready")

    candidates=[]
    for event in events:
        if not event.legal_target or event.event_class=="tempo_only":
            continue
        if (event.event_class=="guildmage_combo_removal" and
                not (guildmage_battlefield and combo_lethal_now)):
            continue
        candidates.append(event)
    if not candidates:
        return ResponseDecision("pass",None,None,False,"no_qualified_event")

    selected=min(
        candidates,key=lambda event:(ACTIONABLE_PRIORITY[event.event_class],
                                     event.event_id))
    buyback=buyback_ready
    return ResponseDecision(
        "cast_buyback" if buyback else "cast_normal",
        selected.event_id,selected.target_class,buyback,selected.event_class)
