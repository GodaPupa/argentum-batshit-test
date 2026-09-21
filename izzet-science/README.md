# Izzet Science?! — PDH Experimental Lab

Commander: Izzet Guildmage

## Objective
Build a high-decision-density Izzet Pauper EDH combo-control deck that feels mechanically distinct from blink/ETB, graveyard, combat, and artifact-Tron projects.

## v0.1 hypotheses
- 36 lands is the initial control.
- Blue-heavy mana base to support High Tide.
- ~10 nonland mana sources so Dramatic Reversal can function as a real engine.
- Primary deterministic kill: Izzet Guildmage + Lava Spike + Desperate Ritual.
- Secondary engine: Izzet Guildmage + Dramatic Reversal + sufficient nonland mana production.
- High Tide/Snap/untap package remains experimental.
- Avoid Peregrine Drake/Ghostly Flicker as a primary package to keep this deck distinct from Lilysplash.

## Design rule
Except for compact deterministic combo necessities, cards should have useful standalone roles. Avoid dead-card combo soup.

## Core candidates
### Arcane / Ritual
- Desperate Ritual
- Lava Spike
- Ideas Unbound
- Eye of Nowhere

### Mana engine
- Dramatic Reversal
- High Tide
- Snap
- Hidden Strings

### Search
- Muddle the Mixture
- Merchant Scroll
- Dizzy Spell
- Drift of Phantasms

### Selection
- Ponder
- Preordain
- Brainstorm
- Consider
- Opt
- Impulse
- Treasure Cruise

### Mana infrastructure
- Everflowing Chalice
- Fellwar Stone
- Star Compass
- Sky Diamond
- Mind Stone
- Network Terminal
- Bonder's Ornament
- Ur-Golem's Eye
- Sisay's Ring

## Test plan
1. Produce exact legal 100-card v0.1.
2. Compare 35/36/37 land configurations.
3. Compare nonland-mana counts around the 10-source control.
4. Measure opening-hand keepability and colored-source availability.
5. Track effective mana turns 3–6.
6. Track dead combo-piece frequency and tutor connectivity.
7. Track earliest and median assembled win opportunities.
8. Repeat with Guildmage effectively unavailable to measure commander independence.
9. Preserve v0.1 before testing challengers.

## Status

Accepted control: `izzet-science/v0.7-control.md`, SHA256
`726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`.
It preserves the v0.6 shell and replaces its 26 ordinary basics with Snow-Covered
equivalents after a paired 100,000-game confirmation.

Latest gate: v0.8-A Mizzium Skin (`-1 Turn Aside, +1 Mizzium Skin`) was rejected as
inadmissible after its sole pilot exposed outcomes but failed before provenance
artifact completion and revealed two ability-event modeling defects. It cannot be
rerun or rehabilitated. A subsequent seed-free deterministic gate proved that a
legal primary-combo launch can distribute 29 retargeted copies plus the original
across three 30-life opponents. This adds no sampled performance claim and changes
no cards. v0.7 remains the accepted control.

Current gate: v0.9 Phase 0 validated seed-free commander-independent readiness
semantics for Murmuring Mystic, Rolling Thunder, Kaervek's Torch, and buyback
Capsize. No sampled baseline, challenger, or backup-plan win-rate claim is yet
authorized.

Phase 1 now freezes passive T1–T10 readiness telemetry for one control-only
10,000-game pilot. Run #181 passed with complete audited artifacts. At T10, Mystic
was castable in 16.00% of trajectories, Rolling Thunder had positive X in 16.85%,
Kaervek's Torch in 16.42%, and Capsize had buyback mana in 15.39%. Conditional on
being present, all four were mana-ready at least 90.96% of the time, making access
the clearer next bottleneck. This is readiness evidence, not a backup-plan win rate;
v0.7 remains the accepted control.

Phase 2 audits the accepted control's existing tutor connectivity to those backup
cards without changing tutor-use policy. It is a seed-free legal-semantics gate:
Dizzy Spell can find either X-spell, Drift of Phantasms and Merchant Scroll can find
Capsize, and no current tutor can find Murmuring Mystic. The seed-free validator
passed with zero policy changes, samples, seeds, or outcome claims; no card change
is authorized by this audit.

Phase 3 freezes passive `targetable`, `payable`, and primary-combo-`uncontested`
tutor-opportunity fields. It is an instrumentation preflight only: tutors remain
unspent and the existing policy remains unchanged. Deterministic trajectory
equivalence and aggregate invariants passed with zero samples or seeds. No sampled
pilot or fresh seed is yet authorized.

