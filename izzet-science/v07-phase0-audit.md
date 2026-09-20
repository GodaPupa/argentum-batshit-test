# v0.7 Phase 0 — Deterministic Interaction Audit

Date: 2026-09-20
Parent source: `5c7bc5adb5499be53bbf9fdd40a041100630f24f`
Frozen control: `izzet-science/v0.6-control.md`
Control SHA256: `a6acc3e0a00fa1bab0eb3ad7d981a449b8ce0e8c6bf4ed384968e46ca081fac5`

Implemented deterministic fixtures:

- first Guildmage cast and first command-zone tax increment;
- removal to the command zone and taxed redeployment boundary;
- exact five-mana Ritual launch with and without one-blue guaranteed protection;
- Seething Song launch with and without one-blue guaranteed protection;
- Goblin Electromancer launch with and without one-blue guaranteed protection;
- protection targeting coverage;
- soft permission excluded from guaranteed protection.

Verification:

- Python compilation: pass
- Full harness regression suite: pass
- 100,000-game v0.6 replay output SHA256:
  `718913aaaee4ca77435cb9f83101faebf9945e36bc144d52734bc8a6a1e16007`
- Replay digest equals the accepted v0.6 challenger-output digest recorded in
  `v06A-pieces-of-the-puzzle-accepted.md`.

The 100,000-game replay is a control-identity check only. It is not a new experiment,
is not pooled with accepted results, and produces no new performance claim.

No phase-1 pilot or challenger run was launched.

Disposition: `V07_PHASE0_PASS`
Next authorized state: `V07_PHASE1_INSTRUMENTATION_NOT_RUN`
