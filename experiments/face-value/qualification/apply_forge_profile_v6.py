#!/usr/bin/env python3
"""Remediate mandatory Undercity Arena goad targeting in Forge AI."""

import argparse
from pathlib import Path

PROFILE_ID = "FACE_VALUE_QUALIFICATION_FORGE_PROFILE_V6_UNDERCITY_TARGETING"

OLD = """        if (!mandatory) {
            return new AiAbilityDecision(0, AiPlayDecision.CantPlayAi);
        }
"""
NEW = """        // Undercity Arena is a mandatory dungeon-room effect. In simulation,
        // Forge may invoke this trigger with mandatory=false; do not let the AI
        // decline a legal target for strategic reasons in that exact case.
        if (!mandatory && !"Undercity".equals(sa.getHostCard().getName())) {
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
        raise RuntimeError(f"expected exactly one target block in {path}")
    path.write_text(text.replace(OLD, NEW), encoding="utf-8")
    print(PROFILE_ID)

if __name__ == "__main__":
    main()
