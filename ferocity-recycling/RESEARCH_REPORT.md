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
