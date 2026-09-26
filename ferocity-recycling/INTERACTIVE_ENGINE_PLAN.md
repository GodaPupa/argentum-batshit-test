# Ferocity Recycling — interactive engine integration plan v0.1

Date: 2026-09-26 UTC. This is a read-only architecture audit and a bounded implementation plan. It executed **zero games, zero randomized trials, and zero new build/test commands**. It changes no frozen deck, policy, protocol, allocation, or other experiment. Exact inspected source paths and hashes appear in the evidence appendix.

## Finding and minimal viable path

The repository contains a real interactive rules engine and an appropriate single-action execution primitive: `GameEnvironment.stepExactlyOne(GameAction)`. The shortest defensible route is a **project-owned JVM runner using that primitive, an actor-only information boundary, complete parameter/decision handling, tailored pilots, and durable action/event recording**. The existing Pest Control production loops are useful examples of sequencing and recording, but none is a drop-in Ferocity runner or a qualification receipt for the present decks.

The engine build is provisioned according to the project build audit. The separately recorded stable baseline is 41 deterministic cases, 38 passing and three failing, including departed-source deathtouch/lifelink behavior. Repair and requalification of that behavior remain prerequisites, alongside the remaining mechanics matrix. Later repair receipts may supersede this baseline; this document does not infer their results. Static coverage independently records 29 missing canonical identities in the complete pool. Test-local Ferocity/Rats definitions do not themselves populate the compiled card registry. [Build audit](BUILD_AND_ENGINE_AUDIT.md), [mechanical qualification](MECHANICAL_QUALIFICATION.md), [card coverage](CARD_COVERAGE_AUDIT.md).

There is no demonstrated fundamental impossibility in using this engine. There are concrete missing implementation and qualification steps. In particular, the inspected trigger pipeline does not expose a demonstrated player choice for ordering simultaneous triggers they control, and X-cost menu metadata does not consistently honor colored-mana restrictions. Those are rule/action-surface blockers to resolve, not reasons to award a loss to a deck.

## Reuse boundaries established from code

| Existing component | Useful behavior | Boundary for this project |
|---|---|---|
| `GameEnvironment` | Explicit initialization, authoritative state, legal-action templates, direct action submission, terminal state and emitted events | Use `stepExactlyOne`; keep the environment and authoritative state inside the trusted runner. Do not use its evaluator/reward as a game result. |
| `stepExactlyOne` / `ExactlyOneSubmissionResult` | Calls `ActionProcessor.process` once; returns typed `Applied` or `Rejected`; leaves a rejected state uninstalled; exposes resulting pending decision | Log every selected action before submission and its actual result afterward. A rules resolution caused by that action is legitimate; no extra pilot action is synthesized. |
| `GameEnvironment.step` and `playGame` | Convenience simulation with automatic decision/priority handling | Unsuitable unchanged: the simulator may answer choices automatically; `step` exposes rejection separately; `playGame` reports `truncated=false` even when its loop reaches its step limit. |
| Pest preboard/Grixis/MonoBlue drivers | Explicit London actions, acting-player selection, exact-one-action loops and serialized events | Their deck identities, profile bindings, caps, constructors, seed vectors, and acceptance machinery belong to Pest Control. Do not invoke their initializers, alter constants, or borrow allocations. |
| Pest Monster Tron operational driver | Preserves partial failed-game raw data; distinguishes durable action INTENT from RESULT; durable attempt-before-initialization pattern | Reuse the design in the new project namespace. Its exact opponent is mehanske's different September 21 list, not this project's kuldothared September 20 list. |
| `TableGameRunner` / arena wrapper | Useful crash-finding and game-loop examples | Skips mulligans, may replace a rejected action with `safeFallbackAction`, and groups unfinished games into its own draw taxonomy. Do not use unchanged for accepted research outcomes. |
| `DeckResolver` and `CardRegistry` | Explicit deck expansion and card lookup; registry supports isolated child overlays | Resolver defaults to minimum 40 and does not enforce Pauper bans/four-copy rules. Registry lookup is case-sensitive and registrations can overwrite a prior name. Apply the project's exact-60/source/duplicate checks first. |
| Generic AI/advisors | Existing targeting, combat, mana planning, card-intent logic and configurable deck-specific modules | Useful behind a qualified synthetic-information boundary, with project-specific policies and budgets. Existing policy labels and scenario results do not establish present benchmark competence. |
| Gym HTTP/observation transport | Structured public observations and action/decision schemas | Not an adequate boundary unchanged: observation perspective can differ from acting player, the current action/decision payload can then reveal another player's information, and action indices lack an epoch binding. |

The inspected Manual Transmission Python work is explicitly a timing/classification overlay and enumerates modeled resource situations. It is not an alternate full-game Pauper engine. The Izzet snapshot workflow is a card-snapshot maintenance operation. Neither provides interactive outcomes for this project. No Industrial Waste or Sphinx's Approach runner was imported; absence from this checkout's inspected paths is not a claim that those projects have no runner elsewhere.

