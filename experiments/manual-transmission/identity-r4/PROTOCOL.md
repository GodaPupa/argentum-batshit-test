# Manual Transmission R4 — exact original-state replay (no games)

Scope: one immutable original R22 control game for seed 202610191622. The experiment runs only the archived accepted actions, never calls GameInitializer, never draws new seed vectors, and never changes any deck or policy.

- Parent: research/manual-identity-telemetry-r2-20261020 at b63c40c2e2408d798fee1cc759a85ec8d4c4ae9b.
- Original R22 source: 10675d8dc3133294c035606f656f9e4d12d50939.
- Run: 39435937911; artifact 12271846670, ZIP SHA-256 418d9d30cef71a4d1c025d9986294f55c8be8d295555cbbaa3fc3a27c5d2ad22.
- Original hardware v0.7 remains unchanged: Git blob c350bdc86eb2d37e5da4eb712e8ee2fa729b7e89.
- Original policy: R5 survival opt-in, not Cruise/Sport/Race live policies. Keep accepted Phase 1 PASS_BOUNDED_POLICY_ELASTICITY_KEEP_V07.
- No mutation of AiProfile.CURRENT, policy modules, production engine, main, or accepted deck.

This gate SHA-verifies the original GitHub ZIP and source, checks original full and 64-action smoke states, deserializes GameState, and reprocesses ONLY the original accepted action series through 1513. It demands exact event text, per-seat life totals, status, and identical serialized state after the original 64-action prefix before reporting success. It captures reconstructed original GameState and insight files for actions 451, 826, 987, 1153, 1158, 1325, and 1514. It measures visible hands, commander tax and affordability, mana pool already floating, untapped lands and opponents' battlefield identity.

Full exact mana-production capability is not equated to floating pool or untapped land count. Engine affordability is the authoritative guard for offered Animar actions.

This first stage runs zero actual forks: fidelity must be demonstrated before considering separate, bounded cast-vs-chosen branches. It must fail closed if any event, snapshot, SHA, or stored decision does not match. A passing run is evidence of correct reconstruction, NOT evidence that a different cast was strategically superior.

R3 and prior R25 handoff NO_PROVEN_DEFECT remain in effect until a reproducible mistake, with a meaningful opponent-responsive counterfactual, is established. No hardware promotion or change to three-gear mission.
