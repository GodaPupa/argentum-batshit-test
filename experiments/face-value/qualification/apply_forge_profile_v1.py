#!/usr/bin/env python3
"""Apply the versioned lab-only Forge profile used for Face Value qualification.

The profile changes opponent pilot behavior only. It does not change the frozen
Face Value 75 or any frozen representative opponent list.
"""

import argparse
from pathlib import Path


PROFILE_ID = "FACE_VALUE_QUALIFICATION_FORGE_PROFILE_V1"


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text(encoding="utf-8")
    if text.count(old) != 1:
        raise RuntimeError(f"expected exactly one source match in {path}: {old!r}")
    path.write_text(text.replace(old, new), encoding="utf-8")


def remove_ai_exclusion(path: Path) -> None:
    replace_once(path, "\nAI:RemoveDeck:All", "")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--forge-root", required=True, type=Path)
    args = parser.parse_args()
    root = args.forge_root

    for relative in (
        "forge-gui/res/cardsfolder/f/faithless_looting.txt",
        "forge-gui/res/cardsfolder/k/krark_clan_shaman.txt",
        "forge-gui/res/cardsfolder/p/prophetic_prism.txt",
        "forge-gui/res/cardsfolder/q/quirion_ranger.txt",
    ):
        remove_ai_exclusion(root / relative)

    highway = root / "forge-gui/res/cardsfolder/h/highway_robbery.txt"
    replace_once(
        highway,
        "A:SP$ GenericChoice | Choices$ DBDiscardToDraw,DBSacToDraw | SpellDescription$ You may discard a card or sacrifice a land. If you do, draw two cards.",
        "A:SP$ GenericChoice | Choices$ DBDiscardToDraw,DBSacToDraw | AILogic$ PayUnlessCost | SpellDescription$ You may discard a card or sacrifice a land. If you do, draw two cards.",
    )
    choose_generic = root / "forge-ai/src/main/java/forge/ai/ability/ChooseGenericAi.java"
    replace_once(
        choose_generic,
        '        } else if ("Always".equals(aiLogic)) {\n            return true;\n        }',
        '        } else if ("Always".equals(aiLogic) || "PayUnlessCost".equals(aiLogic)) {\n            return true;\n        }',
    )

    bargain = root / "forge-gui/res/cardsfolder/r/reckoners_bargain.txt"
    replace_once(
        bargain,
        "A:SP$ GainLife | Cost$ 1 B Sac<1/Artifact;Creature/artifact or creature> | LifeAmount$ X | SubAbility$ DBDraw | SpellDescription$ You gain life equal to the sacrificed permanent's mana value. Draw two cards.",
        "A:SP$ GainLife | Cost$ 1 B Sac<1/Artifact;Creature/artifact or creature> | LifeAmount$ X | AILogic$ FaceValueBargain | SubAbility$ DBDraw | SpellDescription$ You gain life equal to the sacrificed permanent's mana value. Draw two cards.",
    )
    life_gain = root / "forge-ai/src/main/java/forge/ai/ability/LifeGainAi.java"
    replace_once(
        life_gain,
        "        final String sourceName = ComputerUtilAbility.getAbilitySourceName(sa);\n\n        final int life = ai.getLife();",
        "        final String sourceName = ComputerUtilAbility.getAbilitySourceName(sa);\n\n        if (\"FaceValueBargain\".equals(sa.getParam(\"AILogic\"))) {\n            return new AiAbilityDecision(100, AiPlayDecision.WillPlay);\n        }\n\n        final int life = ai.getLife();",
    )

    winding = root / "forge-gui/res/cardsfolder/w/winding_way.txt"
    replace_once(
        winding,
        "A:SP$ ChooseType | Type$ Card | ValidTypes$ Creature,Land | SubAbility$ DBDig | StackDescription$ SpellDescription | SpellDescription$ Choose creature or land.",
        "A:SP$ ChooseType | Type$ Card | ValidTypes$ Creature,Land | AILogic$ FaceValueWindingWay | SubAbility$ DBDig | StackDescription$ SpellDescription | SpellDescription$ Choose creature or land.",
    )
    remove_ai_exclusion(winding)
    choose_type = root / "forge-ai/src/main/java/forge/ai/ability/ChooseTypeAi.java"
    replace_once(
        choose_type,
        "        if (aiLogic.isEmpty()) {",
        '        if ("FaceValueWindingWay".equals(aiLogic)) {\n            return new AiAbilityDecision(100, AiPlayDecision.WillPlay);\n        }\n\n        if (aiLogic.isEmpty()) {',
    )
    computer = root / "forge-ai/src/main/java/forge/ai/ComputerUtil.java"
    replace_once(
        computer,
        "            if (game.getPhaseHandler().is(PhaseType.UNTAP) && logic == null) { // Storage Matrix",
        '''            if ("FaceValueWindingWay".equals(logic)) {
                int bestCount = -1;
                for (String type : validTypes) {
                    int typeCount = CardLists.filter(ai.getCardsIn(ZoneType.Library), CardPredicates.isType(type)).size();
                    if (typeCount > bestCount) {
                        bestCount = typeCount;
                        chosen = type;
                    }
                }
            } else if (game.getPhaseHandler().is(PhaseType.UNTAP) && logic == null) { // Storage Matrix''',
    )

    print(PROFILE_ID)


if __name__ == "__main__":
    main()
