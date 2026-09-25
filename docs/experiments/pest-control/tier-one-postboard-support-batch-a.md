# Pest Control: postboard support batch A

## Scope and evidence boundary

This is a seed-free implementation gate within the existing fixed five-opponent qualification
program. It closes four card identities and eleven sideboard slots from the accepted support
inventory, without selecting boarding plans or constructing postboard decks.

The input is [support audit run 36070379296](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/36070379296),
artifact `10838516145`, whose downloaded ZIP SHA-256 is
`c0c08f915348a0512dd15a17d2edad1479b3f1d09d59948455fe3c81f94b10b7`.
The accepted artifact is unchanged. The new coverage report is a successor inventory, not an
amendment to historical evidence. The initial implementation base is
`684f93af469815d3b9636ba1b117ed3bfd6a34fb`.

| Definition | Canonical printing | Fixed sideboard slots | Existing mechanics qualified here |
|---|---|---|---|
| Smash to Smithereens | SHM 107 | Mono-Red Madness: 4 | Destroy, last-known target controller, damage |
| Unexpected Fangs | IKO 102 | Grixis Affinity: 2 | +1/+1 and lifelink counters |
| Murmuring Mystic | GRN 45 | Mono-Blue Terror: 1 | Own instant/sorcery cast trigger, flying token creation |
| Nylea's Disciple | THS 167 | Spy Combo: 4 | Enters trigger, resolution-time green devotion, life gain |

All four cards were verified as Pauper legal against their current Scryfall records on
2026-09-25. Mystic's earliest printing is uncommon; its later CMM 107 common printing supplies
the common printing relevant to Pauper. Its canonical definition remains GRN 45. The exact API
URLs, payload digests, canonical printing fields, rulings, and common-printing evidence are
recorded in `tier-one-postboard-support-batch-a-sources.json`.

Mystic's behavior is selectively reused from Izzet's accepted implementation at
`b71785b61195039a14bfafbaa75827c978c8a757` (Batch D, run `35760495294`, artifact
`10709599429`; accepted source blob `9a9034f02ce9b99d70ae89cb1426da2a74598ef0`).
This batch adds current canonical card art, token art,
flavor text, and the cast-trigger ruling, and independently qualifies behavior in the Pest source
lineage. No Izzet deck, pilot, matchup result, sample, or execution authority is imported. The
other three definitions compose mechanics already present on the implementation base. There are
no engine or SDK changes.

## Behavioral qualification

The seventeen deterministic scenarios are separate from experimental games:

- Smash: a stolen artifact is destroyed before damage reaches its last controller; an
  indestructible artifact survives while its controller takes damage; a removed target makes the
  entire spell fail to resolve; a nonartifact is rejected without payment.
- Fangs: both counters survive combat and cleanup; real combat and noncombat damage gains life;
  the lifegain belongs to the creature's controller, including an opposing creature; a removed
  target gets neither counter; a noncreature is rejected without changing state.
- Mystic: the exact 1/1 blue Bird Illusion with flying appears before its instant resolves;
  own sorceries trigger while creatures and opposing spells do not; the trigger survives a
  countered spell or removed Mystic; a spell copy does not create an extra trigger.
- Disciple: devotion includes its own two green symbols and a hybrid green symbol, excludes
  opposing permanents, lands' rules text and other zones, and is recomputed after Disciple leaves
  before its trigger resolves, including the zero case.

The dedicated workflow checks out the exact candidate SHA, verifies definition/test file digests,
and checks every previous snapshot block byte-for-byte before running the strict compiled card
snapshot and round-trip tests. Only the four new card blocks are added. It also runs card lint,
the updated current Mono-Blue coverage assertion, the new six-sideboard coverage gate, and the
unchanged general sideboard inventory. It preserves each stage's XML before another Gradle test
invocation can replace the report, and uploads evidence even after a failure.

The local environment has no approved Kotlin/`just` runtime. Local checks cover source structure,
source field binding, snapshot preservation, and workflow syntax. Behavioral acceptance requires
the dedicated workflow, general CI, and artifact audit. Manually prepared snapshot additions are
prospective expected trees; they are accepted only if the unchanged strict snapshot test agrees
with the compiled definitions. No snapshot update mode runs in this workflow.

## Prospective current queue

On the initial base, this batch reduces unresolved sideboard identities from sixteen to twelve
and unresolved slots from forty-three to thirty-two. Pest's own fifteen sideboard slots remain
supported. Mono-Red retains Pyroblast/Relic; Grixis retains Mesmeric Fiend; Mono-Blue retains
Gut Shot/Hydroblast/Spreading Seas; Monster Tron retains Hydroblast/Kaervek's Torch/Pyroblast/Relic;
Spy retains Jack-o'-Lantern/Nyxborn Hydra/Flaring Pain/Mesmeric Fiend/Faerie Macabre/Acorn Harvest.
Concurrent accepted support batches must be reconciled before integration; their closures are
attributed to those batches, not to these four definitions.

The current Mono-Blue test drops Mystic from its remaining support assertion. Its frozen deck,
historical readiness data, accepted results, and execution safeguards are unchanged.

## No gameplay transition

Official seeds generated, games initialized, actions submitted, and outcome exposure by this
gate are all zero. Card scenarios are deterministic fixtures, not official games. No seed vector,
claim ref, one-shot authorization, engine/pilot freeze for gameplay, or postboard deck is created.
Monster Tron's independent frozen source and active execution state are outside this change.

The governing [qualification stopping rule](tier-one-qualification-stopping-rule.md) still requires
complete support, exact boarding plans and conservation, frozen postboard decks and policies,
and the separate bounded postboard smoke admission. Four additional supported identities do not
establish a matchup result or a Tier-1 conclusion.
