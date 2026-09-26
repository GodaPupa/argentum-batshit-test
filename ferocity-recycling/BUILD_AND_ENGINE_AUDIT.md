# Build and gameplay-engine compatibility audit

Audit date: 2026-09-26 UTC. Repository base: `d0c78bb4cca79b7402ba65bd62b5cb621230a054`.

This audit distinguishes source availability, compiled deterministic behavior, and admission to
interactive research. They are separate gates. The project has not inherited another experiment's
acceptance receipts, gameplay allocations, pilot qualification, or outcome conclusions.

## Actual environment work

The initial workspace had OpenJDK 17.0.20 and no `just`. The repository requires JVM toolchain 21
and routes heavy work through `just` and `scripts/gradle-locked`; see `AGENTS.md` and
`.agents/skills/verify/SKILL.md`. A real `just test-class KrarkClanShamanScenarioTest` invocation
failed because `just` was absent. That failure is retained in
`evidence/build/local-preflight-01.json`.

Normal `apt-get update` failed on the container's user/group-switch permissions. The project did
not change those permissions or disable apt's isolation. Instead it installed isolated official
release archives under a scratch toolchain directory. The Adoptium API timed out once; the official
Adoptium GitHub release endpoint succeeded. Release metadata, download identities, and failures are
recorded in `evidence/build/local-provision-*.json`.

| Component | Exact identity | Verified archive SHA-256 |
|---|---|---|
| just | 1.58.0, x86_64 Linux musl | `4a5cc2f53e6f0f8c59092a6cc38291eb729d46a7dd95d3ae582008881b84931d` |
| Java | Eclipse Temurin 21.0.12.1+1, x64 Linux HotSpot | `ce79869e1307ed8ee1e2baa86a412b1eb5b75d10a01006d788a6f968bcfaee94` |
| Gradle distribution | 9.6.1 binary ZIP, 140,682,664 bytes | `9c0f7faeeb306cb14e4279a3e084ca6b596894089a0638e68a07c945a32c9e14` |

The Gradle identity comes from the unchanged repository wrapper; its download was checked against
the official distribution's `.sha256` file. The first real Java/just attempt failed because Java's
direct network path was unavailable. Explicit use of the existing environment proxy advanced to a
10-second wrapper transport timeout. Those two attempts are preserved in
`evidence/build/local-engine-bootstrap-01/` and `local-engine-bootstrap-02/`. Downloading the exact
verified distribution through curl into the existing standard wrapper cache resolved distribution
provisioning. Every actual build still invokes `just`, with no raw Gradle bypass and no disabled TLS
verification. Java uses the operating system's trusted Java certificate store. The repository wrapper
reported `shlock` absent and used its existing unlocked fallback; this worker has run one build at a
time and has not altered the shared wrapper.

The third build invocation compiled the SDK, rules engine, every canonical card era through 2026,
test fixtures, and new project test sources, then **executed 24 Ferocity scenarios: 22 passed and
two failed**. Its result is recorded in `evidence/build/local-engine-bootstrap-03/`. Fixture edits
overlapped that invocation's initial dependency phase, so its source-stability guard correctly
rejected qualification from that attempt. It remains debugging evidence.

A fresh **fixed-scenario debugging replay**, `evidence/build/ferocity-stable-reproduction-01/`,
reproduced **22 passes and the same two failures**, with zero errors/skips and
`compiled_inputs_unchanged=true`. This is stable-source mechanical evidence, not an independent
randomized sample. The two failures prevent complete Ferocity qualification. Actual interactive
development, evaluation, and confirmation game counts are unchanged at zero.

The independent Crypt Rats fixture invocation, `evidence/build/rats-baseline-01/`, passed **8/8**
cases with stable compile inputs and no errors/skips. Its finite scope is black-only X payment,
damage to creatures (including flyers) and players, zero damage, Ferocity return without lifegain,
Toxin lifelink across actual recipients, prevention/indestructible, legal nontap activation after a
tapped return, and queued damage killing a returned one-toughness Rats. It does not resolve the
observed departed-source keyword defect or imply complete runtime card-pool admission.

