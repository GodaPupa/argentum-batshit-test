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

The current Mono-Blue test drops Mystic from its remaining support assertion. A separate current
inventory in the readiness validator makes the same explicit change. The historical unsupported
map, frozen sideboard status, deck hashes, accepted results, and execution safeguards are
unchanged. Negative regressions reject losing either historically supported Annul or newly
supported Mystic; all three disabled-execution errors remain mandatory.

## Initial validation rejection and bounded repair

The initial candidate `28b7cb4dd5b39bbd88409878a0f5ec8ea08c3579` was rejected by
[dedicated run 36086800652](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/36086800652)
and [general CI 36086800247](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/36086800247).
Its retained artifact `10844627205` has downloaded ZIP SHA-256
`ba8388b78271aa9132478a3fd0835dfe4710c85866d2f4b54f540faf2113d141`.
The archive binds the exact candidate and source-manifest SHA-256
`da8336bcc3e5fc7970038aef18ff780ef31b5e3f5acd81bcb1536acf62c990e6`.
All nine stage reports are present. The 338 strict snapshot/round-trip tests, three lint tests,
sixteen of seventeen card scenarios, and both inventory tests passed. Two Mono-Blue readiness
assertions and one Mystic scenario failed; the complete batch was not accepted.

The readiness failures came from comparing the current registry to the historical four-identity
sideboard gap map even after Mystic was implemented. The bounded repair introduces the explicit
three-identity current map described above, with missing-card regressions, while preserving the
historical record and all identity and execution guards.

The Mystic scenario failed with `You don't have priority` when the active player attempted
Divination after an opposing Lightning Bolt resolved. Source inspection found an inherited
rules defect in `PassPriorityHandler.resolveTopOfStack`: priority returns to the resolved stack
item's controller. Current Comprehensive Rules 117.3b instead gives priority to the active player
after resolution; 117.3c concerns retaining priority after casting or activating. This batch does
not modify that shared engine path or any frozen gameplay source. Its combined card scenario
now casts the active player's sorcery before the opposing instant, retaining the same positive
and negative Mystic trigger assertions without asserting the incorrect priority rule. The
priority defect is a separate canonical repair and gameplay-readiness consideration, not a card
behavior repair or permission to use changed engine code in an active frozen block.

No definition or snapshot was changed by this repair. A fresh validation and artifact audit are
required; the initial rejected archive is neither replaced nor relabeled.

## Qualified repair and concurrent main integration

The repaired source `8d401bda54a39ad75814b89b04c2bd42b4324843` passed
[dedicated run 36087840809](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/36087840809).
Artifact `10844573655` was downloaded and audited with ZIP SHA-256
`fcb8943840ba23ee748061bba96ca920425ae7468d27318320dd6dee7e26d29d`.
Its source-manifest digest is
`bf1c6473598a77b7d39fa183f2cb6d6be4967a7cbc10c434961404649803d817`;
all sixteen bound source files match, all nine stages have exit status zero, and all 367 tests
passed with no skipped tests: seventeen card scenarios, seven readiness tests, two inventory
tests, 338 strict snapshot/round-trip tests, and three lint tests. All official execution counters
remain zero. This establishes that repair's dedicated validation, not final integration approval.

While it ran, main advanced to `c6953354b1b799fe4506eb9e44ff25523a132d8e`, incorporating the
independently qualified [Spy support PR #148](https://github.com/GodaPupa/argentum-batshit-test/pull/148)
at `7a3f1429c027925c30ed7c1e329c3879e674574a` and the separate command repair. The integration
keeps both parents and all accepted production changes. Mesmeric Fiend is now supported in
Grixis's two sideboard slots and Spy's one; those three slots are attributed to Spy batch B,
separately from this batch's eleven. The combined current inventory is eleven unique unresolved
identities across twenty-nine slots. Grixis's sideboard is fully registered; Spy retains
Jack-o'-Lantern, Nyxborn Hydra, Flaring Pain, Faerie Macabre and Acorn Harvest.

The existing Spy B current-coverage test prospectively drops Nylea's Disciple, and the dedicated
workflow adds an independently retained compatibility stage for that test. Its original accepted
six-identity sideboard artifact and documents remain historical evidence. The integrated source
must pass fresh dedicated validation, relevant Spy regression checks, general CI, and artifact
audit before this PR can merge.

## No gameplay transition

Official seeds generated, games initialized, actions submitted, and outcome exposure by this
gate are all zero. Card scenarios are deterministic fixtures, not official games. No seed vector,
claim ref, one-shot authorization, engine/pilot freeze for gameplay, or postboard deck is created.
Monster Tron's independent frozen source and active execution state are outside this change.

The governing [qualification stopping rule](tier-one-qualification-stopping-rule.md) still requires
complete support, exact boarding plans and conservation, frozen postboard decks and policies,
and the separate bounded postboard smoke admission. Four additional supported identities do not
establish a matchup result or a Tier-1 conclusion.
