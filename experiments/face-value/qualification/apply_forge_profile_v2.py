#!/usr/bin/env python3
"""Apply the Rally sequencing delta on top of Forge profile v1."""

import argparse
from pathlib import Path


PROFILE_ID = "FACE_VALUE_QUALIFICATION_FORGE_PROFILE_V2"


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text(encoding="utf-8")
    if text.count(old) != 1:
        raise RuntimeError(f"expected exactly one source match in {path}: {old!r}")
    path.write_text(text.replace(old, new), encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--forge-root", required=True, type=Path)
    args = parser.parse_args()
    root = args.forge_root

    bushwhacker = root / "forge-gui/res/cardsfolder/g/goblin_bushwhacker.txt"
    replace_once(
        bushwhacker,
        "SVar:PlayMain1:TRUE\n",
        "SVar:PlayMain1:TRUE\nSVar:FaceValueSequenceAfter:Rally at the Hornburg\n",
    )

    permanent_ai = root / "forge-ai/src/main/java/forge/ai/ability/PermanentAi.java"
    replace_once(
        permanent_ai,
        "        // check on legendary\n",
        '''        // Lab-only sequencing guard: if both spells can be cast this turn,
        // create the Rally tokens before resolving the Bushwhacker pump.
        if (source.hasSVar("FaceValueSequenceAfter")) {
            final String precursor = source.getSVar("FaceValueSequenceAfter");
            final boolean precursorInHand = ai.getCardsIn(ZoneType.Hand)
                    .anyMatch(CardPredicates.nameEquals(precursor));
            final boolean precursorCastThisTurn = CardUtil.getThisTurnCast("Card", source, sa, ai)
                    .stream().anyMatch(CardPredicates.nameEquals(precursor));
            if (precursorInHand && !precursorCastThisTurn
                    && ComputerUtilMana.getAvailableManaSources(ai, true).size() >= 4) {
                return new AiAbilityDecision(0, AiPlayDecision.AnotherTime);
            }
        }

        // check on legendary
''',
    )

    print(PROFILE_ID)


if __name__ == "__main__":
    main()
