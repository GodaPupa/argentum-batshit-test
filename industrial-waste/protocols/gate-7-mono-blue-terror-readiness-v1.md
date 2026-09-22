# Gate 7 Mono-Blue Terror readiness v1

Status: exact sourced 75 frozen for seedless readiness. No Gate 7 gameplay namespace is reserved.

## Selection rationale

Industrial Waste has completed preboard capability work against Madness Burn, Boros Aggro, and Grixis Affinity. The next opponent must add a different competitive axis rather than resample those matchups.

Mono-Blue Terror is selected because it is a current top-tier Pauper archetype and tests a distinct combination of:
- dense countermagic;
- cheap large threats fueled by graveyard velocity;
- Murmuring Mystic's go-wide secondary plan;
- tempo interaction against expensive engine permanents;
- pressure on sequencing around Force Spike, Counterspell, and Dispel.

The exact opponent is Joan Rubies's 2nd-place 75 from the 99-player 43rd Super Ingenio in Barcelona on 2026-09-12.

Source:
https://metagame.info/en-us/mtg/tournaments/43-edicion-super-ingenio-ingeniobcn-barcelona-top-8-pauper-2026-09-12

Frozen deck file:
gauntlet/mono-blue-terror-joan-rubies-2026-09-12.dck

Frozen file SHA-256:
f99a01d040e8d0c5d0144db019ca53db7a07bb251c07ac6fb3118f695d3fc3d3

## Admission gate

No fresh Gate 7 seed namespace may be reserved until all of the following pass:

1. The exact sourced deck parses as 60 maindeck plus 15 sideboard cards and matches the frozen SHA-256.
2. Every maindeck card resolves through the current Argentum registry with no placeholder substitution.
3. A deterministic exact-deck smoke makes legal progress with zero engine exceptions and zero rejected actions.
4. Opponent-policy audit determines whether the production profile already handles:
   - early cantrip/self-mill sequencing;
   - casting Terror/Serpent at reduced cost;
   - holding Counterspell/Force Spike/Dispel for meaningful windows;
   - deploying Murmuring Mystic when the game calls for a secondary engine.
5. Any policy additions must be deterministic and based only on public information available at the decision point.

The readiness smoke is diagnostic only. Its fixed development seed is not registered, does not count as matchup evidence, and cannot authorize deck promotion.

Frozen Industrial Waste v1.0 Control remains unchanged. No postboard work is authorized by this readiness gate.
