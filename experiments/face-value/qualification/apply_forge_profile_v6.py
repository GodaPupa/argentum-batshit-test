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
            // a legal target rather than fail the room.
            if ("Undercity".equals(source.getName())) {
                List<Card> mandatory = CardLists.getTargetableCards(
                        game.getCardsIn(ZoneType.Battlefield), sa);
                if (!mandatory.isEmpty()) {
                    sa.getTargets().add(ComputerUtilCard.getBestCreatureAI(mandatory));
                    return new AiAbilityDecision(100, AiPlayDecision.MandatoryPlay);
                }
            }

            // AI does not find a good creature to goad.
            // because if it would goad a creature it would attack AI.
            // AI might not have enough information to block it
            return new AiAbilityDecision(0, AiPlayDecision.TargetingFailed);
"""

def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--forge-root", required=True, type=Path)
    root = ap.parse_args().forge_root
    path = root / "forge-ai/src/main/java/forge/ai/ability/GoadAi.java"
    text = path.read_text(encoding="utf-8")
    if text.count(OLD) != 1:
        raise RuntimeError(f"expected exactly one original strategic failure block in {path}")
    path.write_text(text.replace(OLD, NEW), encoding="utf-8")
    print(PROFILE_ID)

if __name__ == "__main__":
    main()
