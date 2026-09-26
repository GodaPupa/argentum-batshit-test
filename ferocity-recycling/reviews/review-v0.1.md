# Independent review — initial Ferocity Recycling checkpoint

Review date: 2026-09-26 UTC. Reviewer: delegated `toxin_and_review` agent, independent of the protocol, deck construction, arithmetic runner and build runner authors. Base repository source: `d0c78bb4cca79b7402ba65bd62b5cb621230a054`.

## Disposition and scope

The reviewed material supports publication as **unqualified research preparation and deterministic arithmetic**, with the qualifications below. It does not admit interactive gameplay, demonstrate card-engine compatibility, select a winning deck or support a competitive claim. A technical failure must remain a technical failure.

Reviewed: `protocols/RESEARCH_PROTOCOL.md`; `tools/project.py`; the eighteen candidate/comparator JSON lists, their import text and `decks/DECK_MANIFEST.json`; role metadata against archived card records; `tools/validate_build.py`; the project workflow; and publication scope. The reviewer also independently inspected the Ferocity rules fixture and authored `ToxinAnalysisScenarioTest.kt`. Authorship of the Toxin tests is disclosed: their assertions are not an independent review of themselves, and no compiled passing result is claimed here.

## Verification actually executed

- `python -m unittest discover -s ferocity-recycling/tests -v`: three deterministic combinatorics tests passed. These compare hypergeometric calculations with exhaustively enumerated small populations; they are not games.
- `python ferocity-recycling/tools/project.py validate`: all eighteen initial lists passed the current structural validator.
- An independent reader recomputed all eighteen sorted count/name content hashes, checked the corresponding manifest entries, and compared each import text against the JSON quantities. All matched.
- The same reader checked creature and land tags against archived card type records. The only apparent type discrepancy was resolved by examining the two card faces: Tithing Blade correctly uses its front-face `Artifact` type, while its combined source record says `Artifact // Artifact`.
- After the pre-outcome Grixis A3, Dispatch and Cut Down package changes, the three arithmetic tests and eighteen-list validator were run again successfully. All current source-front-face creature/land tags were independently checked again with no discrepancies.
- Six deliberate malformed inputs were tested in isolated temporary copies, leaving the project files untouched: duplicate sideboard rows, a wrong canonical hash, an incorrect import list, altered manifest identity, an undeclared family and overlapping Ferocity/creature role tags. All six were rejected by the repaired guards. The overlap check isolated the arithmetic guard from gameplay admission; no production freeze was created or bypassed.
- A bounded publication scan examined 333 project, fixture and workflow files, approximately 8.64 MB at that snapshot, for private-key blocks, GitHub personal-access tokens, API-secret patterns, AWS access keys and literal bearer credentials. No pattern matches were found. This is a scoped inspection, not a guarantee that every imaginable secret format is detectable.
- The seed ledger contained no allocations and reported zero generated experimental seeds and zero exposed outcomes. Fixed scenario seeds are fixture identities; they are not future randomized evaluation allocations.

Actual randomized development, evaluation, confirmation and postboard counts remain **zero**. No Kotlin scenario is recorded as passing until a retained build-runner receipt and XML establish that result.

## Arithmetic assessment

The initial screen uses exact hypergeometric inventory probabilities for a uniformly shuffled sixty-card deck and a seven-card hand without mulligans. The one-card-category and bounded-land calculations are mathematically appropriate for that question. The Ferocity-without-creature and Ferocity-plus-sweeper expressions use disjoint categories; those disjointness assumptions hold for the actual reviewed role data because Ferocity is neither a creature nor a creature sweeper.

These quantities do not measure castability by a particular turn, executable sweeper lines, survival, clock, win probability or card causation. Printed colored-source inventory does not model tapped timing, affinity-dependent development, sacrificing artifact lands or Crypt Rats' black activation requirements. The runner explicitly limits its claims accordingly.

The current lists comprise three configurations and three comparators in each of A/B/C. Candidate A lists contain twenty to twenty-one lands and eleven to thirteen creatures; B contains twenty-one lands and eight to nine creatures; C contains twenty-one lands and fifteen creatures. A3 is a Grixis artifact-Shaman package competing within family A. These counts are diagnostics, not a performance ranking. In particular, low creature density is a hypothesis to test interactively, not a reason to invent a goldfish score for an attrition deck.

## Protocol review and corrections

The protocol distinguishes architecture selection, the contribution of Ferocity, and comparison with an established deck. It gives the candidate and no-Ferocity packages equal bounded development opportunities, permits broader comparator rebuilds, separates development from fresh evaluation/confirmation, and preserves failures and unresolved attempts. It does not use merely identical seed numbers as evidence of valid pairing.

The following issues were raised and the author revised the protocol before randomized outcomes:

