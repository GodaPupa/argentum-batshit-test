#!/usr/bin/env python3
"""Fail-closed validation for Industrial Waste frozen inputs."""

from __future__ import annotations

import hashlib
import re
from collections import Counter
from pathlib import Path


LINE = re.compile(r"^(\d+)\s+(.+?)\s*$")


def parse_deck(path: Path) -> tuple[Counter[str], Counter[str]]:
    sections = {"main": Counter(), "sideboard": Counter()}
    seen = {"main": set(), "sideboard": set()}
    section = None
    for number, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if line == "[main]":
            section = "main"
            continue
        if line == "[sideboard]":
            section = "sideboard"
            continue
        if line.startswith("["):
            section = None
            continue
        if section is None or "=" in line:
            continue
        match = LINE.match(line)
        if not match:
            raise ValueError(f"{path}:{number}: malformed deck line: {line}")
        count, card = int(match.group(1)), match.group(2)
        if count <= 0:
            raise ValueError(f"{path}:{number}: nonpositive count")
        if card in seen[section]:
            raise ValueError(f"{path}:{number}: duplicate card line: {card}")
        if count > 4 and card not in {"Forest", "Swamp"}:
            raise ValueError(f"{path}:{number}: more than four copies: {card}")
        seen[section].add(card)
        sections[section][card] = count
    return sections["main"], sections["sideboard"]


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def validate_project(root: Path) -> None:
    control_path = root / "control/industrial-waste-v1.0-submitted.dck"
    control, sideboard = parse_deck(control_path)
    if (sum(control.values()), sum(sideboard.values())) != (60, 15):
        raise ValueError("submitted control must remain exactly 60 main plus 15 side")

    expected_changes = {
        "turbo-a.dck": (Counter({"Crop Rotation": 2}), Counter({"Giant's Boulder": 2})),
        "pactdoll-a.dck": (Counter({"Ichor Wellspring": 2}), Counter({"Eviscerator's Insight": 2})),
        "recursive-a.dck": (
            Counter({"Blood Fountain": 1, "Haunted Fengraf": 1}),
            Counter({"Eviscerator's Insight": 1, "Giant's Boulder": 1}),
        ),
    }
    for name, (additions, removals) in expected_changes.items():
        main, side = parse_deck(root / "challengers" / name)
        if main - control != additions or control - main != removals:
            raise ValueError(f"{name}: not the declared exact swap")
        if side != sideboard:
            raise ValueError(f"{name}: sideboard drift")
        if (sum(main.values()), sum(side.values())) != (60, 15):
            raise ValueError(f"{name}: expected 60 main plus 15 side")

    identity_file = root / "input-identities.sha256"
    if identity_file.exists():
        expected = {}
        for line in identity_file.read_text(encoding="utf-8").splitlines():
            digest, relative = line.split("  ", 1)
            expected[relative] = digest
        for relative, digest in expected.items():
            if sha256(root / relative) != digest:
                raise ValueError(f"identity mismatch: {relative}")


if __name__ == "__main__":
    validate_project(Path(__file__).resolve().parents[1])
    print("Industrial Waste inputs: VALID")
