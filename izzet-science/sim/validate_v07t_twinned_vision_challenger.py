#!/usr/bin/env python3
"""Fail-closed identity validator for the frozen Twinned Vision challenger.

This validator is intentionally seed-free and gameplay-free.  It proves only immutable deck
identity and the preregistered one-card substitution.
"""

from __future__ import annotations

from collections import Counter
from hashlib import sha256
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
CONTROL = ROOT / "izzet-science" / "v0.7-control.md"
CHALLENGER = ROOT / "izzet-science" / "challengers" / "v0.7-T-twinned-vision.md"

CONTROL_SHA256 = "726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01"
CHALLENGER_SHA256 = "0da295e9fcf066728181323dd9acbc0c122f623c99a607709a1e8a85e8936f85"

CARD_LINE = re.compile(r"^\s*(\d+)\s+(.+?)\s*$")


def digest(path: Path) -> str:
    return sha256(path.read_bytes()).hexdigest()


def deck_counts(path: Path) -> Counter[str]:
    counts: Counter[str] = Counter()
    for raw in path.read_text(encoding="utf-8").splitlines():
        match = CARD_LINE.match(raw)
        if not match:
            continue
        count = int(match.group(1))
        name = match.group(2)
        counts[name] += count
    return counts


def main() -> None:
    assert digest(CONTROL) == CONTROL_SHA256, "frozen v0.7 control hash changed"
    assert digest(CHALLENGER) == CHALLENGER_SHA256, "frozen v0.7-T challenger hash changed"

    control = deck_counts(CONTROL)
    challenger = deck_counts(CHALLENGER)
    assert sum(control.values()) == 100, f"control count is {sum(control.values())}, expected 100"
    assert sum(challenger.values()) == 100, (
        f"challenger count is {sum(challenger.values())}, expected 100"
    )

    names = set(control) | set(challenger)
    delta = {
        name: challenger[name] - control[name]
        for name in names
        if challenger[name] != control[name]
    }
    expected = {"Strategic Planning": -1, "Twinned Vision": 1}
    assert delta == expected, f"challenger delta changed: {delta!r}"

    print("V07T_TWINNED_VISION_IDENTITY_VALIDATION_PASS")
    print(f"control_sha256={CONTROL_SHA256}")
    print(f"challenger_sha256={CHALLENGER_SHA256}")
    print("substitution=-1 Strategic Planning;+1 Twinned Vision")
    print("official_seeds_consumed=0")
    print("games_initialized=0")
    print("outcome_exposure=0")


if __name__ == "__main__":
    main()
