#!/usr/bin/env python3
"""Fail-closed metadata guard for the Mono-Blue Terror production freeze."""

from __future__ import annotations

import hashlib
import json
import os

EXPECTED_ACK = "GENERATE_TIER_ONE_MONO_BLUE_TERROR_SMOKE_4_NO_GAMEPLAY"


def main() -> None:
    ack = os.environ.get("FREEZE_ACK", "")
    ref = os.environ.get("RUN_REF", "")
    attempt = os.environ.get("RUN_ATTEMPT", "")
    checks = {
        "main_ref": ref == "refs/heads/main",
        "attempt_one": attempt == "1",
        "ack_exact": ack == EXPECTED_ACK,
    }
    print(json.dumps({
        "checks": checks,
        "ref": ref,
        "run_attempt": attempt,
        "ack_length": len(ack),
        "ack_sha256": hashlib.sha256(ack.encode()).hexdigest(),
        "expected_ack_length": len(EXPECTED_ACK),
        "expected_ack_sha256": hashlib.sha256(EXPECTED_ACK.encode()).hexdigest(),
    }, sort_keys=True))
    if not all(checks.values()):
        raise SystemExit("dispatch metadata guard failed")


if __name__ == "__main__":
    main()
