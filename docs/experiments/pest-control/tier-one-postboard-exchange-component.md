# Pest Tier-1 postboard exchange construction

Status: the exact-source deterministic component passed all 21 declared checks and received
independent non-author source and raw-artifact review. Combined receiving CI remains unaccepted;
inherited failures are being diagnosed. No boarding strategy is accepted by this component.

The qualified source is `232f4904f3929fec917f87b9565f8adb4047487c`, tree
`a7356f15ea5900592cf06ff4e4858929fa5b15ea`. The accepted component evidence is
[run 36254534260](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/36254534260),
artifact `10909039961` (232,053 bytes, SHA-256
`c3651fe85b4c8f195b82b9e8a9a7ab551768f36becb4cd442bc8c28184d933f7`).
The [durable receipt](postboard-exchange-evidence/remote-36254534260/receipt.json),
raw source binding, actual XMLs, inventory and losslessly compressed qualification log preserve
the exact 20 exchange + 1 inventory cases, all with zero failures, errors or skips. The independent
reviewer was the non-author `/root/sphinx` agent; no GitHub approval is asserted. The earlier author
audit is retained as an initial checkpoint, whose pending review is superseded by the independent
receipt.

The frozen qualification stopping rule requires every postboard plan to preserve exact 60/15
quantities and the complete 75-card pool, and to freeze its deck digests before a postboard seed
exists. Previously the project had source inventories but no common postboard deck-construction
boundary. `PestControlTierOnePostboardExchange` now supplies that boundary for the existing Pest
control and its five frozen opponents.

## Construction contract

The compiler reads the six existing frozen main/sideboard declarations; it does not copy or replace
their lists. Every call checks the original main, sideboard and complete-75 hashes and exact counts.
The only policy input is a pair of exact name/count maps: cards moving from sideboard to main and
cards moving from main to sideboard. The API cannot receive a seed, game state, hidden hand,
library order, game number, result or opponent action.

Both transfers are simultaneous. Unknown names, nonpositive or excessive counts, unavailable
copies, unequal exchange totals and a name moved in both directions are rejected. A zero-exchange
plan is represented by two empty maps. Names already present in both zones retain and add their
copies. The compiler independently verifies the resulting 60/15 totals and complete card multiset.

To make the deck order stable, each zone retains the original frozen name order, removes zero-count
rows and appends newly introduced names in the donor zone's frozen order. Incoming map iteration
order never changes the deck. Digests use the existing UTF-8 `name,count\n` format and `MAIN` /
`SIDEBOARD` complete-75 envelope. Prepared results copy their inputs and do not expose their
internal maps.

The prepared result is reviewable through its six original/result hashes and identity enum.
`toDeck(frozenBinding)` requires equality with a separately frozen exact binding before building
the SDK `Deck`. It puts the remaining fifteen cards in the SDK sideboard field; it never shuffles,
initializes, resolves a card or takes an engine action. Digest equality itself is not independent
review or execution admission.

## Meaningful deterministic qualification

The dedicated workflow binds its exact source/tree, all five original deck-declaration files, this
component, its tests, the receiving inventory test and the unchanged stopping rule. It requires
20 actual exchange cases plus the existing one-case six-deck inventory audit, with no missing,
failed, errored or skipped cases and no unresolved inventory row. It retains actual XML, source
digests, logs and a component-only receipt, including failure evidence on an unsuccessful run.

The exchange tests cover all six frozen 75s, a nontrivial six-card exchange with independently
calculated SHA-256 values, exact SDK sideboard construction, 75-card multiset conservation,
overlapping main/sideboard names, ordering invariance, caller mutation isolation, invalid inputs
and rejection of every drifted original/result binding field. The nontrivial fixture is arithmetic
test data, not an adopted matchup strategy. Its expected hashes were independently calculated from
the frozen Pest rows using Python's SHA-256 implementation.

## Admission boundary and next work

Main-source integration, canonical post-block receiving qualification, actual boarding choices
for both sides of each of the five existing matchups, their strategy review and exact digest freeze,
complete sideboard pilots, replay/runtime/journal guards, current admission and independent
gameplay acceptance remain required. This component neither accepts reachable interactions by
registry presence nor permits a pilot to avoid unimplemented interactions.

Monster replacement C2/A2 remains a distinct governed source and authorization boundary. The
Monster proposal expressly forbids silently importing moving main, Spy or postboard changes into
C2. None of those bindings or workflows changes here. All 81 historical games remain quarantined;
no official seed, claim, attempt, initialization, action or outcome is created by this component.
