#!/usr/bin/env python3
"""Phase-28 seed-free integration of frozen surfaces -> compiler -> behavior."""
from __future__ import annotations
from dataclasses import dataclass
import json
from pathlib import Path
from typing import Tuple

from public_action_compiler import (
    PublicState, PublicPermanent, ObservedActionSpec, CompiledAction,
    compile_public_actions,
)
from observed_action_behavior import (
    PublicBehaviorContext, BehaviorDecision, choose_observed_action,
)

@dataclass(frozen=True)
class IntegrationResult:
    compiled: Tuple[CompiledAction, ...]
    decision: BehaviorDecision

def load_phase25_surface_map(path: str | Path) -> dict:
    data=json.loads(Path(path).read_text())
    if data.get("schema")!="izzet-v09-phase25-action-surface-map-v1":
        raise ValueError("unexpected Phase-25 surface schema")
    return data

def _validate_source_surface(surface_map: dict, spec: ObservedActionSpec) -> None:
    surfaces=surface_map.get("surfaces",{})
    cards=surfaces.get(spec.surface)
    if not isinstance(cards,list) or spec.source_name not in cards:
        raise ValueError("observed source/surface pair is not frozen in Phase 25")

def integrate_public_fixture(
    surface_map: dict,
    state: PublicState,
    observed_specs: Tuple[ObservedActionSpec,...],
    behavior_context: PublicBehaviorContext,
) -> IntegrationResult:
    seen=set()
    for spec in observed_specs:
        if spec.action_id in seen:
            raise ValueError("duplicate observed action id")
        seen.add(spec.action_id)
        _validate_source_surface(surface_map,spec)
    compiled=compile_public_actions(state,observed_specs)
    decision=choose_observed_action(compiled,behavior_context)
    return IntegrationResult(compiled,decision)