## The actor-only information boundary

The authoritative `GameState`, environment, registry mutation interface, seed ledger, future game randomness, and omniscient recorder must be inaccessible to every pilot. A proposed `FerocityPilotInput` is immutable data containing only:

- Acting player and decision epoch; public phase/step/priority/stack; projected public permanent characteristics, damage, attachments, counters and status; public graveyards and visible exile; life/mana and public zone counts.
- That actor's current hand and other information they are currently entitled to know. Opponent hands, unrevealed library identities/order, hidden exile identities, and authoritative RNG state are absent.
- Current legal action templates and their complete parameter domains, or the exact pending decision belonging to this actor. The binding includes a hash of the observable state, decision ID, menu, and source version.
- A separately allocated policy RNG stream/state and the explicitly permitted pregame knowledge. Whether the benchmark's exact list is open information or only its archetype is supplied must be fixed before D2; the runner cannot infer hidden cards from the realized shuffled game.

The trusted adapter builds the observation for `pendingDecision.playerId`, otherwise the priority player. It rejects requests by any other perspective. It must not attach an opponent's legal action descriptions to a different player's masked board. `revealAll=true` is reserved for the isolated recorder and debugging; it is never a pilot option.

Mandatory choices need explicit visibility handling. A search-library decision may expose the searching player's legal search options because the effect authorizes the search; it does not expose the opponent's library or a future top-card ordering. Scry, surveil, Rumble, Stirrings, and similar effects reveal only their actual currently authorized cards to the appropriate actor. Random draw identities become usable after that draw resolves. Public discard/return information becomes public when the rules make it so. Targets, modes, sacrifice choices, optional effects, ordering decisions, and mandatory discards each retain their complete current legal domain. The adapter must reject an unhandled decision type visibly instead of returning an empty answer or auto-passing.

The existing `ObservationBuilder` supplies useful visible card projections but is not the entire security boundary: it includes legal descriptions supplied by its caller, and pending-decision data is not universally filtered by recipient. The default `GameGymEnv` fixes an observation perspective while asking `environment.legalActions()` for the actor; this combination must not be used unchanged. Its raw `submitDecision` path also does not perform the same explicit rejection check as its action-ID step path.

### Can the existing AI operate on synthetic states?

Yes, this is a concrete reuse option, subject to qualification. A **trusted synthetic-state builder** can construct hypothetical worlds solely from the immutable pilot observation, permitted deck knowledge and independent policy RNG. It may use `HiddenWorldMaterializer` as a checked transformation primitive. Every unknown identity and future random state must be supplied independently of the realized game. The AI receives only the resulting synthetic state; it receives no authoritative state reference, closure, environment, or recorder object.

`HiddenWorldMaterializer` requires explicit slot assignments and `futureRng`, returns `Materialized` or `Unsupported`, and can reject unsafe in-flight substitutions. It is not itself an information boundary: the adapter must establish that all unresolved hidden slots and relevant hidden components were replaced, not merely request changes for a convenient subset. An `Unsupported` result is a recorded unsupported policy branch, never permission to evaluate the original state. Public/currently revealed object IDs must remain correctly associated so the proposed actual action can be bound back to the live legal menu. A proposed action using a synthetic-only hidden object is rejected by the adapter before authoritative submission.

Do not mistake the current `Determinizer` for this boundary. `AiProfile.CURRENT` and `PRODUCTION` default to determinization off. The Pest drivers explicitly select `PRODUCTION_CANDIDATE_EXPIRING`, which **does inherit determinization on** from `PRODUCTION_CANDIDATE`. Nevertheless, `AIPlayer.respondToDecision` passes its supplied state to the responder; the determinizer can use identity-permutation fallback, preserve unsafe hidden slots, return the original state for unsupported in-flight cases, and retain authoritative future RNG. Some responder branches mask newly drawn cards during a narrow completed-branch evaluation, but that does not qualify all yes/no, target, mode, combat and cost choices.

For a minimal implementation, start with deterministic public/own-information rules for the current actor's choices and use shared combat/mana helpers only through this boundary. Add generic synthetic-world search where it supplies demonstrated competence. Any internal action ranking is a pilot implementation detail; only actual terminal game outcomes answer the research question. Give both Ferocity and no-Ferocity pilots the same synthetic-world count, deterministic search-work budget, and permitted information. Wall-clock search cutoffs must not silently select different best-so-far actions: use fixed work limits and treat the game watchdog as an unresolved attempt.

### Mandatory information-invariance qualification

For a fixed visible decision and fixed independent policy RNG, the serialized pilot input and chosen action must remain identical when the test changes:

1. Opponent hand identities while preserving visible hand count and previously revealed knowledge.
2. The unseen order of the actor's library, the opponent's library, or both; also change unseen identities where consistent with permitted knowledge.
3. Hidden exile or face-down card identities and unrelated hidden object IDs.
4. The authoritative game's RNG state and future random outcomes.
5. Another player's pending private decision when this actor is not entitled to receive it.

Companion positive cases must verify that newly authorized information actually reaches the correct actor: a revealed top group, legal library search, public discard, and the actor's own newly drawn card. Compare complete input payloads, not only final chosen actions; a leaking field is a failure even when a heuristic happens not to use it. Repeat the same tests at mulligan/bottoming, ordinary priority, target/mode selection, sacrifice payment, combat/ninjutsu, and resolution-choice boundaries. Deterministic fixtures consume D1 policy development opportunities as specified by the active contract; they are not matchup games.

## Legal choices and rule surfaces that require explicit work

The enumerator returns `LegalAction` **templates and metadata**, not every completely specified action. It also exposes entries marked `affordable=false`; a listed template is not proof of immediate playability. A bare action can validly ask for later parameters. The project needs a total typed choice dispatcher and stale-menu rejection, not a loop that submits the first action ID.

- **Targets, mana, costs and X.** `ActivateAbility` templates may leave targets, `xValue` and cost payment unset. Mana abilities advertise color choices. The gym `ActionParams`/observation combination does not expose every mana-color and sacrifice-payment field. Preserve these domains in the project adapter and submit them through actual engine actions/decisions. A cost is paid once, atomically; no pilot gets a response midway through payment.
- **Crypt Rats.** `CostEnumerationUtils.calculateMaxAffordableX` and the mana-X choice construction inspected here count total available mana without the ability's black-only restriction, while actual activation payment paths do enforce `xManaRestriction`. A visible maximum is therefore not proof an X is payable. Qualify black/colorless mixtures, zero, alternative colored-source assignments, and payment that preserves interaction; narrowly fix the advertised domain or supply a fully validated project domain. Do not repeatedly submit unaffordable X values and repair them afterward.
- **Sacrifice choices.** The activation handler can issue `SelectCardsDecision` when there are several eligible sacrifices; forced singleton payment is distinct. Preserve choice among Wellspring, Clue, Blood, Treasure, creature and artifact-land resources where the actual cost allows it. Recompute future affinity costs and fodder after every action.
- **Same-controller trigger order.** The inspected trigger processor and cast-priority pipeline preserve trigger-list order while applying APNAP ordering; this audit found no explicit player ordering decision. Qualify simultaneous Ferocity, Ghast, Wellspring, Familiar and other selected triggers. If the player cannot express a legal order that changes play, an engine extension is required before affected games are admitted. Do not silently bless insertion order as a pilot decision.
- **Combat and alternate casts.** Attackers/blockers are parameterized; ninjutsu and Leonardo's Sneak need the actual unblocked-attacker/declare-blockers window. Nyxborn Hydra needs creature and bestow paths with actual X/cost/attachment handling. Sazacap's Brew needs gift choice, additional discard, target player and conditional creature target. Black Mage's Rod requires its entry token/attachment and granted trigger. These are selected-card obligations even though a canonical file exists.
- **Decisions versus automatic resolution.** A real cost payment or state-based action may proceed inside one engine submission. A pilot's choice or priority pass must not be supplied by an unrecorded simulator. Reject missing cases, invalid parameters and stale epochs visibly; retain the original attempt.

## Exact pool and bounded implementation queue

The complete 24-list archive contains 135 identities. For **preboard** support, the union of main decks contains 110 identities, of which the baseline static audit is missing 22. The remaining seven missing identities are sideboard-only. This split is a dependency ordering, not permission to drop cards or relevant opponent lines. Counts refer to the hashed coverage snapshot, not later card implementation work.

| Scope | Missing canonical identities at baseline |
|---|---|
| Candidate/comparator main decks | Accursed Marauder; Avenging Hunter; Bone Picker; Crypt Rats; Cut Down; Defile; Dispatch; Eviscerator's Insight; Ferocity of the Hunt; Glint Hawk; Journey to Nowhere; Kor Skyfisher; Thorn of the Black Rose; Witch's Cottage |
| Additional current benchmark main-deck needs | Agony Warp; Augur of Bolas; Contaminated Landscape; Moon-Circuit Hacker; Ninja of the Deep Hours; Spinning Darkness; Unmake; Writhing Chrysalis |
| Additional benchmark sideboard-only needs | Arms of Hadar; Crimson Fleet Commodore; Drown in Sorrow; Faerie Macabre; Relic of Progenitus; Searing Blaze; Vandalblast |

All 22 preboard identities are already in the frozen source audit; this plan introduces no new card search or architecture. Implement canonical cards using the repository card workflow, qualify existing primitives before adding shared engine vocabulary, and retain one card per scenario file. Existing cards also require actual selected-mode qualification; this missing-name list is not a complete mechanics checklist.