Phase 4 freezes a single 10,000-game control-only pilot contract for both readiness
and passive tutor-opportunity telemetry. Run #184 passed with complete independently
verified artifacts. At T10, payable tutor opportunity was 3.05% for Rolling Thunder,
2.88% for Kaervek's Torch, and 24.34% for Capsize; Capsize's entire opportunity was
primary-combo-uncontested. This remains passive evidence, not executed acquisition.
Seed `0x1A22E700F` is consumed and no rerun is authorized. v0.7 remains the control.

Phase 5 freezes executable but dormant Capsize tutor semantics. The existing
primary-combo tutor action has global precedence; otherwise Merchant Scroll is
preferred over Drift of Phantasms when Capsize is in the library and exact ready
mana can pay the search. Deterministic execution, failure atomicity, and adjacent
validators passed with no sampled games or seeds. The default simulator remains
unchanged, and v0.7 remains the accepted control.

Phase 6 adds an explicit opt-in activation path and four fail-closed Capsize tutor
event fields. A fixed ordered-deck fixture acquires Capsize exactly once on turn two;
the disabled path remains exactly identical to the default. No sampling is yet
authorized: the batch simulator's shared random stream could let an extra policy
shuffle perturb later games. Per-game paired RNG isolation is the next required
seed-free gate. v0.7 remains the accepted control.

Phase 7 resolves that coupling with SHA-256 domain-and-counter child seeds. Separate
control and policy RNGs begin every game identically; policy-only search randomness
can diverge inside that game but cannot affect any later pair. Frozen derivation
vectors, replay, isolation, and invalid-input regressions passed without consuming
the paired iterator. No seed or pilot is authorized until paired estimands and the
artifact contract are frozen. v0.7 remains the accepted control.

Phase 8 freezes matched-pair estimands for Capsize access, buyback readiness, primary
assembly/lethal timing, and commander deployment. Its strict JSON contract preserves
both/control-only/policy-only/neither cells and independently recomputes every delta.
A synthetic ten-turn ledger passed while twelve malformed outputs were rejected,
including duplicate-key and non-finite JSON attacks. No paired games or experimental
seeds were consumed. Execution identity, pilot size, seed, and artifact provenance
must be frozen next; v0.7 remains the accepted control.

Phase 9's sole paired pilot is inadmissible. GitHub Actions run `35548464487` passed
its exact checkout, identity checks, and complete seed-free preflight, then failed
closed during paired execution with `policy event identity violation`; manifest and
artifact steps were skipped and zero artifacts were uploaded. Static diagnosis found
that Capsize tutor selection used the exact mana solver while execution used an older
mutating payer that omits Izzet Signet and cannot model Prismatic Lens filtering or
Star Compass colors. The log does not identify which source state triggered first.
Seed `0x00000001A22E7010` is consumed and permanently retired; no rerun, replacement,
pooling, outcome claim, or promotion is allowed. The temporary workflow was removed,
the ordinary workflow restored, and v0.7 remains the accepted control. A future
pilot requires a seed-free unified-payment gate and a fresh frozen identity.

Phase 10 completes the seed-free payment recovery. Feasibility and mutating payment
now share one exact activation-state engine with deterministic tap witnesses.
End-to-end fixtures prove successful Capsize acquisition through Izzet Signet,
Prismatic Lens filtering, and Star Compass color production—the three source classes
the retired Phase-9 executor could mishandle. The failed runner now exits before
argument parsing or iterator construction, preventing accidental reuse of its
consumed seed. Adjacent seed-free semantic controls pass with zero sampled games or
consumed seeds. No pilot is yet authorized, no policy or card is promoted, and v0.7
remains the accepted control.

Phase 11 freezes a recovered 10,000-pair runner at SHA256
`2db5bdc6351836ffa3f5c76c98f9966b0a516fbeafc796eb9a2ae85f70932bbe`.
Repository and full-history search found fresh seed `0x00000001A22E7011` unused;
it is now assigned but unconsumed, while failed seed `0x00000001A22E7010` remains
retired. The exact four-file artifact contract rejected ten adversarial fixtures,
and the recovery preflight includes payment-parity fixtures plus old-runner
retirement. The experimental source is now frozen at commit
`cd93b7e427b4e8c1b67cbf23560770d2ad19b5bc`, tree
`d5b4ce36672181cdcb26b34644ca9dd56862ad4e`. The workflow is not yet armed and the
fresh seed remains unconsumed; no outcomes exist and v0.7 remains the control.

