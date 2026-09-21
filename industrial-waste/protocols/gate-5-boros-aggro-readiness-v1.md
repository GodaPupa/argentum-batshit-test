# Gate 5 Boros Aggro readiness v1

Status: card implementation qualified; policy/smoke qualification pending; no seeds authorized.

## Question

What is the lowest-cost contemporary opponent that adds a materially different matchup axis after
closing the Madness Burn sample, and can Argentum represent its actual plan before gameplay credits
are spent?

## Candidate set and provenance

The audit used the published maindecks from the 99-player 43rd Super Ingenio in Barcelona on
2026-09-12. Candidates were compared by exact distinct maindeck cards absent from Argentum's card
corpus; sideboards are preserved but intentionally excluded from this preboard readiness gate.

| Candidate | Finish | Missing maindeck cards | Decision |
|---|---:|---:|---|
| Boros Aggro — Marco Garrido Aboy | Top 8 | 4 | Selected |
| Grixis Affinity — Carlos Dc | Top 8 | 6 | Reserve |
| UR Control — Oriol Saez | Top 4 | 6 | Reserve |
| Mono-Black Aggro — Merlin Sales | Top 16 | 6 | Reserve |
| Mono-Blue Terror — Joan Rubies | Finalist | 7 | Reserve |
| Golgari Pestilence — Francisco Sarabia Martínez | Top 16 | 7 | Reserve |
| Reaping the Song — Pedro De La Rocha | Top 16 | 7 | Reserve |
| Balustrade Spy — Christian Casanova | Winner | 10 | Reserve |

Boros is not selected merely because its count is smallest. It adds recursive go-wide pressure,
flashback resource use, and damage prevention, all absent from the Burn gate. Its exact published
75 is frozen in `gauntlet/boros-aggro-garrido-aboy-2026-09-12.dck`.

## Implementation audit

The four absent maindeck cards are Battle Screech, Perilous Landscape, Prismatic Strands, and
Thrilling Discovery.

- Battle Screech maps to existing token creation plus Flashback with a three-white-creature tap
  additional cost.
- Perilous Landscape maps to existing land-search, tap/sacrifice costs, subtype-union filtering,
  and Cycling.
- Thrilling Discovery maps to existing life gain plus an optional discard-two / draw-three
  `IfYouDo` sequence.
- Prismatic Strands requires a rules-faithful chosen-color, source-side damage-prevention effect.
  It must not be approximated as protection, a chosen single source, or combat-only prevention.

The first three may be batched because they compose existing primitives. Prismatic Strands is an
isolated engine-vocabulary change with its own scenario coverage.

## Admission gate

No seed namespace is reserved and no matchup execution is authorized until all of the following are
true:

1. The exact 60-card maindeck loads without substitutions or placeholders.
2. Card snapshots, printing checks, and applicable engine/card tests pass.
3. Deterministic policy fixtures prove the opponent can:
   - cast and flash back Battle Screech by tapping three white creatures;
   - choose an opponent-relevant color for Prismatic Strands and use its graveyard line;
   - sacrifice Perilous Landscape for an appropriate basic land; and
   - take the Thrilling Discovery discard/draw branch when useful.
4. A seed-free deterministic smoke game demonstrates legal action progression with zero rejected
   actions or exceptions.

Only then may a tiny paired preboard capability pilot receive a fresh, disjoint namespace. Control
remains frozen and Pactdoll-A remains unpromoted throughout this readiness work.

## Implementation evidence

GitHub Actions run 35555220238 passed frozen-input validation, card snapshot generation, the focused
Battle Screech and Prismatic Strands scenarios, the targeted card build, and the repository-wide
`just test` gate. An independent engine-smoke run (35555220178) also passed. The card-capability job
then stopped at printing validation because Battle Screech's MH1 reprint record was absent; this is
a metadata-only failure after all executable behavior passed. The exact MH1 #4 printing has been
added. Follow-up run 35556984302 then passed frozen-input validation, reproducible committed
snapshots, the focused card build/scenarios, all eight canonical printing checks, both new JUD/STX
Assay differentials, and the clean-worktree snapshot gate. Card implementation is therefore
qualified. Admission items 3–4 (Boros policy fixtures and seed-free matchup smoke) remain closed.
No seeds were used.
