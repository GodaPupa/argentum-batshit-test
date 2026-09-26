# Independent review of the concurrent protocol checkpoint

Date: 2026-09-26 UTC. Reviewer: delegated `toxin_and_review` agent. This review makes recommendations; it does not edit either frozen protocol or allocate gameplay.

## Authoritative state inspected

The reviewer read the remote-tracking branch at `3afd83c8741c5b3d89f231543e951c5f204ca4ef` directly with `git show` and inspected its project tree. That checkpoint contains exactly three Ferocity Recycling files:

| Original file | SHA-256 of exact committed bytes |
|---|---|
| `ferocity-recycling/PROTOCOL.md` | `69a4018e9831fbd2d163ee441752d387adc4f8af9354b18f88285f4fe9a01699` |
| `ferocity-recycling/CURRENT_STATUS.md` | `2509b29d7e0356329f19295f153421828b0fd1e1d8dc91d80d3a622cc11c631a` |
| `ferocity-recycling/RULES_LEGALITY_AUDIT.md` | `d41953a26a3dd9f5781bf781cccc6f48ae1e8d332ff2ba3c4c0546bd3e2d119f` |

The committed status explicitly reports zero randomized development, evaluation, confirmation and postboard samples. No deck lists, seed ledger, engine implementation or experimental outcome artifact exists in that committed project tree. The local project ledger independently contains an empty allocation list, zero generated experimental seeds and zero exposed randomized outcomes. The local detailed protocol and initial input manifest were also inspected; they are separate preparation records, not authority to erase the concurrently published record.

The existing PR is reported by the root coordinator as PR #173. Continue that branch and PR after reconciling the accepted history; do not create a competing project or duplicate PR.

## Recommendation

**Preserve the original checkpoint and reconcile prospectively through an explicit amendment before any randomized gameplay.** This is justified by the user's existing authorization for bounded deck development, publication and autonomous continuation. No additional user permission is needed for the stated preparation reconciliation. The original protocol must remain intact and traceable; a local document must not silently supersede it merely because it is more detailed.

The reconciliation is not an outcome-driven sample increase. Neither preparation stream has generated or inspected randomized gameplay. The user permits up to nine initial Ferocity configurations, allows counts other than four, directs the project to test competing architectures and gives no mandatory 4/3/2 allocation. The differences in allocation and the one-copy candidate can therefore be declared and justified prospectively within that authorization. The original document's word “frozen” still matters: record an explicit exception/amendment and its timing, rather than rewriting what it originally said.

Deterministic arithmetic and engine-qualification observations remain their own evidence classes. They must be preserved, and they do not become randomized development games. In particular, a mechanics failure must be repaired or keep admission closed; it must not be used to discard an inconvenient candidate and call the remainder superior.

## Concrete reconciliation of the development limits

| Item | Concurrent original | Local detailed preparation | Recommended operative rule |
|---|---|---|---|
| Initial families | A/B/C; no fourth family | Same three strategic families | Preserve those three. A's Grixis package is an explicitly declared artifact-Shaman variant, not a fourth strategic family. |
| Initial Ferocity configurations | 4/3/2, nine total | 3/3/3, nine total | Prospectively amend to the actual nine prepared lists, 3/3/3. State that one A slot was reassigned to C before random outcomes to test the recovery-density boundary. |
| Ferocity copy counts | 2–4 | 1–4 | Prospectively admit the single one-copy B configuration as a lower-density comparison. Preserve the nine-list ceiling; do not create a tenth list. |
| Initial sample budget | At most 20 games per candidate/comparator pair per opponent | Eight games per list per opponent | Use eight games for each list, four play and four draw: **16 total games per pair per opponent**, below the conservative reading of the original 20-game cap. Five opponents and nine pairs yield **720 games maximum**. “Pair” names the deck comparison; it does not assert coupled random trials. |
| Refinement variants | At most two revised complete configurations per survivor | Incumbent plus two revisions for each of at most two survivors, mirrored for comparators | Keep **at most two new variants per survivor**, with equivalent no-Ferocity development. No additional incumbent resampling. |
| Refinement sample budget | At most 30 games per revised configuration | Forty per list including incumbent resamples | Use **30 per new list**, six per opponent, three play and three draw. Two survivors × two new Ferocity variants × 30 = 120 Ferocity games; equivalent comparator opportunity adds 120. **240 refinement games maximum**. |

This yields **960 randomized development games maximum**: 720 initial plus 240 refinement. With the detailed contract's two separately frozen 3,000-game evaluation/confirmation stages, the maximum preboard total becomes **6,960**, not 7,200. No old seed allocation must be replaced because none exists. Neither unused D2 nor D3 capacity transfers to a later stage.