| Issue | Reviewed correction |
|---|---|
| A point estimate of at least +5 points with a positive lower bound does not establish a true effect of at least +5 points. | The text now distinguishes supported positive benefit, a meaningful point estimate, and the stronger claim requiring a lower bound of at least +5 points. |
| Failure to demonstrate a large adverse matchup effect does not establish an acceptable loss margin. | Confirmation requires per-matchup lower difference bounds above -10 points against both the no-Ferocity comparator and established benchmark in aggression and blue-interaction matchups. |
| Sideboard-development allocations were ambiguous relative to the stated maximum. | The revised allocation is 200 candidate/comparator practice matches plus 100 sourced-benchmark policy matches, 300 total. |
| Diagnostics used as tie breakers were not operationally defined. | Exact ties use lexicographic deck identity; diagnostic interpretation cannot change the ranking. |
| Inference method and draw handling left implementation discretion. | Ten fixed strata, score coding, sample-variance formula, primary normal interval and 20,000 within-stratum bootstrap sensitivity resamples are specified; disagreement yields inconclusiveness. Implementation qualification is still required before evaluation. |
| A premature development stop could skip the permitted refinement stage. | The exploratory no-improvement stop follows completion of the bounded D3 round when valid candidates remain. |
| Sideboard development conflicted with exact sourced benchmark lists or could use confirmation feedback. | Sourced benchmark/gauntlet 75s remain fixed; candidate sideboards and all plans freeze before confirmation is exposed. |

## Data-integrity hardening — resolved

The independent checks above validate the actual current inputs. The arithmetic-runner author was advised to make those checks durable in the executable validator before input freeze and implemented all five changes:

1. Reject duplicate sideboard rows as well as duplicate mainboard rows; otherwise list-to-dictionary conversion can hide repeated entries.
2. Verify the declared content hashes, manifest identity/hash entries and import text against the actual JSON quantities.
3. Enforce exactly the declared A/B/C family allocation, rather than accepting any six family/directory keys with three files each.
4. Assert the disjoint category assumptions used by the joint-presence formula.
5. Include the deck manifest and import text among frozen inputs when they are published as authoritative exports.

These guard improvements were re-inspected and the six isolated rejection checks above passed. The revised freeze-input set includes the deck manifest and all eighteen import text files. No actual mismatch was observed in the independently audited eighteen decks. The guards do not replace the separate current-rules/common-printing audit or establish gameplay admission.

A final provenance ambiguity was also corrected: arithmetic output now distinguishes the canonical `deck_sha256` from `deck_file_sha256` for the JSON artifact. The family-A protocol description now explicitly admits the Rakdos and Grixis variants. After these corrections the three arithmetic tests and eighteen-list validator passed again. Reviewed file identities: `tools/project.py` SHA-256 `b51e5e771322c04e55b6d51bc0b356631906a9d9a41e77b883a9a1cfb9040354`; `protocols/RESEARCH_PROTOCOL.md` SHA-256 `ab663b6b5f3d4761469bb2a9fcfd73a0bffd74efd9bfdb80b2de94640b59cee5`. These identify the reviewed preparation; they do not replace an engine/deck/policy gameplay-admission receipt.

## Rules and build review

The Ferocity fixture's continuous `ModifyStats` and `GrantKeyword` defaults were checked against the SDK: both target the attached creature. A stolen-creature setup originally updated only `OwnerComponent`; the reviewer found that the zone-transition path also reads `CardComponent.ownerId`. The author corrected the fixture to maintain coherent ownership in both components before executing it.

The Toxin comparison has nine new deterministic real-engine fixtures covering temporary deathtouch/lifelink and Clue ownership; the Clue's actual two-mana activation, sacrifice cost and delayed draw; rejection with insufficient mana; loss of the only target before resolution; Shaman's symmetric nonflying damage; using a Clue as Shaman fodder without drawing; queued-source information and indestructible damage; prevention; zero-power combat; and lifelink going to the targeted creature's controller rather than the spell's caster. Crypt Rats arithmetic is separately covered by the mechanics author.

Source inspection identified a material qualification risk: the shared noncombat damage helpers appear to read projected/spell-granted damage keywords without the pending ability's prior-object information. A Shaman that has died or returned before an older activation resolves therefore needs an actual passing regression. This is an **unconfirmed engine-risk observation**, not a failed test result or a deck verdict. No shared engine was edited by this reviewer.

The build runner records exact compiled inputs before and after invocation, retains per-class XML/logs, rejects empty/skipped/failed results and refuses to accept input drift. The workflow uses read-only repository permissions. Its evidence is explicitly limited to deterministic assertions and does not constitute full pool/pilot qualification.

## Publication boundaries

At inspection, `git status` showed only the dedicated project, its workflow and three project-owned scenario-test files. No unrelated program changes were observed. Source card imagery is public audit material. Python `__pycache__` files are transient products and must remain excluded from the commit. No future evaluation seeds or hidden outcomes were present in the inspected publication scope.

Approval here concerns publication of the reviewed preparation with honest status labels. The root author must still inspect the final staged diff and archive the actual remote source checkpoint. Gameplay admission remains closed until rules, complete selected-pool compatibility, policies, masking, outcome handling and the stage's frozen source/seed contract are qualified.
