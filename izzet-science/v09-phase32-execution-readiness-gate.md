# v0.9 Phase 32 — Official Execution Readiness Gate

Disposition: `V09_PHASE32_EXECUTION_READINESS_GATE_OPEN`

Accepted card control: `izzet-science/v0.7-control.md`
Control SHA256: `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`

Frozen opponent identity: `veteran-beastrider-commander-clash-2025-v1`

Parent acceptances:
- Phase 29 sampled-pilot design accepted.
- Phase 30 runner construction accepted.
- Phase 31 seed vector freeze accepted.
- Phase 25–28 public adversarial stack remains frozen.

Official freeze:
- vector SHA256: `5d8f9f758ed87286efb0ca07b74cec652a44304158e867f4c1aa6fc8a3bb824f`
- assignment-vector SHA256: `b2da95af015d1acd84bebd6794acad5c8a81530ccae74f41e6f925ef301e5be3`
- quarantine artifact ID: `10627816776`
- quarantined vector file SHA256: `9c9089fbe256652ffcb30363258409fe8fff9df33414216386faedb4be2d745f`
- exact positions: 12
- Izzet play/draw: 6/6

Counters entering Phase 32:
- experimental seeds generated: 12
- experimental seeds consumed: 0
- games initialized: 0
- outcome exposure: 0/12
- card changes: 0

## Authorized scope

Build and qualify execution-readiness infrastructure only:

1. an artifact loader that accepts only the exact Phase-31 quarantine artifact identity;
2. validation of quarantine artifact/file/vector/assignment digests without printing raw seeds;
3. position-to-seed-to-assignment binding for exactly positions 1–12;
4. durable attempt marker written before any game initialization;
5. one-shot consumption marker per position;
6. stop-on-first-invalid and terminal-rejection semantics;
7. crash/restart recovery that never retries an already-attempted position;
8. a synthetic or opaque test harness proving these invariants without official game initialization.

## Required fail-closed behavior

- wrong artifact ID, file hash, vector hash, or assignment hash;
- duplicate/missing positions;
- seed already consumed/attempted;
- runner/control/opponent identity mismatch;
- inability to durably persist attempt marker before initialization;
- invalid event ledger contract;
- any path that would expose raw seeds to ordinary logs.

Any such defect must block initialization.

## Explicitly unauthorized

- initializing an official game;
- consuming an official seed;
- executing matchup gameplay;
- creating outcome artifacts;
- inspecting outcomes;
- changing either deck;
- altering the frozen vector or assignment order.

## Exit criterion

Phase 32 may be accepted only after a fresh seed-free/opaque CI qualification proves
the exact-loader, durability, one-shot binding, recovery, and stop-on-first-invalid
contracts while preserving:
- seeds consumed = 0;
- games initialized = 0;
- outcome exposure = 0/12.

A later Phase 33 execution-authorization gate is required before position 1 may
initialize.
