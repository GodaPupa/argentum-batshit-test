#!/usr/bin/env python3
"""Restore exact captured Industrial AI worker bytes without launching a worker or game."""
import argparse
import os
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))
from restore_observed_runtime import RuntimeRestoreRequest, restore_observed_runtime


def main():
    if os.environ.get("IW_V2_R1_PREPARED_DIRECTORY"):
        raise ValueError("Official R1 execution environment is forbidden")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--qualification-zip", type=Path, required=True)
    parser.add_argument("--zip-sha256", required=True)
    parser.add_argument("--capture-receipt-sha256", required=True)
    parser.add_argument("--source-head", required=True)
    parser.add_argument("--repository", type=Path, required=True)
    parser.add_argument("--java", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    request = RuntimeRestoreRequest(**vars(parser.parse_args()))
    restore_observed_runtime(request,
        qualification_prefix="build/reports/industrial-waste-v2/runtime-composition/ai-worker",
        capture_directory="observed-ai-runtime",
        capture_schema="industrial-observed-ai-runtime-archive-v1", expected_module="ai",
        report_schema="industrial-observed-runtime-relocation-v1", restorer_source=Path(__file__))


if __name__ == "__main__":
    main()
