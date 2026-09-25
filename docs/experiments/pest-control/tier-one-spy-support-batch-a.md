# Spy Combo support batch A

This seed-free batch follows the accepted Spy admission at merge
`0cca9a6ef00e5597f646745453e57d9d987bfa01`. It preserves Dr_dej96's exact 60/15 and
does not initialize official games, create seeds, define a pilot, or authorize outcome exposure.

The independently downloaded admission artifact `10838356890` from run `36070862863`
has ZIP SHA-256 `bacf86c2a1aed44bd5b8cc765b0abf0ae668fe6971d99d51a2024268a9491ae2`
and identifies ten unresolved main-deck identities. This batch addresses five:

| Card | Existing primitives | Canonical printing |
|---|---|---|
| Gatecreeper Vine | Optional ETB and basic-land-or-Gate search | RTR 124 |
| Overgrown Battlement | Projected defender count and immediate green mana ability | ROE 203 |
| Lotleth Giant | Targeted ETB damage counted from own graveyard creature cards | GRN 74 |
| Lead the Stampede | Look, optional creature selection, reveal, ordered bottom placement | MBS 82 |
| Balustrade Spy | Targeted reveal-until-land and move revealed collection to graveyard | GTC 57 |

Implementation logic is reused from accepted Industrial Waste source
`abfd806f6332c0da311e40b01c9d85eeb0962165`. Source paths and SHA-256 digests,
current Scryfall Oracle text, Pauper legality, and canonical printing references are recorded
in `tier-one-spy-support-batch-a-sources.json`. No Industrial result, opponent policy,
seed registry, or historical CI exception transfers to Pest Control.

The source branch placed Lead the Stampede in ROE. The first expansion printing is MBS,
so this integration places it in the already-scaffolded MBS package and uses collector 82.
Current Oracle wording and printing metadata are refreshed for all five cards.

Each card has its own scenario test. New-card snapshot entries are derived only from the
corresponding source card's serialized tree and verified metadata. Existing target-branch
entries remain byte-for-byte unchanged; the ordinary snapshot comparison must pass against
the freshly compiled definitions. No snapshot-update mode or broad CI exception is enabled.

Expected next unresolved main queue after a green audit:
Nyxborn Hydra, Mesmeric Fiend, Wall of Roots, Land Grant, and Winding Way.
Winding Way's source implementation uses a card-type-from-variable predicate absent from
this target branch and therefore needs separately qualified shared support. The other
remaining cards also require exact capability checks before integration.

The dedicated workflow independently executes card scenarios and produces the updated
registry inventory. A green batch qualifies card support only; exact Spy pilot, runner,
seed, execution, and postboard gates remain separate requirements.
