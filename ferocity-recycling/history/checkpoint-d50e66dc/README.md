# Ferocity Recycling

Independent Pauper archetype research centered on Ferocity of the Hunt. This project asks separately which bounded configuration performs best, whether Ferocity improves its own shell, and how it compares with current established decks.

**Current evidence and execution state:** [CURRENT_STATUS.md](CURRENT_STATUS.md).

- [Active contract](protocols/ACTIVE_CONTRACT.json) and [prospective reconciliation](protocols/RECONCILIATION_AMENDMENT.md)
- [Detailed protocol](protocols/RESEARCH_PROTOCOL.md), subject to the explicit reconciliation
- [Rules and legality audit](RULES_LEGALITY_AUDIT.md)
- [Synergy/resource map](SYNERGY_MAP.md)
- [Prototype construction](DECK_DEVELOPMENT.md)
- [Current benchmark sources](BENCHMARK_AUDIT.md)
- [Build and compatibility audit](BUILD_AND_ENGINE_AUDIT.md)
- [Mechanical qualification](MECHANICAL_QUALIFICATION.md)
- [Research report](RESEARCH_REPORT.md)
- [Seed ledger](seeds/ledger.json)

Prototype lists are not a tournament recommendation or a supported final 75. The final deck/primer/sideboarding deliverable follows accepted interactive evidence. `FINAL_CONCLUSION.md` is intentionally absent while a stopping rule has not been reached.

## Reproduce the available deterministic checks

From the repository root:

```sh
python3 -m unittest discover -s ferocity-recycling/tests -v
python3 ferocity-recycling/tools/project.py validate
python3 ferocity-recycling/tools/project.py verify
```

The `screen` command checks the frozen input hashes and computes exact opening-inventory probabilities. It uses no RNG and cannot simulate a game:

```sh
python3 ferocity-recycling/tools/project.py screen --output /tmp/ferocity-opening-inventory-replay.json
```

Use a new output path; the runner refuses to overwrite existing evidence. Compare the JSON with the archived result. Do not create another freeze to make drift pass.

Real-engine qualification uses the repository's `just` recipes through `tools/validate_build.py`; its logs and XML distinguish test-source mistakes, engine failures and successful scenarios. A successful scenario suite does not authorize gameplay by itself. Follow the admission requirements in the protocol before generating a stage's trial manifest.

## Current source qualification

The Q3 checkpoint publishes the reviewed combined source for exact-head review and validation. Its local fixed selection executed 502 cases: 488 passed, two Red cases failed and 12 server cases failed during instrumentation. The source is not admitted to research gameplay. See [current status](CURRENT_STATUS.md) and the exact [CI selection](ci/common-fixed-selection-v1.json).

The project CI runs ten independent module batches; the separate [combat browser contract](runtime-audits/combat-browser/README.md) runs four fixed current-UI cases without retry. These are mechanics and interface gates, not matchup trials. Closed manifests include original failures, source/dependency maps, process artifacts and replay instructions. No randomized development or evaluation evidence exists yet.
