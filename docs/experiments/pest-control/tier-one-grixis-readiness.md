# Pest Control Tier-1 coverage — Grixis Affinity readiness

## Boundary

This change is a seedless, no-game construction gate. It does not authorize an execution runner,
generate or freeze seeds, initialize a game, expose an outcome, alter Pest Control v1.0, or modify
the accepted Mono Red Madness qualification. All prior experimental identities and dispositions
remain unchanged.

## Why Grixis is next

The metagame snapshot reviewed on 2026-09-20 placed Grixis Affinity third by share behind Mono Red
Madness and Mono-Blue Terror. Mono Red is already qualified. The current winning Mono-Blue Terror
candidate requires six unsupported maindeck identities spanning 18 slots. Grixis has an exact,
previously provenance-audited list and a completed shared card-support package, making it the least
expensive strategically distinct second matchup to validate.

This ordering is an implementation decision, not a performance claim and not a declaration that
the selected list represents every Grixis build.

## Frozen opponent identity

- Archetype: Grixis Affinity
- Pilot: Giandomenico Pasquale
- Event: `1.tappa Lega Str Autumn`, Santa Teresa di Riva, Italy; 16 players; first place
- Date: 2026-09-07
- Published entry: <https://www.mtgtop8.com/event?e=90623&d=887996&f=PAU>
- Main deck: 60
- Sideboard: 15, recorded but never instantiated by this preboard gate
- Main SHA-256: `2e20ec68c58dda1f913f99e8c97b7458f920a9be18bb4ee2c09784326cc2896c`
- Sideboard SHA-256: `19c64bb94ef1dbdd953409dede79b02a478e9cf959bb230fe0c67a9b43d3177a`
- Complete-75 SHA-256: `b73fe84ec0dd11961f45cf0ab39f155c92d99636ff1ad961bbc8f4774504bdcd`

The quantities and hashes are enforced in `PestControlTierOneGrixisReadiness`. The exact 60 is fully
resolvable. Eleven of 15 sideboard slots are supported; Mesmeric Fiend and Unexpected Fangs remain
unsupported and therefore postboard work is explicitly blocked.

## Shared Argentum change

`SHARED ARGENTUM CHANGE: yes`

The reusable package adds or completes six Grixis maindeck card identities with one scenario file
per card: Ichor Wellspring, Galvanic Blast, Reckoner's Bargain, Refurbished Familiar, Utrom Monitor,
and the already-present Nihil Spellbomb. It also adds dedicated Krark-Clan Shaman coverage and
deterministic Grixis policy fixtures. No Batshit deck, seed, workflow, report, or experiment state is
adopted.

## Fail-closed state

- Runner: `DISABLED`
- Official games authorized: `0`
- Official seeds generated: `0`
- Outcome exposure: `0`
- Execution adapter: absent
- Seed vector: absent
- Execution workflow: absent

The next gate is deterministic CI validation of the card scenarios, exact deck registry, and agent
policy fixtures. Only after that entire gate is green may a separate no-outcome smoke harness be
specified. Seed generation and official gameplay require later, explicit freeze and authorization
gates.