Phase 11's sole recovered run `35549404361` is also inadmissible. Exact checkout,
identity verification, payment recovery, and the complete seed-free preflight
passed; paired execution then failed closed on `more than one Capsize acquisition
in a game`. The contract's one-acquisition assumption is unsound because modeled
Brainstorm can return an acquired Capsize from hand to the library, permitting a
later reacquisition. The log did not preserve the failing trajectory, so no narrower
path is claimed. Seed `0x00000001A22E7011` is consumed and retired, the v2 runner is
guarded against reuse, zero artifacts were uploaded, the ordinary workflow is
restored, and no outcome or promotion is accepted. v0.7 remains the control.

Phase 12 replaces the invalid one-acquisition invariant with paired summary schema
`izzet-v09-capsize-paired-v2`. Cumulative tutor events may exceed the number of
trajectories, while a separate `ever tutored` field remains a per-game estimand.
Merchant Scroll and Drift event counts must still partition all events exactly. A
deterministic fixture now executes Merchant acquisition, Brainstorm put-back, and
Drift reacquisition in one trajectory; a two-event synthetic ledger passes and
thirteen malformed outputs fail closed. No samples or seeds were consumed, no new
pilot is authorized, and v0.7 remains the accepted control.

Phase 13 adds a cost-controlled invariant-only qualification before any third pilot.
It runs 1,024 complete matched pairs on existing public regression coordinate `1`,
emits no outcome fields, and interprets no effect sizes. Both retired runners, the
accepted control hash, full paired generation, shared payment engine, repeated tutor
events, aggregation, and strict v2 summary validation are exercised end to end. No
experimental seed is assigned or consumed and no pilot is authorized.

Phase 14 freezes the v3 10,000-pair runner at SHA256
`760ce035e6dc81fc001b2f080e87ebfda97be7444819a7a1e2fd7f3afb3eb5b3`.
Repository and full-history search found `0x00000001A22E7012` unused; it is assigned
but unconsumed. The run binds summary schema `izzet-v09-capsize-paired-v2` and
artifact schema `izzet-v09-capsize-paired-artifact-v2`; ten adversarial artifacts
were rejected. The experimental source is frozen at commit
`5a981b1f4a34b065af0630bf68113f7ac73e94b9`, tree
`777c2d4b0219871dbed04dfe4daa8a928cb0dace`. No workflow is armed, the seed remains
unconsumed, no outcomes exist, and v0.7 remains the control.

Phase 14 run `35551616504` passed the complete qualified workflow and independent
four-file artifact audit. At T10, the Capsize policy increased direct presence from
16.96% to 41.11% (+24.15 points) and buyback readiness from 15.11% to 37.50%
(+22.39 points). Primary lethal was 7.05% versus 7.12%, cumulative lethal 7.07%
versus 7.13%, and commander presence 95.02% versus 94.94%. T3 commander presence did
fall 2.05 points from early tutor spending, but the gap largely recovered by T5 and
did not reduce the modeled T10 lethal clock. The artifact is accepted and the tutor
policy is promoted for future stateful modeling; no card is promoted and the exact
v0.7 deck remains the accepted card control. Seed `0x00000001A22E7012` and the v3
runner are retired, and the ordinary workflow is restored.

Phase 15 begins the opponent-aware path with a seed-free Capsize resolution gate.
Twelve deterministic fixtures now cover target legality, exact normal and buyback
payment, Electromancer reduction, successful return and retention, countered and
target-illegal failures, explicit commander hand/command-zone branches, and the
three recovered mana-source classes. The gate deliberately assigns no opponent
deck, threat frequency, tempo value, survival rate, or win rate. No sample or seed
is consumed, no card changes, and v0.7 remains the exact accepted card control.

Phase 15 run `35552812250` passed all frozen identity checks, twelve interaction
fixture groups, adjacent semantic validators, manifest construction, and artifact
upload; ordinary CI run `35552816003` also passed. The deterministic infrastructure
is accepted without a performance claim. Phase 16 may define fixed-event paired
interaction estimands, but no sample or experimental seed is yet authorized.

Phase 16 freezes a strict paired fixed-event output contract. It separates one-shot
Capsize readiness, retained buyback, Guildmage self-rescue, and both opposing-
commander destination choices while preserving primary-lethal and commander-
deployment guardrails. Countered and target-illegal branches explicitly resolve
neither the bounce nor buyback. This is a seed-free schema and adversarial-audit
gate only; it authorizes no pilot, new seed, performance claim, or card change.