### Independent pilots for the actual opponents and treatments

Use three candidate-family pilot modules and distinct benchmark modules, with treatment-specific package logic where justified. Each no-Ferocity list uses its actual rebuild and receives equivalent bounded development. Do not require the same action heuristic when the lists have different legal engines. Do not import any old win-rate claim or calibration result.

| Pilot | Concrete qualification priorities |
|---|---|
| A artifact/Shaman, including A3 Grixis | Colored artifact-land order, Familiar/Enforcer/Monitor affinity, productive sacrifice, Shaman collateral damage, Ferocity/Toxin/return timing, evasion and clock, blue draw/Strix choices |
| B black/Golgari Rats | Black-only X, own life and friendly losses, lifegain before/after sweeps, swamp count, tapped-return vulnerability, real selection/recursion costs and a finish |
| C Orzhov recovery | Bounce targets, Auramancer recovery, Dispatch metalcraft, disposable entry value, Aura mana expenditure, flying clock; no-Ferocity builds contain no Auramancer baggage |
| MisterTwin Red Madness, September 24 | Discard/madness funding and windows, Sneaky Snacker's third-draw trigger, gift/target choices, Fireblast/Lava Dart land sacrifice, burn-to-creature versus lethal/race lines |
| barff Dimir Faeries, September 25 | Counterspell/Dispel/Spellstutter windows and real faerie count, Brainstorm and authorized top-card knowledge, ninjutsu/Mukotai lines, monarch acquisition/defense, removal and colored lands |
| seasonofmists Esper Affinity, September 25 | Affinity deployment, Drum creatures/colors, paid Clue draw, Dispatch before/after artifact losses, Moon-Circuit Hacker and Leonardo Sneak timing, go-wide versus sweeper recovery |
| John1111 Golgari Gardens, September 20 | Rats/life management, draw-fodder sacrifice, Campfire and library/graveyard recovery, Cottage/landcycling, initiative acquisition/defense, Craft, graveyard hate and finishing |
| kuldothared Tron, September 20 | Missing Tron-piece searches, Crop Rotation costs, Boulder/Jelly fixing versus removal, Rumble/Spawn mana, Chrysalis, Hydra X/bestow, Wurm life, drawing and deploying an actual finish |
| barba94 Grixis comparator, September 24 | Artifact sequencing and sacrifice, Toxin/Shaman, Bargain lifegain, Munitions reach, Rod and Cam modes, Familiar/Monitor pressure and graveyard interaction |

Existing fixed probes in `GrixisAffinityAgentDecisionTest`, `PestControlMonoRedMadnessMulliganPolicyTest`, and `PestMonsterTronPolicyAuditTest` provide useful scenario designs. The Tron advisor explicitly handles Map/Rotation/Stirrings, Ornament and Bog for another exact list. Those public-state rules can be adapted and requalified, but they omit substantial parts of this project's Tron build. The generic mulligan code itself uses printed mana value, early-horizon and additional-cost heuristics plus a typecycling helper; this does not establish competence for reduced-cost affinity hands, Rats life plans or Aura-recovery hands.

Preserve the active contract's limit: each candidate family and benchmark pilot gets at most two documented revisions using up to 24 fixed training scenarios per revision; D3 has at most its single permitted policy revision before new outcomes. The effective amendment limits D3 to 240 games with no incumbent resampling, and moves sideboard development before E. This plan adds no extra self-play, rehearsal or calibration game allowance. [Active contract](protocols/ACTIVE_CONTRACT.json), [reconciliation](protocols/RECONCILIATION_AMENDMENT.md).

## One runner, one recorder, one replay path

Proposed project implementation units are a runner/input loader, actor-observation adapter, complete choice dispatcher, synthetic-policy bridge with independent RNG, pilot modules, an append-only recorder, and a replay checker. Keep them project-scoped. A test-runtime entry under `gym/src/test/kotlin/com/wingedsheep/gym/` can use the existing `:gym` dependencies plus test-only `:mtg-sets`; a new server process or external simulator is unnecessary. These proposed units and entrypoint **do not yet exist as qualified executable code**.

The executable sequence should be:

1. Validate the active contract, list/source/policy/dependency hashes and all exact registry names. Register tokens and canonical definitions, detect unintended duplicates, resolve the explicit documented Unicode aliases, and require exact 60-card inputs. Do not strip diacritics indiscriminately or silently substitute unknown names.
2. After the applicable admission receipt is accepted, durably claim one project trial before `reset`. Initialize two players at 20 life with `skipMulligans=false`, `useHandSmoother=false`, explicit starting-player allocation, and the recorded game RNG seed. Preserve the exact initialization configuration and initialized state.
3. Execute London keep/mulligan/bottom actions explicitly with each actor's own information. Then select the pending-decision player or priority holder, construct the actor input/menu and record its hash, obtain one fully bound action, record INTENT, call `stepExactlyOne`, and record RESULT with all emitted events.
4. Stop on actual engine terminal state or an explicit failure/cap. A terminal winner is win/loss; a legal terminal draw is distinct. Invalid actions, unsupported choices, missing priority, engine exceptions, observer faults, and caps are unresolved attempts. Record every partial action sequence and original failure; no repaired fallback action or reroll.
5. Apply the frozen limits: 6,000 submitted actions, 150 complete turns and 300 seconds of game runtime, excluding CI queue delay. Specify the exact completed-turn counter mapping to `GameState.turnNumber` before D2; it counts player turns, not full two-player rounds. Make the 6,000th action's legitimate terminal outcome and the nonterminal cap distinguishable. Persist throughout so a watchdog exit retains evidence.

