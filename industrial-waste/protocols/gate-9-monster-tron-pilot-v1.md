# Gate 9 Monster Tron preboard capability pilot v1

Status: frozen and reserved; execution authorized only from this exact protocol.

## Inputs

- Namespace `IW-G9-MONSTER-TRON-PILOT-V1`; exactly two fresh, nonzero, disjoint seeds.
- Frozen seed vector:
  - 4887770374418762850
  - 3072597163935011335
- Vector SHA-256 `0513dfbac2da94356299f289c8ea7de1746c3ae3dab6371e4368a996b20177a4`.
- Frozen v1.0 Control and exact Pactdoll-A versus PinoIo_Cosmico's provenance-locked Monster Tron
  maindeck from MTGO Pauper Challenge 16 #12854110 on 2026-09-15.
- Opponent deck SHA-256 `4d358e76a3566ed096299550f7400c9d63c79c2e715cd74c372faa38960428cc`.
- Every seed is played once from each seat for each Industrial identity: eight games total.
- Industrial identities participating in this gate are exactly:
  1. `industrial-waste/control/industrial-waste-v1.0-submitted.dck`
  2. `industrial-waste/challengers/pactdoll-a.dck`
- Industrial uses the same frozen Industrial Waste advisor policy used by prior capability pilots.
- Monster Tron uses `PRODUCTION_CANDIDATE_EXPIRING` plus the qualified
  `MonsterTronAdvisorModule`.
- London mulligans; preboard 60s only; 16 turns per seat; 4,000 accepted actions.

The vector was derived deterministically from the namespace before any Gate 9 matchup outcome existed
and checked against every registered Industrial Waste namespace. No outcome-dependent edits, rerolls,
replacement seeds, identity changes, or postboard actions are permitted.

## Admission evidence

Gate 9 gameplay is admitted only because all seed-free readiness requirements are green:

1. exact sourced 60/15 Monster Tron identity and frozen deck SHA-256 validated;
2. Nyxborn Hydra, Pulse of Murasa, Bonder's Ornament, and Bojuka Bog exact capability scenarios
   qualified, including Nyxborn Hydra's X-value, bestow, illegal-target fallback, and
   unattached-to-creature behavior;
3. every Monster Tron maindeck card resolves through the actual registry with no placeholders or
   substitutions;
4. six Monster-Tron-specific policy fixtures qualified, covering Crop Rotation resource/tutor
   sequencing, Expedition Map tutoring, Nyxborn Hydra threat development, Pulse stabilization,
   Bonder's Ornament mana-versus-draw decisions, and Bojuka Bog targeting;
5. deterministic exact-deck readiness smoke passed with legal progression, zero exceptions, and
   zero illegal actions;
6. qualified GitHub Actions runs on the admitted Gate 9 branch:
   - card capability: `35869695241`;
   - Monster Tron policy audit: `35869695330`;
   - exact-deck readiness: `35869695317`.

The repository-wide CI still carries unrelated hygiene failures outside the Gate 9 admission surface:
`SkipNextControllerUntapComponent` is missing a pre-existing polymorphic registration, and
`DeepAnalysis.kt` violates the existing facade-boundary check. These failures do not touch the
Gate 9 card definitions, Bestow component registration, Monster Tron policy, exact opponent identity,
or readiness smoke; the focused Gate 9 rails above are the admission authority.

## Validity and decision rule

Reject the entire pilot for any exception, illegal action, unregistered card, seed/digest mismatch,
missing seat rotation, or draw other than a declared turn/action cap. No rerolls or replacement seeds
are allowed after execution begins.

If valid, Monster Tron must win at least one game to establish opponent pressure. Industrial Waste
must win at least one game across the two identities to establish fair-game capability. Failure of
either gate closes Monster Tron sampling for diagnosis without replacement seeds.

Only a Pactdoll-A advantage of at least one win over Control may authorize one fresh,
predeclared Monster Tron replication. A tie or Control lead closes this opponent without challenger
promotion. Absolute results are descriptive at this sample size and are not pooled with Burn, Boros,
Grixis, Mono-Blue, or Jund evidence.

This pilot is capability/screening evidence only. It cannot by itself promote Pactdoll-A, alter the
frozen v1.0 Control, authorize postboard work, or justify any deck change.
