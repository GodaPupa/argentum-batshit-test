# Elves preboard replacement V3 — gameplay-quality audit

Date: 2026-09-21
Workflow run: `35608867466`

## Automated integrity

PASS.

- 32/32 frozen assignments executed exactly once.
- 0 flagged games.
- 0 internal AI timeout flags.
- 0 outer process timeouts.
- 0 Undercity Arena target failures.
- 29 successful Arena target resolutions observed.
- Elves capability telemetry present: 40 Quirion Ranger casts, 132 Ranger activations, 26 Winding Way casts.
- Seat balance preserved at 16/16.

## Gameplay-quality review

Representative raw traces were reviewed across both Face Value seats, wins/losses, and games exercising Undercity / Quirion Ranger / Winding Way behavior.

No material rules, targeting, terminal-bookkeeping, deck-identity, timeout, or obvious pilot-totality defect was identified in the reviewed traces.

Disposition: **ACCEPTED_PREBOARD_ELVES_REPLACEMENT_V3**

## Accepted result

Face Value: **11-21** (34.375%)

- Face Value seat 1: 5-11
- Face Value seat 2: 6-10
- Draws: 0

This result replaces the quarantined Elves component of Stage 1B for qualification purposes. It does not rehabilitate or overwrite the original Stage 1B Elves block, Replacement V1, or Replacement V2; those remain separate permanently quarantined identities.

## Consequence

The Elves preboard weakness is now supported by admissible evidence.

A fresh postboard Elves replacement block is now authorized using the frozen sideboard map and fresh non-overlapping OS-cryptographic seeds. It must use the accepted Undercity remediation and retain the same fail-closed integrity rules.

No maindeck or sideboard challenger is authorized solely from this one preboard result. Challenger development remains locked until postboard Elves evidence is accepted and the broader qualification record is synthesized.
