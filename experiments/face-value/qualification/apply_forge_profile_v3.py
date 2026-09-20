#!/usr/bin/env python3
"""Enable mapped postboard blast and Relic scripts on top of profiles v1/v2."""

import argparse
from pathlib import Path


PROFILE_ID = "FACE_VALUE_QUALIFICATION_FORGE_PROFILE_V3"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--forge-root", required=True, type=Path)
    root = parser.parse_args().forge_root
    paths = (
        "forge-gui/res/cardsfolder/b/blue_elemental_blast.txt",
        "forge-gui/res/cardsfolder/h/hydroblast.txt",
        "forge-gui/res/cardsfolder/p/pyroblast.txt",
        "forge-gui/res/cardsfolder/r/red_elemental_blast.txt",
        "forge-gui/res/cardsfolder/r/relic_of_progenitus.txt",
    )
    for relative in paths:
        path = root / relative
        text = path.read_text(encoding="utf-8")
        marker = "\nAI:RemoveDeck:Random"
        if text.count(marker) != 1:
            raise RuntimeError(f"expected one Random exclusion in {path}")
        path.write_text(text.replace(marker, ""), encoding="utf-8")
    print(PROFILE_ID)


if __name__ == "__main__":
    main()
