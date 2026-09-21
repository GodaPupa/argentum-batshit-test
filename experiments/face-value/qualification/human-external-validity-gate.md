# Face Value — external validity / human-play gate

Date: 2026-09-21

## Simulator program status

The simulator-eligible frozen Tier-A coverage program is complete for seven archetypes:
- accepted preboard authority exists;
- accepted postboard authority exists;
- frozen-meta weighted engineering coverage has been calculated with exact 2026-09-20 primary weights;
- Elves is the isolated accepted structural weakness signal;
- the permanent Final Forge 75 remains unchanged.

Simulator evidence cannot by protocol establish human tournament Tier 1 status.

## Remaining Tier-A archetype: Mono-Blue Faeries

Frozen opponent:
- file: `opponents/mono-blue-faeries-enz091-2026-09-19.dck`
- pilot/source: Enz091
- event: Pauper Challenge 16, 2026-09-19
- finish: 9th, 3-2
- frozen primary share: 4.14%
- protocol status: `HUMAN_ONLY_REQUIRED`

No simulator result should be invented, substituted, or imputed for this matchup.

## Human-play evidence packet

For each actual best-of-three match, record:
- date and platform/event;
- Face Value list hash / confirmation that Final Forge 75 was used unchanged;
- opponent archetype and, when available, list/source;
- play/draw for game 1;
- game results and match result;
- mulligans by game;
- Face Value sideboard ins/outs by game;
- opponent sideboarding when observable;
- concise turn/decision notes for material interactions;
- whether any game was affected by disconnect, concession unrelated to board state, misclick, rules misunderstanding, or incomplete information.

For Mono-Blue Faeries specifically, record whether Spellstutter Sprite, Ninja of the Deep Hours/Moon-Circuit Hacker, Faerie Seer, and counter/bounce interaction were materially represented. Record Face Value's Pyroblast/Relic/Safekeeping usage where applicable.

## Minimum interpretation discipline

Human matches are external-validity evidence and must remain separate from simulator games.

Do not call the deck Tier 1 from a tiny human sample. Report sample size and event context. A useful first checkpoint is 10 completed best-of-three matches against competent opposition; a broader tournament-readiness checkpoint should include multiple archetypes and preferably sanctioned/League/Challenge-style play.

No deck change is authorized merely because one or two human matches are lost.

## Next action

The research program is now waiting on human-play evidence rather than another autonomous simulator block. When match records are supplied, append them to a versioned human-results ledger and compare observed failure modes against the frozen simulator diagnosis without retroactively changing simulator evidence.
