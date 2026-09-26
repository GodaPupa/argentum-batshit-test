# Research checkpoint — 2026-09-26 13:24 UTC

**No deck-strength conclusion is supported yet.** The strongest Ferocity configuration, Ferocity's causal contribution and competitiveness remain unresolved. All randomized stages remain0; all18 initial sixties are unchanged. [CURRENT_STATUS.md](CURRENT_STATUS.md) records exact current source and game counts.

The combined fixed selection has now actually executed458 cases:441 passed and17 failed. Fresh ordinary content342 and the complete SDK531 passed on their recorded source. Fifteen failures concern explicit fixture flow/setup; one exposes a real combat-priority defect; one exposes unavailable JVM process metadata in this environment. Every failure remains recorded. The reviewed process04 successor subsequently passed34 fresh Admission/Journal cases. The unchanged-engine combat baseline executed13 cases: eleven failed priority/trigger assertions and two stopped at serializer fixture configuration. Those two cases require a separate fixture correction before their later assertions can be interpreted. The earlier cache-lock attempt executed zero cases. These findings qualify infrastructure and diagnose interactions; they do not select a deck or estimate a win rate.

The clean-build repair and reviewed golden changes have actual subsequent ordinary comparison evidence. Runtime/fixture successors remain separately versioned and must pass fresh gates. Initial Red gameplay policy and all decklists remain unchanged. A working effect, legal return or successful board wipe is still not a game win.

The earlier narrative below is historical and retains the search rationale and D0 arithmetic. Its checkpoint-specific pending statements are superseded by the current record; original complete text is archived at `history/checkpoint-8102098e/RESEARCH_REPORT.md`.

---

# Ferocity Recycling — research report

## Present conclusion

