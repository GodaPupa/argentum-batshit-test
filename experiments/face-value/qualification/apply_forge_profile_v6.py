#!/usr/bin/env python3
"""Remediate mandatory Undercity Arena goad targeting in Forge AI."""

import argparse
from pathlib import Path

PROFILE_ID = "FACE_VALUE_QUALIFICATION_FORGE_PROFILE_V6_UNDERCITY_TARGETING"

OLD = """            // AI does not find a good creature to goad.
            // because if it would goad a creature it would attack AI.
            // AI might not have enough information to block it
            return new AiAbilityDecision(0, AiPlayDecision.TargetingFailed);
"""
NEW = """            // Undercity Arena is a mandatory dungeon-room effect. If the
            // strategic filter rejects every candidate, Forge must still choose
            // a legal target rather than fail the room. Prefer an opponent's
            // creature if available, then fall back to any targetable creature.
            if ("Undercity".equals(source.getName())) {
                List<Card> mandatory = CardLists.getTargetableCards(
                        game.getCardsIn(ZoneType.Battlefield), sa);
                if (!mandatory.isEmpty()) {
                    List<Card> opp = CardLists.filter(mandatory,
                            c -> c.getController().isOpponentOf(ai));
                    sa.getTargets().add(ComputerUtilCard.getBestCreatureAI(
                            opp.isEmpty() ? mandatory : opp));
                    return new AiAbilityDecision(100, AiPlayDecision.MandatoryPlay);
                }
            }

            // AI does not find a good creature to goad.
            // because if it would goad a creature it would attack AI.
            // AI might not have enough information to block it
            return new AiAbilityDecision(0, AiPlayDecision.TargetingFailed);
"""

OLD2 = """        if (!mandatory && !"Undercity".equals(sa.getHostCard().getName())) {
            return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
        }
"""
NEW2 = """        if (!mandatory) {
            return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
        }
"""

def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--forge-root", required=True, type=Path)
    root = ap.parse_args().forge_root
    path = root / "forge-ai/src/main/java/forge/ai/ability/GoadAi.java"
    text = path.read_text(encoding="utf-8")
    if text.count(OLD) != 1:
        raise RuntimeError(f"expected exactly one strategic failure block in {path}")
    text = text.replace(OLD, NEW)
    if text.count(OLD2) != 1:
        raise RuntimeError(f"expected exactly one v6 trigger fallback block in {path}")
    text = text.replace(OLD2, NEW2)
    path.write_text(text, encoding="utf-8")
    print(PROFILE_ID)

if __name__ == "__main__":
    main()
