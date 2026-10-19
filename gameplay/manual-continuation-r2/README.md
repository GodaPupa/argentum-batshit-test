# Manual productive-continuation revision

Parent: Round 2 evidence/source commit `9ee4ae5262558803d09b2cf828deb54afd7498ec`.

The archived seed 202610190804/Seat 2/turn 39 decision credits Purphoros despite its with-Animar gain being negative. This opt-in candidate now requires that follow-up itself have a finite, positive ordinary evaluation gain before adding its incremental Animar benefit. It adds no new rules, SDK vocabulary or UI behavior. CURRENT and the frozen 100 cards are unchanged. The separate mechanics branch is not incorporated.

A real-action regression replays the immutable final-seed-3 archive to positions 905 and 1270, preserving actual Animar→Visionary development and refusing the counterexample. Unique legal ability-ID bindings are recorded on the same physical card; no entity remapping is used. The regression is published against unchanged r1 code first to verify that it detects the defect.

After focused regression success, the workflow executes 20 matched cells (40 attempts), each with one candidate game and one all-CURRENT game. It repeats the same seeds 202610190801–202610190805 and four candidate seats used in Round 2. These are matched regression seeds, not five new independent setups. Ten independent cell jobs can run concurrently; each preserves its own artifacts. Tests explicitly disable Gradle build-cache reuse.

This guard does not commit the pilot to a specific continuation or implement a Statue endpoint/payoff planner. It does not implement Cruise, Sport or Race. All 40 scheduled attempts are now verified: 38 complete and two incomplete. See REPORT.md, games.csv, comparison-results.json, and ARTIFACT_RECEIPTS.json for actual outcomes and source-bound raw traces. Retain the candidate as an opt-in scoring correction, without merging or claiming stronger play.

The first guarded run passes the real counterexample regression but exposes an older unit expectation that credited the minimal Llanowar fixture despite a nonpositive absolute gain. That expectation now explicitly measures the real simulated gain and requires deferral. Productive development remains covered by the actual action-905/action-908 Visionary sequence. The intermediate failed validation is preserved rather than counted as success.
