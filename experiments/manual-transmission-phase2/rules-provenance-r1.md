# Manual Transmission Phase 2 — effective rules provenance

Protocol: `MT_V07_PHASE2_CEDH_MULTIPLAYER_R1_2026_09_24`

## Effective baseline

For Phase 2 source/admission work performed on **September 24, 2026**, the effective Magic
Comprehensive Rules release is the **August 7, 2026** rules release
(`MagicCompRules 20260807`).

Evidence:

- Wizards' rules page currently links `MagicCompRules 20260925`, whose own header states
  that it is effective **September 25, 2026**.
- The Reality Fracture update bulletin describes those changes as planned for the upcoming
  release rather than already effective.
- The immediately preceding tracked Comprehensive Rules release is August 7, 2026; the
  August rules update is also the rules release associated with the then-current Hobbit rules
  update.

Therefore the September 25 document is **future-effective** at the Phase 2 freeze date and is
not used as the experiment's effective ruleset.

## Binding rule

Before any official Phase 2 gameplay initialization, the production manifest must bind:

- effective rules release id: `MagicCompRules 20260807`;
- freeze date: `2026-09-24`;
- a content digest for the exact archived rules bytes used by the execution package;
- a statement that no September 25, 2026 Reality Fracture rule change is applied early.

If the official experiment executes on or after September 25, the ruleset must be re-evaluated
before seed freeze. That is a provenance check, not permission to change deck identities or
experimental outcomes after exposure.

Status: `EFFECTIVE_RELEASE_IDENTIFIED_BYTES_ARCHIVE_DIGEST_PENDING`.

Official gameplay counters remain zero.
