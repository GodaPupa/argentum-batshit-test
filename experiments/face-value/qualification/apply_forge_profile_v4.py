#!/usr/bin/env python3
"""Restrict Pyroblast/Hydroblast AI targets to objects they can affect."""

import argparse
from pathlib import Path


PROFILE_ID = "FACE_VALUE_QUALIFICATION_FORGE_PROFILE_V4"


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text(encoding="utf-8")
    if text.count(old) != 1:
        raise RuntimeError(f"expected exactly one source match in {path}: {old!r}")
    path.write_text(text.replace(old, new), encoding="utf-8")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--forge-root", required=True, type=Path)
    root = parser.parse_args().forge_root / "forge-gui/res/cardsfolder"
    replacements = {
        root / "p/pyroblast.txt": (
            (
                "SVar:DBCounter:DB$ Counter | TargetType$ Spell | TgtPrompt$ Select target spell | ValidTgts$ Card | AITgts$ Card.Blue | ConditionDefined$ Targeted | ConditionPresent$ Spell.Blue | SpellDescription$ Counter target spell if it's blue.",
                "SVar:DBCounter:DB$ Counter | TargetType$ Spell | TgtPrompt$ Select target spell | ValidTgts$ Card.Blue | SpellDescription$ Counter target spell if it's blue.",
            ),
            (
                "SVar:DBDestroy:DB$ Destroy | ValidTgts$ Permanent | AITgts$ Card.Blue | ConditionDefined$ Targeted | ConditionPresent$ Card.Blue | ConditionCompare$ GE1 | SpellDescription$ Destroy target permanent if it's blue.",
                "SVar:DBDestroy:DB$ Destroy | ValidTgts$ Permanent.Blue | SpellDescription$ Destroy target permanent if it's blue.",
            ),
        ),
        root / "h/hydroblast.txt": (
            (
                "SVar:DBCounter:DB$ Counter | TargetType$ Spell | TgtPrompt$ Select target spell | ValidTgts$ Card | AITgts$ Card.Red | ConditionDefined$ Targeted | ConditionPresent$ Spell.Red | SpellDescription$ Counter target spell if it's red.",
                "SVar:DBCounter:DB$ Counter | TargetType$ Spell | TgtPrompt$ Select target spell | ValidTgts$ Card.Red | SpellDescription$ Counter target spell if it's red.",
            ),
            (
                "SVar:DBDestroy:DB$ Destroy | ValidTgts$ Permanent | AITgts$ Card.Red | ConditionDefined$ Targeted | ConditionPresent$ Card.Red | ConditionCompare$ GE1 | SpellDescription$ Destroy target permanent if it's red.",
                "SVar:DBDestroy:DB$ Destroy | ValidTgts$ Permanent.Red | SpellDescription$ Destroy target permanent if it's red.",
            ),
        ),
    }
    for path, pairs in replacements.items():
        for old, new in pairs:
            replace_once(path, old, new)
    print(PROFILE_ID)


if __name__ == "__main__":
    main()