The Toxin invocation, `evidence/build/toxin-baseline-01/`, executed **9 cases: eight passed and one
failed**, with stable compile inputs and no errors/skips. The queued departed-Shaman activation
incorrectly left its controller at 23 life rather than the required 24, establishing the comparator
lifelink defect. Clue creation, payment, sacrifice and draw, target fizzle, keyword expiry, symmetric
damage, prevention, zero damage, and source/caster control distinctions passed their assertions.

| Stable-source baseline class | Distinct cases | Passed | Failed |
|---|---:|---:|---:|
| FerocityOfTheHuntScenarioTest | 24 | 22 | 2 |
| CryptRatsScenarioTest | 8 | 8 | 0 |
| ToxinAnalysisScenarioTest | 9 | 8 | 1 |
| **Total distinct baseline scenarios** | **41** | **38** | **3** |

Including the separately retained 24-case source-drifted bootstrap gives 65 actual test-case
executions. Repeating that fixture set does not create additional independent scenarios or game
evidence. The departed-source defect is now recorded for both compared packages, so allowing either
configuration into comparative gameplay before repair would bias or invalidate the comparison.

The version catalog pins Kotlin 2.4.0, Kotest 6.2.1, kotlinx serialization 1.11.0,
coroutines 1.11.0, datetime 0.8.0, and the remaining declared dependencies. The exact catalog,
wrapper JAR, wrapper configuration, build scripts, task launcher, and applicable Kotlin compile
inputs are SHA-256 bound by `tools/validate_build.py`. Local receipts explicitly identify modified
and untracked compile inputs; they must not be misread as clean results at base HEAD.
`evidence/build/provisioned-dependency-artifacts.json` additionally hashes the 422 provisioned
dependency-cache artifacts (257,750,129 bytes) after the cold build. It is an explicitly
overinclusive cache inventory, not an assertion that every artifact is on the test runtime classpath.

## Real source inventory

`evidence/build/card-implementation-inventory.json` records exact file hashes. This is source
discovery, not an assertion that every discovered card already behaves correctly.

| Card or package | State at pinned main | Main compatibility question |
|---|---|---|
| Ferocity of the Hunt | No production canonical definition found | Project direct-test-only verified-text card; production registration/printing gate still required |
| Crypt Rats | No production canonical definition found | Project direct-test-only card; black-only X payment and player damage must pass |
| Krark-Clan Shaman | MRD canonical and scenario test present | Existing scenario covers sacrifice cost and nonflyer damage; deathtouch/lifelink/LKI combinations require project fixtures |
| Toxin Analysis | MKM canonical present; no dedicated scenario test at base | New separate scenario class qualifies target fizzle, lifelink, Clue, payment, and expiry |
| Not Dead After All | WOE canonical and three scenarios present | Return tapped, Wicked Role, removal of granted ability, sacrifice return; not proof of all Ferocity Aura semantics |
| Fungal Fortitude | LCI canonical present | Useful existing Aura death-trigger composition, not independent proof of Ferocity |
| Refurbished Familiar | MH3 canonical and three scenarios present | Affinity source count, remaining black pip, discard/draw branch; costs must remain correct after sacrifices |
| Shambling Ghast | AFR canonical and scenario test present | Trigger mode/target selection and ordering with sweep/return |
| Myr Retriever | MRD canonical present | Legal graveyard target, other-artifact restriction, target visibility and timing still need exact package qualification |
| Ichor Wellspring / Blood Fountain | Canonicals and scenario tests present | Entry/death draws, graveyard recovery, Blood decisions, mana and sacrifice accounting |
| Eviscerator's Insight | No canonical found under straight or curly apostrophe spelling | Missing card support if selected; flashback costs and sacrifice timing cannot be approximated |
| Fanatical Offering | LCI canonical and scenario test present | Sacrifice as cost, draws, Map creation and activation |
| Malevolent Rumble | MH3 canonical and two scenarios present | Permanent selection/mill/token; hidden-card handling must be qualified in pilot wrapper |
| Makeshift Munitions | XLN canonical and scenario test present | Sacrifice costs, legal damage target, practical clock; discovering a loop is not assigning a win |

