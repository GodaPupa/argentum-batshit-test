# Face Value — coverage provenance correction

Date: 2026-09-21

This note supersedes any interpretation in `elves-challenger-series-closure-and-coverage-gate.md` that the raw postboard Stage 1 non-Elves scores are already accepted qualification evidence.

## Accepted preboard authority

The authoritative preboard evidence is Stage 1B gameplay audit:
- run 35537193920
- 224/224 terminal, 0 automated flags
- all seven simulator-eligible matchup blocks accepted
- descriptive aggregate 116-108 (51.8%)
- Mono-Blue Faeries remains HUMAN_ONLY_REQUIRED and is excluded from simulator aggregation.

Accepted Stage 1B matchup scores:
- Mono-Red Madness 15-17
- Mono-Blue Terror 25-7
- Grixis Affinity 13-19
- Monster Tron 20-12
- Jund Wildfire 19-13
- Elves 10-22 (later Elves replacement authority supersedes for current Elves preboard)
- Mono-Red Rally 14-18

## Postboard Stage 1 status

Run 35543054441 produced automated-green raw scores for six blocks and quarantined Rally, but the summary itself labels the green blocks `AUTOMATED_GREEN_PENDING_GAMEPLAY_REVIEW`.

Therefore:
- Grixis Affinity 26-6
- Jund Wildfire 24-8
- Mono-Blue Terror 25-7
- Mono-Red Madness 28-4
- Monster Tron 25-7

are **descriptive pending-review metadata**, not accepted qualification evidence yet.

The 128-32 subtotal may be used only to prioritize audit work; it must not be presented as an accepted postboard win rate or Tier-1 evidence until gameplay review closes those blocks.

Elves has separate later accepted replacement authority at 13-19.

Mono-Red Rally's Stage 1 19-13 remains quarantined. Diagnostic sequencing replication run 35536776074 demonstrated Rally/Bushwhacker sequencing capability (8/8 terminal, 6 joint turns, 0 order violations) but explicitly has `qualification_authority=false`; it does not rehabilitate or replace the quarantined 32-game Rally outcome.

## Next gate

Audit the five automated-green postboard Stage 1 blocks outcome-independently from preserved evidence. Do not rerun consumed seeds. Accept or quarantine each block separately. Then create a fresh Rally replacement qualification block only after confirming the sequencing remediation identity and freeze discipline.