Use an exclusive create-new attempt file and append-only action records with flushing, following the good pattern in the Monster Tron operational stack. A durable INTENT is not proof that the processor returned; separate RESULT or interrupted status is required. A single project validation workflow and one gameplay entrypoint are sufficient; additional authorization layers and duplicated per-opponent orchestration are unnecessary. Heavy future build/test execution remains through `just`, with the project's build receipt wrapper and exact runtime/dependency hashes. Do not dispatch any Pest workflow.

Replay should deserialize the exact initial `GameState` with the current state serializer and submit the recorded actual `GameAction` sequence through the same authoritative processor, comparing the full ordered event stream and after-state digest at every step. Preserve initial events, player IDs, entity-ID counter, RNG state, card-definition hashes, and serialization identity. Recording only seed plus a reconstructed configuration is insufficient unless initialization equivalence is also proven: replacing generated player IDs with explicit ones can change entity-ID allocation. `SnapshotCodec` is an in-memory slot store, not a durable experiment archive; `GameGymEnv.snapshot` does not preserve a meaningful step counter by default.

The game-server replay code demonstrates archived card-definition overlays, checkpoints and divergence detection, but adds server dependencies and its own replay contract. Its card-pin capture skips unavailable definitions, and failed pin decoding can warn and fall back to live cards; neither behavior is acceptable for an exact research replay. Its sparse fingerprint hashes selected counters, sizes and life totals and truncates the digest; its `EXACT` label does not certify full-state equality. Reuse those ideas with complete required pins and fail-visible mismatches, without assuming a presentation fingerprint is a complete research-state digest. Full state serialization and exact action/event replay in the existing JVM runner are the smaller initial implementation. Replay is debugging/verification of the same attempt, never an independent game. A separate policy replay can additionally verify the same observation and independent policy RNG select the same action.

The omniscient record is permitted for offline audit, separated from the live pilot. It must not expose future evaluation material before the protocol permits publication. Ferocity-role diagnostics should index actual actions, costs, zone changes, damage and resolved triggers. A successful sequence, material estimate, or board wipe is not a terminal outcome and cannot substitute for one.

## Completion order and stopping boundary

The bounded implementation queue is: (1) repair and qualify the established LKI failures and selected mechanics; (2) fill the fixed missing canonical pool, prioritizing preboard; (3) implement the strict observation/choice/replay skeleton; (4) resolve X-domain and trigger-order gaps with deterministic tests; (5) qualify each actual pilot and hidden-information invariance within D1's existing budget; (6) freeze the exact stage admission receipt and cap/seed procedure; then (7) consume only the already allocated D2 games.

A first valid D2 sub-batch can use a qualified treatment/comparator against a qualified gauntlet opponent while remaining pairwise rows are pending, if the stage's admission scope explicitly permits it. It consumes that list/opponent's original eight-game allocation; it creates no extra smoke budget and cannot rank the incomplete five-opponent sample. The Red main deck has no statically missing identity, but still needs full current-mode and pilot qualification. A3 is a plausible implementation starting point because Ferocity is its sole statically missing identity; this is an engineering observation, not a deck-selection outcome or a reduction of other families' opportunities.

No gameplay was admitted by this audit. If information invariance, a mandatory choice, canonical identity, replay or mechanics qualification cannot be made valid within the project, retain the partial work and report a technical suspension. Do not classify an unimplemented Rats, Faeries or recovery engine as a losing deck. Sanctioned Pauper claims still require the reconstructed project's fresh post-release check; verified-text prerelease development separately requires complete applicable mechanical and pilot admission.

## Source evidence appendix

The following hashes identify actual inspected bytes, independently of branch movement. They are source evidence, not build or gameplay receipts. A relevant change requires reassessment before reuse.
Inspected checkout HEAD at finalization: `3b25dc37012380311c473eed8a5b2f2a80f3be5d`. The 65-file evidence-set digest is `c3d0eb18fe9378f4c6f5043181a190dd26feeb06c16f415af11e66eb90fc4150`, computed as SHA-256 of sorted `path TAB sha256` records joined by LF with no trailing LF. Files are identified individually below; this digest does not claim to pin every engine dependency.

