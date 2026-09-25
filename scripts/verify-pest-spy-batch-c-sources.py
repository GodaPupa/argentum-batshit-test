#!/usr/bin/env python3
"""Verify the exact compiled Hydra bytes separately from accepted prior-code provenance."""
import argparse
import hashlib
import json
from pathlib import Path

CARD_PATH = "mtg-sets/2024/src/main/kotlin/com/wingedsheep/mtg/sets/definitions/mh3/cards/NyxbornHydra.kt"
MANIFEST_PATH = "docs/experiments/pest-control/tier-one-spy-support-batch-c-sources.json"
PRIOR_SOURCE = "abfd806f6332c0da311e40b01c9d85eeb0962165"
PRIOR_DEFINITION = "c06870de76e4ea024695b524311ad995c52afa86652bf118cd15ecaa2f3ecff4"


def verify(manifest, root):
    if manifest.get("schema") != "pest-spy-support-batch-c-source-v1":
        raise ValueError("Wrong source schema")
    if manifest.get("accepted_prior_source") != PRIOR_SOURCE:
        raise ValueError("Prior source identity mismatch")
    card = manifest["target_card"]
    if card.get("name") != "Nyxborn Hydra" or card.get("source_path") != CARD_PATH:
        raise ValueError("Wrong compiled card path or identity")
    if "source_definition_sha256" in card or card.get("prior_definition_sha256") != PRIOR_DEFINITION:
        raise ValueError("Ambiguous or changed prior-definition identity")
    actual = hashlib.sha256((root / CARD_PATH).read_bytes()).hexdigest()
    if actual != card.get("compiled_definition_sha256"):
        raise ValueError("Compiled definition digest mismatch")
    return {"prior_definition_sha256": PRIOR_DEFINITION, "compiled_definition_sha256": actual,
            "scope": "SOURCE_BYTES_ONLY_NO_GAMEPLAY"}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository-root", type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    manifest = json.loads((args.repository_root / MANIFEST_PATH).read_text())
    print(json.dumps(verify(manifest, args.repository_root), sort_keys=True))


if __name__ == "__main__":
    main()