For refinement selection, retain the D2 incumbents' existing exploratory records without replaying them as fresh trials. Predeclare how their fixed D2 scores are compared with the new variants' fixed D3 scores; do not pool stages or imply equal sampling precision. A reasonable deterministic rule is the highest equal-weight five-opponent score among the eligible incumbent and revised lists, with the existing lexicographic tie rule. This is development selection with selection bias and unequal precision, not confirmation. The eventual final comparison is resolved with fresh, equal-budget E/C samples.

## Two original requirements that need explicit preservation

### Final 60/15 freeze comes before evaluation

The original gate explicitly requires exact 60/15s and opponent 75s **before evaluation outcomes**. The local detailed plan currently places candidate-sideboard development after preboard evaluation. Do not silently omit the original requirement because preboard games do not use sideboards.

The clearest reconciliation is to move the bounded sideboard-development stage after D3's continuation decision and **before E**. Keep its already specified 300-match ceiling and equivalent candidate/comparator opportunity, then freeze all final 75s and sideboarding plans before E. E/C outcomes cannot train sideboard choices. This preserves the stricter original freeze condition and leaves later preboard/confirmation outcomes independent of deck tuning. A project that cannot yet qualify the 75s remains in development; it does not pretend the gate passed.

### “No catastrophic pressure-cell regression” needs a fixed definition

The original refinement selection rule includes this restriction; the detailed local selection text omits an operational threshold. The amendment should define the criterion before D2 rather than remove it or leave it discretionary.

One concrete conservative screening definition is: a revised list is ineligible when its observed score against any of the five opponents is **more than 25 percentage points below its own D2 incumbent's score against that opponent**. Apply the same definition to candidate and comparator refinements, with fixed starting-player balance. This is an explicit coarse development safeguard, not a confidence statement or proof of underlying matchup harm; six/eight-game cells have substantial sampling noise. The root author may choose another clear threshold before any samples, but the final chosen threshold and its reference deck must be written in the amendment before use.

## Required preservation and implementation steps

1. Integrate the concurrent remote commit as history without force-pushing or overwriting another worker's commits. Recheck the branch immediately before publication and reconcile any further advance in the same way.
2. Preserve the three original files byte-for-byte in an auditable archive as well as retaining the original `PROTOCOL.md`. Preserve the already frozen local detailed protocol and manifest too. No archived content is retrospectively relabeled as amended.
3. Add one clearly named prospective amendment with both parent source identities, the zero-game/zero-allocation status, the exact changed rules, the reasons above and an explicit precedence statement. Only listed provisions are changed; original rules and limits otherwise continue to govern.
4. Bind the next gameplay-admission manifest to the original checkpoint, local preparation manifest and amendment hashes. Reject admission on disagreement. An old file remaining in the tree is not enough if a runner silently reads the wrong budget.
5. Update current status and the research report to describe the reconciliation and continued zero gameplay. Such current summaries may be updated through the normal reviewed change while their accepted prior bytes remain archived and in history.
6. Continue the existing scoped PR. Deterministic qualification failures remain preserved and gameplay admission remains closed until their actual causes are resolved and the required coverage passes.

## Review disposition

The reviewer supports this prospective reconciliation under the user's existing authorization, with the stricter 30-game/new-variant-only refinement budget, preservation of original bytes/history, final-75 freeze before evaluation, and a concrete refinement-regression rule. The reviewed recommendation does not itself amend frozen files, consume seeds, admit gameplay, approve a card promotion or reach an experimental conclusion.

## Addendum — adopted amendment inspected

The reviewer subsequently inspected `protocols/RECONCILIATION_AMENDMENT.md` and `protocols/ACTIVE_CONTRACT.json`. The amendment adopts the recommended 720/240 development limits, no incumbent resampling, 6,960-game preboard maximum, S before E, exact final-75 freeze, symmetric refinement opportunity and continued post-release sanctioned-legality gate. All four document/manifest hash bindings in the active contract were independently verified. All three archived initialization files exactly match the original committed bytes above.

The root author chose an absolute **one-third score-point** catastrophic-cell threshold rather than this review's suggested greater-than-25-point example. This is permitted because the choice was made with zero randomized attempts/outcomes. The final wording is unambiguous: `s_new <= s_D2 - 1/3`, a decline of 33⅓ percentage points, applied symmetrically to Ferocity and no-Ferocity revisions with the small-cell uncertainty caveat. It satisfies the requirement to operationalize the original condition prospectively.

Reviewed amendment SHA-256: `2829d4623e142cdf427339b4e19f8bc8f96747410eeccb1abe1287986b35458f`. Reviewed active-contract SHA-256: `8d0551bb3fc5e4a9492540412fec17284bb8311d3501d0baf014da3969cc15d4`.

**No remaining textual or budget mismatch blocks publication of this preparation.** At inspection the local HEAD was still the shared base; the root coordinator must verify that `3afd83c8741c5b3d89f231543e951c5f204ca4ef` is an ancestor of the final published tip after the planned merge before claiming that history integration is complete. This is a concrete pending publication check, not a request for new permission. Mechanical failure receipts remain failures; gameplay remains unadmitted.