The project has begun a bounded search. There is no accepted interactive performance evidence yet, so questions A (strongest configuration), B (Ferocity's contribution) and C (competitiveness) are unresolved. No negative deck verdict follows from an infrastructure or mechanics blocker.

## Why these comparisons matter

The artifact family tests Ferocity as a deathtouch sweeper enabler and creature-value protector in a shell that already has competitive alternatives. Toxin Analysis supplies lifelink and a Clue; Not Dead After All supplies a cheaper return-on-death effect and a different resource outcome. A no-Ferocity build must spend its freed slots efficiently rather than retain cards included solely to recover Ferocity.

The Rats family exposes a distinct cost: damage to its controller remains real. Returning Rats tapped does not refund life, restore the Aura or preserve deathtouch. Black activation costs also impose a stricter mana requirement than a superficial two-color source count suggests.

The white family tests actual recovery of the Aura. Its Auramancer line restores access to Ferocity, but each repetition consumes fresh casting mana and a sacrifice opportunity. It is a finite-resource value engine unless an explicit additional mana/resource engine closes the ledger. Recovery of a component is not an infinite combo.

The exact rules sequences, resources and opponent windows are in `SYNERGY_MAP.md`; verified source records are in `sources/`.

## Evidence hierarchy

Deterministic arithmetic checks list structure and opening inventory. Fixed engine scenarios check rules and policy behavior. Interactive development explores configurations. Frozen evaluation and independent confirmation test the three research questions. These are different evidence classes and their counts are reported separately.

The complete finite budgets, comparative thresholds, stage separation and stop rules were declared before randomized outcomes in `protocols/RESEARCH_PROTOCOL.md`. Changes to a list, policy or runtime require a visible version boundary. Source-backed tournament lists establish relevant benchmarks, not a result for this new deck.

## Accepted outputs so far

Repository reconstruction, pre-outcome family/search design, source-archival work, prototype construction and executable exact-inventory arithmetic. Actual build/scenario receipts and resulting findings are added only after their logs and outcomes have been inspected. `CURRENT_STATUS.md` records the current counts and continuation boundary.

## Final deliverable boundary

The strongest supported 60/15, import list, practical primer, mulligans and sideboard plans will be provided when the corresponding evidence exists. Published prototype 60s and prospective sideboards are clearly marked as development inputs. This report does not substitute them for a tested final list.

## D0 exact opening-inventory evidence

The initial file freeze is `ef75f5cec859dcc9f765de0feacb7cb7696037f4c7035d4b68b6bdfccee1c87d`. The reproducible JSON is `evidence/opening-inventory-v0.1.json`, SHA-256 `70398c98cfde2fac725b59a80626914e8b6422294e76d463b859c9a8e06e4fa6`. These probabilities enumerate uniformly shuffled opening sevens with no mulligans; they use no random seeds and are not games.

| Prototype | Ferocity count | At least one Ferocity | At least two Ferocities | Ferocity present, no creature card | Two to four lands |
|---|---:|---:|---:|---:|---:|
| A1 Rakdos affinity | 2 | 22.15% | 1.19% | 5.96% | 71.74% |
| A2 Rakdos flying clock | 3 | 31.54% | 3.35% | 7.31% | 71.74% |
| A3 Grixis Strix/Thoughtcast | 4 | 39.95% | 6.32% | 7.94% | 73.79% |
| B1 mostly black Rats | 1 | 11.67% | 0.00% | 4.66% | 73.79% |
| B2 Golgari selection | 2 | 22.15% | 1.19% | 7.74% | 73.79% |
| B3 Golgari recovery | 3 | 31.54% | 3.35% | 10.91% | 73.79% |
| C1 light Orzhov recovery | 2 | 22.15% | 1.19% | 3.41% | 73.79% |
| C2 middle Orzhov recovery | 3 | 31.54% | 3.35% | 4.76% | 73.79% |
| C3 higher Orzhov recovery | 4 | 39.95% | 6.32% | 5.93% | 73.79% |

The JSON also includes no-Ferocity alternatives, source inventories and joint sweeper presence. Ferocity without a creature in the opening hand does not prove it remains stranded after subsequent draws; two Ferocities do not automatically make a hand unplayable. Conversely, having a creature does not prove it is castable or a worthwhile Aura target. Land inventories do not establish colored-mana timing, especially Grixis's tapped artifact lands and Golgari's black-only Rats activation. No candidate advances or is eliminated from this arithmetic.

## Prospective integration boundary

Concurrent remote initialization at `3afd83c8` is preserved in history and reconciled explicitly by `protocols/RECONCILIATION_AMENDMENT.md`. It changes no D0 input or output. The effective refinement ceiling is 240 games and the full preboard ceiling is 6,960; sideboard development/freeze precedes E. Those provisions supersede the corresponding draft rows in the archived detailed protocol. No randomized outcomes informed this reconciliation.

## Real-engine baseline and defect boundary

The provisioned real engine compiled the SDK, rules engine, complete canonical card corpus and scenario sources. Stable-source fixtures executed 41 distinct cases: Ferocity 22/24 passed, Crypt Rats 8/8 passed, Toxin Analysis 8/9 passed. Every invocation recorded zero errors/skips and unchanged compiled inputs. The earlier drifted 24-case Ferocity attempt remains preserved debugging evidence. No randomized game has run.

The three failures demonstrate lost departed-source deathtouch/lifelink on queued Shaman damage. Live-source gain/loss of deathtouch passed, excluding an activation-time snapshot as a valid shortcut. A narrow generic repair is isolated for review and regression testing. These failures block interactive admission and give no evidence that Ferocity is weak or strong. `BUILD_AND_ENGINE_AUDIT.md`, `MECHANICAL_QUALIFICATION.md` and the raw receipts under `evidence/build/` specify exact source, dependencies, assertions and replay commands.

## Publication and CI setup evidence

The complete reviewed baseline tree was published at `901729f2` through existing PR #173 with the original initialization as parent; the remote tree was independently matched to local reviewed bytes. Two automatically triggered qualification jobs failed before assertions because the Java setup action rejected the exact version's SemVer notation. Their logs are retained; this adds zero mechanical or gameplay outcomes. The workflow correction uses the same verified official archive as the working local environment and removes duplicate push/PR triggering. A successful installer or green CI alone will not resolve the three deckbuilding questions.

## Support publication and continuing qualification

The qualification checkpoint at `14ec21b8` preserved all concurrent research history and now has two successful workflow statuses (`36226502281`, `36226502312`). This establishes that the pinned Java installer works remotely; exact cases and source scope are recorded separately before acceptance. No randomized game has run.

The first ten missing canonical support cards are now frozen with 58 authored cases, eight printing rows and archived fresh source verification. Their runtime gates remain pending on this integration. The original eight Rats interaction bodies now use the canonical card and a distinct engine-level class name; their previous fixture pass is not inherited.

Additional deterministic execution has exposed generic faults that could bias deck comparison: wrong restricted-X legal-action ceilings, token cleanup before priority, optional targeted-trigger timing, and insufficient public combat information for a pilot. The source corrections, failed runs and requalification boundaries are explicit. These findings concern the simulation environment; they do not favor or disfavor any architecture. The deck lists and experimental budgets remain frozen, and all development/evaluation/confirmation game counts remain zero.

## Evidence checkpoint — 2026-09-26 09:20 UTC

The preceding support-publication section describes its earlier boundary. The current source-specific results, including failed public CI at2e7e7865, are superseded by `CURRENT_STATUS.md` and the newly published compact evidence archives. Restricted-X18 and actor-observation28 now have passing targeted gates; full LKI targeted73 passed on its earlier exact source, while its subsequent broad4,379-case run exposed14 failures and is not accepted as a green broad gate. Fresh nested-payment fixes and historical fixture corrections are being qualified, with every failed attempt retained.

All18 prototype/comparator lists and the bounded search protocol remain unchanged. Both first-cell pilots now have24 fixed qualification scenarios authored; none has executed at this checkpoint. Static review has corrected draft cost assumptions against actual card definitions before the initial policy freeze. This is policy construction, not evidence that one deck wins more.

The research questions still require real interactive outcomes. No development, evaluation, confirmation or postboard sample has been consumed. A technically successful mechanism or pilot fixture cannot support a Ferocity advantage, competitive win rate, final archetype promotion or tournament claim.

## Evidence checkpoint — 2026-09-26 10:20 UTC

The new current qualification matrix is in `MECHANICAL_QUALIFICATION.md`; the prior baseline/status is preserved under `history/checkpoint-53c68e95/`. The departed-source and nested-payment targeted gates now pass85 complementary fixed cases, and paid-flashback support passes69 complementary cases. These improvements have exposed further broad-suite fixture and runtime defects, whose failures and exact version boundaries remain preserved. The corrected collector15 fresh count is9,872 cases with40 failures; stale695 XML and up-to-date3,696 engine cases are excluded. This correction changes an infrastructure evidence count, not a randomized deck result.

The artifact pilot's first24 cases produced22 passes and two setup failures. Its policy remains unchanged while the fixture issues are repaired. Real Gift testing also exposed missing player shroud/hexproof checks on resolution; that failure is receiving a runtime correction, not relaxed card assertions. The project has not spent any of its720 initial interactive development games or240 refinement games. A small16-game first cell is being made executable under the exact-source admission, journal, replay and watchdog contract.

A, B and C remain unanswered because they require interactive comparisons. No card/package changes, candidate promotion, no-Ferocity handicap, competitive claim or new-archetype label follows from these mechanical results. The published60s remain prototypes. The finite search and the final independent evaluation/confirmation boundaries are unchanged.
