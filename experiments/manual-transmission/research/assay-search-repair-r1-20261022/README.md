# Authorized Oracle Assay search-printer repair

The ordinary build at `a9ea02595bcdc681778b1e003e1b79d51737aeb2` failed four existing assertions because the parser calls the SDK's current `searchLibrary` facade, which emits `CardSource.AuthorizedLibrarySearch`, while both printer extraction helpers still expected `CardSource.FromZone`. Parsing therefore succeeded but printing returned null. The original failure and held copy-entry work are preserved at `f57d961780ab196f2c6d74269a9c9f9d752de80e`.

This repair aligns the shared printer extraction helper with the facade and retains each caller's exact whole-recipe reconstruction. No SDK, engine, card, filter, experimental protocol, frozen deck or original assertion changes. Six new model-first regression cases exercise singular destinations, counted and two-filter searches, and refusal of changed scope, choice ordering or event emission.

Qualification is pending. `PROTOCOL.json` defines a separate old-production negative control, all Assay tests, full touchstone and differential reporting, followed by the ordinary whole-project build. The negative control changes only `Library.kt` to the parent version inside its disposable CI checkout. All four original failing assertions and the three new positive cases must fail there. Three new refusal cases must pass. The repaired source must pass all 568 Assay cases. This establishes regression sensitivity without weakening or editing tests; it is not a claim of independent human/agent review.

Copy-entry remains held until the whole-project build passes and this repair is qualified. Official competitive results remain zero. Both Precision games remain reserved.
