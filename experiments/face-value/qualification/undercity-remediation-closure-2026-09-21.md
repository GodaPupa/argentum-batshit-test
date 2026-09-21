# Undercity targeting remediation closure

Date: 2026-09-21
Branch: `face-value/lab`

## Accepted diagnostic confirmation

Workflow run: `35571259363`

Result: **PASS**

Acceptance conditions met:

- 8/8 diagnostic games terminated.
- Undercity Arena was exercised.
- Zero Arena target-selection failures.
- Zero execution flags.
- The stale-target remediation and mandatory legal-target fallback behaved as intended.
- The diagnostic vector `741041-741048` is permanently retired and has no qualification authority.

## Scope of remediation

The remediation changes only Forge AI handling for Undercity Arena:

1. stale Arena targets are cleared before repeated AI evaluation; and
2. if strategic filtering rejects all candidates while legal targets exist, the mandatory room effect selects a legal target rather than failing.

No card rules, deck identities, opponent lists, sideboard maps, or qualification outcomes were changed.

## Qualification consequence

The previously exposed Elves preboard and postboard blocks remain quarantined permanently.

A fresh **Elves preboard replacement block** is now authorized under the existing Stage 1B design:

- 32 games
- 16 Face Value on the play
- 16 Face Value on the draw
- fresh, unique, nonzero signed 64-bit seeds
- no reuse of any historical Face Value seed
- same frozen Face Value 75
- same frozen representative Elves list
- pinned Forge plus accepted profiles and the Undercity remediation
- full raw trace preservation
- automated audit followed by gameplay-quality review

Postboard Elves remains blocked until the fresh preboard replacement block is independently accepted.

No challenger is authorized.
