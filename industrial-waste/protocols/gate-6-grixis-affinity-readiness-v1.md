# Gate 6 Grixis Affinity readiness v1

Status: exact 75 and admission criteria frozen; no seed namespace or gameplay run authorized.

## Question

What is the lowest-cost remaining opponent that adds a materially different competitive axis after
Boros sampling closes, and can Argentum represent its actual plan before gameplay credits are spent?

## Selection and provenance

The comparison retains the published Top 16 from the 99-player 43rd Super Ingenio in Barcelona on
2026-09-12. Grixis Affinity, UR Control, and Mono-Black Aggro each had six absent distinct maindeck
cards at the Gate 5 audit. Grixis Affinity is selected from that tie because its missing cards map
more narrowly to existing engine primitives, it finished in the Top 8, and it adds artifact density,
affinity deployment, sacrifice value, discard, reach, and graveyard interaction. Those axes test
Industrial Waste's resilience and fair-game plan more directly than another pure speed matchup.

Carlos Dc's exact published 60/15 is frozen in
`gauntlet/grixis-affinity-carlos-dc-2026-09-12.dck` from:

- https://www.mtgtop8.com/event?d=889936&e=90850&f=PAU
- Event index and field size: https://www.mtgtop8.com/event?e=90850&f=PAU

Selection does not admit the opponent to gameplay and is not evidence about either Industrial list.

## Implementation audit

The six absent distinct maindeck cards are Refurbished Familiar, Utrom Monitor, Galvanic Blast,
Reckoner's Bargain, Scrapyard Salvo, and Nihil Spellbomb.

- Galvanic Blast, Reckoner's Bargain, and Scrapyard Salvo map to existing conditional damage,
  sacrifice, life-gain, draw, and artifact-count primitives.
- Utrom Monitor requires artifact-affinity cost reduction plus flying. The initial audit incorrectly
  attributed Cycling to the card; the frozen TMC #113 oracle text has no Cycling ability.
- Refurbished Familiar requires artifact-affinity, flying, and an opponent discard trigger that
  draws once for each opponent who cannot discard.
- Nihil Spellbomb requires targeted graveyard exile and the black-payment draw trigger.

The audit must verify rules fidelity rather than substitute approximate effects. In particular,
affinity must reduce only generic mana, Scrapyard Salvo must count artifacts in the controller's
graveyard at resolution, and Nihil Spellbomb's card draw must depend on the separate black payment.

### Capability evidence

- Batch 1 (Galvanic Blast, Reckoner's Bargain, Scrapyard Salvo) passed the complete card-capability
  workflow on 2026-09-21: GitHub Actions run
  [35564718118](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/35564718118).
- The run passed frozen-input validation, committed snapshots, focused scenarios, canonical
  printing checks, Assay differential checks, and the final clean-tree requirement.
- This is implementation evidence only. It reserves no seed and authorizes no gameplay while
  admission items 1, 3, and 4 remain open.

## Admission gate

No seed namespace may be reserved and no matchup execution may occur until all of the following pass:

1. The exact sourced maindeck loads as 60 cards with no placeholders.
2. The six missing cards pass snapshots, printing checks, focused rules scenarios, and applicable
   full-suite regression gates.
3. Deterministic opponent-policy fixtures demonstrate artifact-land sequencing, affinity casting,
   profitable Bargain/Shaman sacrifices, relevant Spellbomb use, and lethal-reach recognition.
4. A seed-free deterministic exact-deck smoke progresses legally with zero exceptions or rejected
   actions.

Only after all four items qualify may a minimal fresh, disjoint paired capability pilot be specified.
That later pilot must be non-promotional and separately predeclare pressure, capability, and
Control-versus-Pactdoll decision rules. The exact v1.0 Control remains frozen; Pactdoll-A remains
unpromoted; postboard work remains blocked.
