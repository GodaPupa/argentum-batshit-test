#!/usr/bin/env python3
"""Enable the frozen Elves map's Monstrous Emergence script."""

import argparse
from pathlib import Path


PROFILE_ID = "FACE_VALUE_QUALIFICATION_FORGE_PROFILE_V5"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--forge-root", required=True, type=Path)
    path = parser.parse_args().forge_root / "forge-gui/res/cardsfolder/m/monstrous_emergence.txt"
    text = path.read_text(encoding="utf-8")
    marker = "\nAI:RemoveDeck:All"
    if text.count(marker) != 1:
        raise RuntimeError(f"expected one All exclusion in {path}")
    path.write_text(text.replace(marker, ""), encoding="utf-8")
    print(PROFILE_ID)


if __name__ == "__main__":
    main()
