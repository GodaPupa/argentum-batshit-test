# v0.9 Phase 33 — Position 1 Execution Authorization Gate

Disposition: `V09_PHASE33_POSITION1_EXECUTION_AUTHORIZATION_GATE_OPEN`

Accepted card control: `izzet-science/v0.7-control.md`
Control SHA256: `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`

Frozen opponent identity: `veteran-beastrider-commander-clash-2025-v1`

Parents:
- Phase 29 pilot design accepted.
- Phase 30 runner construction accepted.
- Phase 31 seed vector freeze accepted.
- Phase 32 execution readiness accepted.

Official freeze:
- vector SHA256: `5d8f9f758ed87286efb0ca07b74cec652a44304158e867f4c1aa6fc8a3bb824f`
- assignment SHA256: `b2da95af015d1acd84bebd6794acad5c8a81530ccae74f41e6f925ef301e5be3`
- quarantine artifact ID: `10627816776`
- positions: 1–12
- play/draw: 6/6

## Authorization scope

Authorize **position 1 only** after this gate itself is statically qualified.

Required sequence for position 1:
1. load only the exact Phase-31 quarantine artifact through the accepted Phase-32 loader;
2. verify exact artifact/file/vector/assignment/control/runner/opponent identities;
3. durably write the position-1 attempt marker;
4. only then reveal position-1 seed to the authorized execution adapter;
5. initialize exactly one official game;
6. execute to a terminal state or fail closed on first invalidation;
7. durably record the canonical Phase-30 ledger entry and consumption marker;
8. expose outcome only through the canonical artifact;
9. do not initialize position 2 unless a later gate explicitly authorizes it.

## Fail-closed conditions

- any identity/hash mismatch;
- inability to durably persist attempt marker before initialization;
- illegal action or hidden-information leak;
- missing/invalid event ledger;
- terminal-state accounting defect;
- nondeterministic replay defect;
- seed reuse or duplicate attempt;
- any attempt to initialize position >1.

## Explicitly unauthorized

- positions 2–12;
- reroll, replacement, regeneration, or reassignment;
- deck changes;
- outcome-conditioned protocol changes;
- pooling/tuning from the single position-1 result.

## Exit criterion

Phase 33 may close only after a fresh static/opaque CI qualification proves the
position-1-only authorization boundary and there is still:
- seeds consumed = 0;
- games initialized = 0;
- outcome exposure = 0/12.

Only after that qualification may position 1 execute once.
