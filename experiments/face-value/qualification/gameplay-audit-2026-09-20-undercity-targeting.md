# Face Value qualification gameplay audit — Undercity target-selection defect

Date: 2026-09-20
Branch: `face-value/lab`

## Scope

This is a gameplay-quality audit artifact only. It does not alter the frozen Face Value control, opponent lists, sideboard maps, seed registries, or preserved run artifacts.

Reviewed raw traces from:

- Preboard Stage 1B run `35537193920`
- Postboard Stage 1 run `35543054441`

The frozen protocol states that any material rules, AI, timeout, parsing, target-selection, identity, or terminal-bookkeeping defect quarantines the entire affected matchup block.

## Finding

The Elves opponent reaches the Undercity room **Arena — Goad target creature**, but Forge fails to acquire a target even when legal Face Value creatures are on the battlefield.

### Preboard Stage 1B

Game:
`151-elves-seat1-1466734944389079930.log`

Runtime failure:
`Add To Stack: Undercity (204) - [Couldn't add to stack, failed to target] - Goad target creature.`

Immediately before the failure, Face Value had surviving legal creatures, including Wretched Gryff and Nest Invader / other battlefield creatures visible in the trace. The failure occurs while the Elves player owns the initiative.

Disposition: **QUARANTINE_ELVES_PREBOARD_STAGE1B**

The complete 32-game Elves Stage 1B block is ineligible for qualification evidence. Its seeds remain permanently retired.

### Postboard Stage 1

Games include:

- `002-elves-seat2-4749411514297732591.log`
- `015-elves-seat1-5311499250263773168.log`

In game 002, Face Value had creatures including It of the Horrid Swarm / tokens and subsequently Wretched Gryff around the failed Arena room resolution.

In game 015, Face Value had Writhing Chrysalis and Eldrazi Repurposer on the battlefield around the failed Arena room resolution.

Runtime failure:
`Add To Stack: Undercity (...) - [Couldn't add to stack, failed to target] - Goad target creature.`

Disposition: **QUARANTINE_ELVES_POSTBOARD_STAGE1**

The complete 32-game Elves postboard block is ineligible for qualification evidence. Its seeds remain permanently retired.

## Other blocks

This audit does not accept or reject the other matchup blocks. They remain at their prior gameplay-review status.

The existing Mono-Red Rally postboard quarantine remains unchanged and independent.

Mono-Blue Faeries remains `HUMAN_ONLY_REQUIRED`.

## Next justified gate

Open a **zero-qualification-seed Undercity targeting remediation gate**:

1. Reproduce Arena / "Goad target creature" in deterministic diagnostic fixtures with the Elves pilot holding initiative.
2. Include fixtures where the opponent controls one, multiple, and token/non-token legal creatures.
3. Fail closed if Forge cannot choose and resolve a legal target.
4. Make only the minimum simulator/pilot-policy remediation required; do not alter card rules or deck identities.
5. Validate the remediation on non-official diagnostic seeds.
6. Require gameplay review of the diagnostic traces.
7. Only after that gate is accepted may a fresh Elves preboard qualification block be frozen.
8. Postboard Elves must wait until the remediated preboard block is independently accepted, then receive its own fresh non-overlapping vector.

No challenger may be designed from either quarantined Elves block.

## Control discipline

- Face Value Final Forge 75: unchanged.
- Deck changes: 0.
- Challenger opened: no.
- Qualification seeds rerolled/reused: no.
- Quarantined Elves outcomes may not be used for tuning or promotion.