The new Ferocity and Rats definitions live inside separate real-engine scenario test files. They
use `ScenarioTestBase`, the real registry, `ActionProcessor`, targeting, payment, stack resolution,
state-based actions, and zone transitions. They are not an alternate Python mechanic simulation.
Because they are test-only definitions, a pass does not itself make either card available to a
production deck loader. Runtime registration remains an explicit later implementation step.

## Specific source risks under qualification

Ferocity's trigger is an ability on the Aura, while Not Dead After All grants an ability to the
creature. The Ferocity composition uses an attached-source death trigger and returns the triggering
card tapped under its owner's control. No Aura, keyword bonus, haste, or untap follows that returned
new object. The verified rules audit is authoritative for Oracle wording and rules details.

Source inspection identified, and stable-source execution reproduced, a defect in pending damage activations:
`DealDamageExecutor` passes the source entity ID into `DamageUtils`; the latter's deathtouch and
lifelink read sites consult projected/current keywords and spell keyword grants. This does not by
itself establish a correct last-known-information snapshot for an original permanent that died and
returned using the same internal entity ID. A queued old Shaman ability must not read the returned
creature's new unenchanted characteristics. Two real-engine fixtures failed because the opposing
Craw Wurm survived damage that should retain the original Shaman's deathtouch: one after source
removal and Aura return, the other when graveyard hate prevented the return. Both failures repeated
with stable compile inputs. Live-source deathtouch gained after activation and lost before damage
both passed, so taking a static keyword snapshot at activation would be an incorrect repair.
No shared engine source was changed by this audit. The comparator lifelink failure was established
in its separate stable-source baseline before any repair. The defect must not be concealed by
weakening the assertions or leaving only one package affected.

Token movement, graveyard removal before a trigger resolves, stolen-creature ownership, Aura
target legality, APNAP and simultaneous-death ordering, prevention, indestructible, and zero damage
must retain their actual engine behavior. Unsupported actions cannot be converted into a pass,
automatic simplification, or winner assignment.

## Reusable gameplay infrastructure and pilot limits

The `gym` module provides real `GameEnvironment` reset/step/observe/fork operations. Its
`TrainingObservation` masks opposing hands and libraries by default. Structured choices such as
multi-card selections, target ordering, search-library and distribute decisions require explicit
responses; an action-ID-only loop is insufficient for this project. `revealAll=true` is a debugging
facility and is not admissible to a research pilot.

The generic `EngineAiPlayerController` explicitly receives unmasked `GameState` through a state
provider. `AIPlayer` can pass a `Determinizer` to its strategist when the selected profile requests
it, but that implementation detail does not establish information safety at every choice boundary.
No project pilot has yet passed public-observation equivalence tests under changes to hidden cards,
draw order, or opponent hand. The project cannot admit the generic AI merely because it exists.

`GrixisAffinityAgentDecisionTest` contains useful fixed probes for land sequencing, productive
Wellspring sacrifice, metalcraft removal, affinity deployment, graveyard interaction, and a Shaman
sweep. The generic policy was built for other work; these probes neither train a Ferocity pilot nor
certify a current benchmark pilot. The project may rerun relevant deterministic code against its
exact source version without inheriting that other program's experiment conclusions.

The other programs' ready/disabled contracts and private runners are not this project's runner.
No existing official vector, allocation, opponent evidence, or private evaluation material was
imported. A new project runner still requires exact candidate/benchmark registry coverage, visible
errors for unsupported decisions, recorded actions and replay checks, caps/invalid-attempt handling,
source/deck/policy identities, and an independently frozen development allocation.

## Qualification workflow and evidence contract

`.github/workflows/ferocity-recycling-validation.yml` runs on scoped pull requests. It uses JDK
21.0.12.1+1 and the exact checked just archive. It runs project integrity and
arithmetic tests, verifies an existing freeze if present, then calls `tools/validate_build.py`.
It does not create an experimental freeze, allocate seeds, launch randomized simulations, or
initialize games. Project evidence uploads run even when a test fails.