| Inspected source path | SHA-256 |
|---|---|
| `.github/workflows/izzet-snapshot-rebless.yml` | `1cc057890aaff4c8e679c22cf3c2103ac97dbcf5d754b19111fdd035450ac04a` |
| `ai/src/main/kotlin/com/wingedsheep/ai/engine/AIPlayer.kt` | `8c451b1dc412951766971a05630fb51e77913db16405b8013c6e7246f97d8bc1` |
| `ai/src/main/kotlin/com/wingedsheep/ai/engine/AiProfile.kt` | `e17daa32352d5a297f92869d5eaea8e777a4ce23249241db2c9f704a8ae0a36a` |
| `ai/src/main/kotlin/com/wingedsheep/ai/engine/DecisionResponder.kt` | `5b9901e1a39b6de6aaccb069fb8d942cf6ae4b6b86b9b9592ca0564c93305223` |
| `ai/src/main/kotlin/com/wingedsheep/ai/engine/EngineAiPlayerController.kt` | `226dedf18931bf88193ada4ea82f026305d1cf9ab7f80b8fadc93a731f43800a` |
| `ai/src/main/kotlin/com/wingedsheep/ai/engine/PestMonsterTronPolicy.kt` | `8cc6db916f50233331c72a9e603c8b887365fc625776d5a429ed9fc6ec63f513` |
| `ai/src/main/kotlin/com/wingedsheep/ai/engine/Strategist.kt` | `4485697db8e281f50214280b2979857f1a87e10ccc5afc861ac467412545a7b6` |
| `ai/src/main/kotlin/com/wingedsheep/ai/engine/advisor/CardAdvisor.kt` | `757c0183ec3289889cd4b875d022f65f8229545ad6c4b8ddcc0615ef9f3e6f93` |
| `ai/src/main/kotlin/com/wingedsheep/ai/engine/advisor/modules/PestMonsterTronAdvisorModule.kt` | `21d60a04da428312ede8aa05f29d16f0b7a3baa0a4a077fe47b9455857341912` |
| `ai/src/main/kotlin/com/wingedsheep/ai/engine/budget/DecisionBudget.kt` | `88972ae5230147fa324af74869d01d49c082fcfb0193e9ad92881af093f68958` |
| `ai/src/main/kotlin/com/wingedsheep/ai/engine/hidden/Determinizer.kt` | `fec6650ff57dc52a14f68f675df6b15d028136539949f0f4f7e1c81db73748c8` |
| `ai/src/main/kotlin/com/wingedsheep/ai/engine/hidden/OpponentModel.kt` | `2628d6cd79a316d22448e3c4e916e1dd9e0d9fae31172d61202a74754d2089f6` |
| `ai/src/test/kotlin/com/wingedsheep/ai/arena/PestMonsterTronPolicyAuditTest.kt` | `6369feeb0ecfdf76a08b7e7960b16431aed8f86250c822eb445813fbdc8fe2f9` |
| `ai/src/test/kotlin/com/wingedsheep/ai/arena/TableGameRunner.kt` | `388c9a3879699de63a1bf899a689c5d46ed434429f89d6a8c8aa98bf0b856e2e` |
| `ai/src/test/kotlin/com/wingedsheep/ai/engine/GrixisAffinityAgentDecisionTest.kt` | `00115a3e4330d807009f3da7d3a83fc04edc2b455a558ed732a5a40bb436f9f6` |
| `ai/src/test/kotlin/com/wingedsheep/ai/engine/PestControlMonoRedMadnessMulliganPolicyTest.kt` | `b980acb78df09e44fa2a5ab4dd982c3539cf33b42f33d0eefbb5885cce51cf99` |
| `experiments/manual-transmission/development/magda_policy_development.py` | `497f0648c4974a698d5d91374095e3129106a68b8ba1ccd0b94e1d05772a2e71` |
| `ferocity-recycling/BUILD_AND_ENGINE_AUDIT.md` | `c88ab016d6b5915480096803f6b6fd5ae378fee943268573e546d1067415b311` |
| `ferocity-recycling/protocols/ACTIVE_CONTRACT.json` | `8d0551bb3fc5e4a9492540412fec17284bb8311d3501d0baf014da3969cc15d4` |
| `ferocity-recycling/protocols/RECONCILIATION_AMENDMENT.md` | `2829d4623e142cdf427339b4e19f8bc8f96747410eeccb1abe1287986b35458f` |
| `ferocity-recycling/protocols/RESEARCH_PROTOCOL.md` | `ab663b6b5f3d4761469bb2a9fcfd73a0bffd74efd9bfdb80b2de94640b59cee5` |
| `ferocity-recycling/sources/card-coverage.json` | `f880b11b9ea9fa8c98c6c5aaf6e449b96c9b17ad386812dfc3b15ebf99d93772` |
| `game-server/src/main/kotlin/com/wingedsheep/gameserver/replay/CompactReplay.kt` | `9e0b357ce7b5dd3954721a30c2fb9ae371985310b9c0742cc46c32b7a1fa4518` |
| `game-server/src/main/kotlin/com/wingedsheep/gameserver/replay/ReplayCardPin.kt` | `986991eae336a5ffe1a9a1a15f655d1f4b9dfc9053ee2d0473e6ea10770705a9` |
| `game-server/src/main/kotlin/com/wingedsheep/gameserver/replay/ReplayCodec.kt` | `95381114a518db3940dc56cec196407417cf05c35f709c1ff5f2537f1a3d22a6` |
| `game-server/src/main/kotlin/com/wingedsheep/gameserver/replay/ReplayFingerprint.kt` | `d370f9670a33171409d6f148ddd5308430265c7cc64bd19b287ddaeead3a5c78` |
| `game-server/src/main/kotlin/com/wingedsheep/gameserver/replay/ReplayReconstructor.kt` | `ba451aefee02ba6496893b8c5f3d0d76df67c671aba72c1f6261592698b5b8fe` |
| `gym/build.gradle.kts` | `8ddb53991ef7fd6237a484400c869331a1828f22066a51aa4747a93a73ec973c` |
| `gym/src/main/kotlin/com/wingedsheep/gym/ExactlyOneSubmissionResult.kt` | `c8a7066301708ddf5c8080df041757784225f1ab8b8a8dac52de7a35dca218eb` |
| `gym/src/main/kotlin/com/wingedsheep/gym/GameEnvironment.kt` | `8101b0bc53778f9790b7c7f034ee2e0df20294b6d96e09c2b6721f8afc9be4de` |
| `gym/src/main/kotlin/com/wingedsheep/gym/GameGymEnv.kt` | `3abbfdfd31689bfcc75d6040412d608f545d7798e75f5652a99267d294968e5d` |
| `gym/src/main/kotlin/com/wingedsheep/gym/contract/ActionParams.kt` | `73395a970e7826b2abf17a48e3bf15da12c2c4dfbd8af7a5767dc09f2ef99757` |
| `gym/src/main/kotlin/com/wingedsheep/gym/contract/ActionRegistry.kt` | `0c36d0236023b4898fb670ba458e4e0f5d9cfe65540d7ea93023cb6503da61fb` |
| `gym/src/main/kotlin/com/wingedsheep/gym/contract/ObservationBuilder.kt` | `0a0f782d3608e6b507a88a31a8446ef145254834b25b1a627199aa7f0011d409` |
| `gym/src/main/kotlin/com/wingedsheep/gym/contract/StateDigest.kt` | `d9b34f12124b96c72ceda94b26af9d1ddad3257a200d9fbe5552afa877dd66f9` |
| `gym/src/main/kotlin/com/wingedsheep/gym/contract/TrainingObservation.kt` | `95feb4bd3053572ad1064a1b4e3202152b631e13fbb7b86b93ba4d8087818e84` |
| `gym/src/main/kotlin/com/wingedsheep/gym/matchup/PestControlPreboardContract.kt` | `2ef7f1a8712dfa7600d7100e96463bac2a86f61969ac91c6a09bf8bda60192d9` |
| `gym/src/main/kotlin/com/wingedsheep/gym/matchup/PestControlPreboardProductionDriver.kt` | `95c5294fa06485787c837efe7875e6cfdb5358d6f3844b54d6d26a7fb6052565` |
| `gym/src/main/kotlin/com/wingedsheep/gym/matchup/PestControlTierOneGrixisProductionDriver.kt` | `559bbd4fac2880a478a8c17c3ed4a6005af13edbad54b5995b1414494a7e10f3` |
| `gym/src/main/kotlin/com/wingedsheep/gym/matchup/PestControlTierOneMonoBlueTerrorProductionDriver.kt` | `d57e28195518a4519be1e3b0d0817b5727bbefe9303e78562920ca63ab8c74e5` |
| `gym/src/main/kotlin/com/wingedsheep/gym/matchup/PestControlTierOneMonsterTronAdmission.kt` | `5356f73ff61cafa87f809ab0c20cc2f2f2cf9b8dd50c89761d2d05b5f11b494c` |
| `gym/src/main/kotlin/com/wingedsheep/gym/matchup/PestControlTierOneMonsterTronOperationalStack.kt` | `af78e3f08b8729f5271ee8ebe2190a89614951dc72183264f9e2987808dabf26` |
| `gym/src/main/kotlin/com/wingedsheep/gym/service/DeckResolver.kt` | `12007e92aa4546097b82650e83b8a25c7b7c9f4d9e7ce3ca31d89eb8ae963279` |
| `gym/src/main/kotlin/com/wingedsheep/gym/service/EnvConfig.kt` | `4711b6de150fac058408566517907191b1fd9a2a8ee26e5c90210cf2c4f3152d` |
| `gym/src/main/kotlin/com/wingedsheep/gym/service/MultiEnvService.kt` | `706858452ef25f07a08749ef444a683426ff358572b4acb31885e3564d6c93a4` |
| `gym/src/main/kotlin/com/wingedsheep/gym/service/SnapshotCodec.kt` | `feb63715b477be3b554f530fb104cbf7bf59e9564138f715e42281c8e481ae42` |
| `gym/src/test/kotlin/com/wingedsheep/gym/PestControlTierOneGrixisOfficialExecutionRunnerTest.kt` | `a880970455bde2e2b95c7325377d3aa7d7d535b5d9506a64ea03211b54079a75` |
| `mtg-sdk/src/main/kotlin/com/wingedsheep/sdk/model/GameRng.kt` | `92e5e38957c61e384f29c81bf07df4d80e2ca4730686852f71a17b6d14d36c53` |
| `rules-engine/src/main/kotlin/com/wingedsheep/engine/core/ActionProcessor.kt` | `971246aa1958d74bc8ed3a37ba2016fbd869f4293052d4b515991fafca948822` |
| `rules-engine/src/main/kotlin/com/wingedsheep/engine/core/GameAction.kt` | `4a7aba591b71d5d5bf57c6039cafb0925ce85a0976fee15d3b42eb15c928a0b1` |
| `rules-engine/src/main/kotlin/com/wingedsheep/engine/core/GameInitializer.kt` | `4b9a42f9e7de7400ef12bf6598981ca741c05644113844baa43a4e2e90ecbb71` |
| `rules-engine/src/main/kotlin/com/wingedsheep/engine/event/TriggerProcessor.kt` | `abddff4eeb294092ce0f3b5b8f840818233dfc706c1d02a4e0864dea14a41832` |
| `rules-engine/src/main/kotlin/com/wingedsheep/engine/handlers/actions/ability/ActivateAbilityHandler.kt` | `140f0c6231819e9aea7d089cc06712983b5dcae14097e20083e7374bfd6b09fe` |
| `rules-engine/src/main/kotlin/com/wingedsheep/engine/hidden/HiddenWorldMaterializer.kt` | `332a8024eda9c5b8d06bb3f02338e71faee97819165f5727076272bdf49b706b` |
| `rules-engine/src/main/kotlin/com/wingedsheep/engine/legalactions/LegalAction.kt` | `32aa41fbb25295aaf779480110f23398de4dc34515286499a7bfae06a2ffb9fc` |
| `rules-engine/src/main/kotlin/com/wingedsheep/engine/legalactions/LegalActionEnumerator.kt` | `9ee0ac7cb45e92b06780f0a70ed0077c52ef51237b4f40bf88b325c51e6b44a2` |
| `rules-engine/src/main/kotlin/com/wingedsheep/engine/legalactions/enumerators/ActivatedAbilityEnumerator.kt` | `2be0fd979b3701eeefa037569bd3bd69626aeb807485a73d98c812a2edf1a360` |
| `rules-engine/src/main/kotlin/com/wingedsheep/engine/legalactions/enumerators/ManaAbilityEnumerator.kt` | `fd173f6e9b56f0824484186386e8d7b4e74e0a411158f131b5011e2682dbf8b1` |
| `rules-engine/src/main/kotlin/com/wingedsheep/engine/legalactions/utils/CostEnumerationUtils.kt` | `9767c8aed284e17a83e8efe04b46cbc5e7f46c01531be353d1d5046aa017a65e` |
| `rules-engine/src/main/kotlin/com/wingedsheep/engine/mechanics/CastPriorityProcessor.kt` | `1aec94b051c983deacf4a7a22f6d94aa40336e2d2b158096ed6c2305d20a8d35` |
| `rules-engine/src/main/kotlin/com/wingedsheep/engine/registry/CardRegistry.kt` | `026f8cc625790db6b3bec32c48d2ed4d9b0ac27823969a650871a69b9969bd07` |
| `rules-engine/src/main/kotlin/com/wingedsheep/engine/state/GameState.kt` | `027dc178e12668e447eb9cea6f6b8d95305c74683ff135dad41be1b4417b864f` |
| `rules-engine/src/main/kotlin/com/wingedsheep/engine/state/GameStateSerializer.kt` | `5b759df2964dd6ea90a096c64ff67fbf1959e236ddf29f5f5bcff56c62d08e6c` |
| `rules-engine/src/main/kotlin/com/wingedsheep/engine/state/LegacyGameStateSerializer.kt` | `6fcffcebf0cc29316343271828a69e664dbed9cfecb0b90ddfe0cc10f0d36e57` |
| `rules-engine/src/main/kotlin/com/wingedsheep/engine/view/Visibility.kt` | `5ca2b529577604549b95dbcd0ce58a9ed093aba43dfe9b40d7a85b76d68d4f14` |