Phase 16 run `35553843335` passed the exact-source gate and all seventeen
adversarial summary rejections. The accepted contract observes the residual response
window only after the deterministic turn policy has spent mana. Zero samples ran,
zero experimental seeds were assigned or consumed, and no outcome was exposed. The
next authorized work is an invariant-only qualification on public regression
coordinate `1`; an official interactive pilot remains unauthorized.

Phase 17 defines that cost-controlled invariant qualification: 1,024 complete
matched pairs on public regression coordinate `1`, with the accepted tutor policy
active and all Phase-15 interaction fields enabled. The complete Phase-16 summary
is aggregated and validated only in memory. Durable output is restricted to
identity, execution counts, zero-outcome accounting, and disposition; no outcome
field, effect estimate, or performance interpretation is emitted. The gate assigns
and consumes no experimental seed and does not yet authorize an official pilot.

Phase 17 run `35554539657` passed exact-source verification, both adjacent controls,
and all 1,024 matched pairs. Its independently downloaded four-file artifact matched
GitHub's ZIP digest and every receipt hash; it contained zero serialized interaction
outcome assignments. Public regression coordinate `1` is not an experimental seed,
so zero experimental seeds were assigned or consumed. This accepts only the full-
path invariant qualification. The temporary workflow is removed, ordinary CI is
restored, the accepted tutor policy is unchanged, and v0.7 remains the card control.

Phase 18 freezes a single 10,000-pair fixed-event interaction pilot. Full-history
search found fresh master seed `0x00000001A22E7013` unused; it is assigned but
unconsumed. The runner binds the Phase-16 nine-metric summary and strict four-file
artifact schema, while eleven adversarial artifacts fail closed. No workflow is
armed, no paired iterator has been consumed, and no outcome exists. A separate
source-freeze record is required before the sole pilot can be authorized.

The Phase-18 experimental source is now frozen at commit
`9bc8b75cffc1fc8698bf135819c0465a0641bfbd`, tree
`9947c4b0bac55b7628ba8fa2d3bdedc34a84c461`. This provenance record changes no
frozen source. The workflow remains unarmed, seed `0x00000001A22E7013` remains
unconsumed, and no interaction outcome has been exposed.

Phase 18 run `35556914552` passed the complete exact-source workflow and independent
four-file artifact audit. At T10, the accepted policy raised one-shot hostile-
permanent response readiness from 15.55% to 39.50%, buyback-retained readiness from
13.64% to 34.19%, and Guildmage self-rescue from 14.98% to 37.47%. Primary lethal by
T10 was 7.20% versus 7.09%, and commander presence was 94.76% versus 94.69%. These
are fixed-event readiness results, not opponent-frequency, tempo, survival, or win-
rate evidence. Seed `0x00000001A22E7013` and its runner are retired, the temporary
workflow is removed, the accepted tutor policy is unchanged, and v0.7 remains the
exact card control.

Phase 19 freezes a seed-free Capsize response policy over four externally declared
event classes: imminent loss, lethal-combo Guildmage removal, next-main lock, and
tempo-only. It answers at most one legal target using stable emergency priority,
prefers buyback only when already affordable in the one-response window, and passes
on tempo-only events. Commander destination remains the owner's choice; countered
and illegal-at-resolution branches retain no buyback. This assigns no event
frequency, opponent policy, tempo value, survival rate, or win rate and authorizes
no sampled pilot.

Phase 19 run `35557828845` passed exact-source validation, the complete adjacent
semantic chain, all 84 exhaustive response states, and eight malformed-input
rejections. The independently downloaded five-file artifact matched GitHub's ZIP
digest and every receipt hash. The response policy is accepted as methodology, not
as proof of strategic optimality. Zero games ran and zero experimental seeds were
assigned or consumed. The temporary workflow is removed, and v0.7 remains the exact
card control.

Phase 20 replaces caller-supplied severity labels with a deterministic public-state
ledger. Stable event, policy, window, source, and target identities are bound to four
enumerated public facts; classification then follows the accepted Phase-19 priority.
The contract rejects mixed or contradictory records and preserves nonlethal
Guildmage events in the audit trail while omitting them from the frozen consumer,
which cannot encode that case as tempo-only. No opponent deck, frequency, hidden
information, sampling, seed, or outcome is introduced.

Phase 20 run `35558604980` passed the complete adjacent semantic chain, all 96
target/fact/legality combinations, and eighteen malformed-input rejections. Its
independently downloaded six-file artifact matched GitHub's ZIP digest and every
receipt hash after accounting for GitHub's stripped upload-directory prefix. The
ledger contract is accepted as methodology only. The temporary workflow is removed,
ordinary CI is restored, and v0.7 remains the exact card control.