The validation runner:

1. Binds HEAD, tree, launcher/build dependency files, selected scenario sources, and every applicable
   Kotlin compile input including untracked local fixtures.
2. Rejects dirty compiled inputs when an exact CI HEAD is requested.
3. Preserves each invocation's command, status, complete log, class XML, and SHA-256 hashes.
4. Archives then removes only that class's preexisting XML, so stale XML cannot qualify an attempt.
5. Uses Gradle's per-task `--rerun` option and explicitly rejects `FROM-CACHE`, `UP-TO-DATE`, skipped,
   or absent test-task execution markers. Requires real executed assertions, no skipped tests, zero
   errors/failures, and successful process completion. It stops on the first failure without
   rerunning it as a new result.
6. Includes Kotlin, Java, KTS and runtime/test resources in source capture, rejects deleted tracked
   inputs by path membership, and rejects changes during execution. Failure to capture final inputs
   writes a failed receipt rather than leaving a running status.

Publication review added five evidence-guard unit tests, all passing. These use explicitly synthetic
XML to verify rejection of cached success, deleted inputs, and final-capture errors, plus acceptance
of an executed-task marker and coverage of Java/KTS/resources. They are not engine scenarios or
gameplay. `evidence/build/validator-guard-review.json` binds that validator revision and confirms
that every retained actual baseline log reports an executed test task rather than a cache hit;
the baseline counts above therefore remain valid. Existing receipts and original validator hashes
were preserved.

The workflow records the runner image OS/version/architecture. Its `ubuntu-latest` image selection
and action major-version references remain mutable infrastructure inputs. The observed runtime and
source identities support this diagnostic publication; independent evaluation still requires its
own exact resolved dependency/runtime identity and acceptance contract. No claim of fully pinned
gameplay deployment is made by this workflow.

The current default class set is Ferocity, Rats, Toxin, Shaman, Not Dead After All, Familiar, Rumble,
Offering, Wellspring, Ghast, Fountain, Munitions, and the fixed Grixis decision probes. This selection
checks useful available primitives; it is not a substitute for the final chosen-pool interaction
matrix or complete gameplay-policy qualification. A green workflow must retain this limited claim.

### First published CI setup incident

At published source `901729f259d044dfa157f745b8d764cb454922ef`, push run `36224992267`
and PR run `36224994898` both failed before compilation or assertions. `actions/setup-java@v5`
rejected the official four-component Temurin release string `21.0.12.1+1` as invalid SemVer.
Exact job logs, action revisions, runner identity and uploaded provenance-artifact identities are
retained in `evidence/build/ci-setup-semver-incident-01/`. Both jobs executed **zero** engine cases
and **zero** research games; they neither add to nor invalidate the local stable-source 38/41
baseline. The workflow's early provenance upload worked on this failure path.

The scoped repair replaces that version parser with installation of the exact official Temurin
archive already used successfully locally. It verifies the same SHA-256 before extraction, then
exports its `JAVA_HOME` and executable path and preserves a JDK provenance record. No Java release,
test assertion or TLS policy changed. The installer has passed static review at this checkpoint;
fresh CI execution after publication must establish its hosted-runner result. No blind rerun was
dispatched. Because the project already has PR #173, the revised workflow keeps its path-scoped PR
trigger and removes its redundant push trigger; one source update no longer launches two identical
qualification jobs. Both original failed setup jobs remain preserved. Known baseline mechanical
failures remain expected until the separately reviewed engine
repair is accepted and tested against this project's exact inputs.

## Admission status

Interactive development: **not admitted**. Evaluation: **not admitted**. Confirmation: **not
admitted**. Counts remain **0 / 0 / 0 games**. Technical failures have no interpretation as a deck
performance verdict. The next authorized work is to resolve the recorded real build result, qualify
the selected mechanics, register required production cards through the normal card process, and
qualify actual bounded pilots and full-game runner before consuming development samples.
