# Public target-group R4 recovery design

## Demonstrated R3 failure

Qualification 40104302392 compiled both normal and visit-loss mutant forms, passed all 34 client cases, 39 actor safeguards, and 20 normal targeted cases, then produced exactly the intended five `AssertionFailedError` witnesses under the executable mutant. Its gate nevertheless rejected the run because the frozen first-frame metadata was stale. The original report binds PG01/PG04/PG05/PG15/PG17 to lines 61/81/88/147/165; R3 expected 59/79/86/147/166.

## Bounded repair

R4 changes no production source and no test source. It retains all test identities, assertions, executable fault injection, compile controls, positive gates, protected-byte checks, and report-presence fail-closed behavior. The new protocol and gate bind the immutable R3 source as their parent and correct only the source-bound first-frame pins.

## Acceptance limit

A successful R4 qualification can accept only public target-group transport, server validation, and server-side original-slot capture through declared continuations. It cannot accept occurrence-aware resolution, Spellskite, multiplayer, official games, or seeds.
