# ARGENTUM — R7 Ballista target-allocation opt-in candidate (October 19, 2026)

## Scope and disposition

**VERIFIED: 20/20 targeted regressions passed; isolated gameplay integration in progress; no promotion to frozen deck or CURRENT.**

- Base verified R7 Ballista fork report source `09d9af83f4338605fec4dc0380f0fda20963854c`, validated fork run `39328726693`.
- Exact passing corrective source `a4a4234533c78c3be628fd1e22f0fd7c54f54f8a`, branch `gameplay/manual-r7-ballista-allocation-20261019`.
- GitHub Actions [39337184560](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39337184560): **SUCCESS**, 20/20 test cases: 12 Ballista AI discovery + 4 delegation + 2 witness guards + 1 Pact debt protection + 1 combined archived positive/negative test.
- Artifact `12228205338`, original ZIP SHA-256 `7d4ddf910017ded7fff8b8e13c4db30f38f931c98990e3a7b259901a7df804b4`; downloaded and independently SHA-256 verified. Artifact includes all ten archived source-bound allocation scoring observations and the actual selected AI action.
- All source modifications are isolated, explicitly opt-in. `ManualR7BallistaAllocationCandidate.profile` replaces the R4 Ballista advisor module with one combined `ManualR7BallistaCombinedAdvisor`, which delegates all original Ancestral Statue response and Ballista/Statue terminal-witness scoring; no duplicate Walking Ballista registration.
- Frozen v0.7 deck, `AiProfile.CURRENT`, original R5 candidate and original R4 Ballista advisor remain unchanged. No main merge or C4/D2 official activation.

## Positive control: real decision corrected

Replayed the exact accepted R7 seed-1609 pre-action `2659` state: turn 46, Walking Ballista with eight remaining +1/+1 counters, seven queued earlier Ballista shots targeting other opposing creatures, and ten-toughness opposing Animar. The archived R5 pilot fired all eight remaining shots at Animar without killing it.

The original opt-in scoring proposal used an insufficient `passScore - 3.0` floor. Exact diagnostic [39335758795](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39335758795) recorded that the advisor was in fact invoked, but Strategist added `+5.0` structural sequencing **after** advisor scoring, resulting in score `191.35846399783657` versus pass `189.35846399783657`; R5 behavior still fired the shot.

The narrow candidate now offsets this observed `+5.0` post-score adjustment with an `8.0` raw-pass margin, **only** when the visible stack has Ballista shots aimed elsewhere and the target cannot be killed even using all remaining available counters plus already queued target shots.

The new prospective, actual `AIPlayer.chooseAction` behavioral test selected:

`PassPriority(playerId=manual-mirror-2)`

instead of the original Animar-targeted shot at the exact decision state.

The direct candidate score was `181.15846399783655` versus pass `189.15846399783655`, before any post-score adjustment. The repaired chosen-action test passed.

## Required opposing lethal-control

Replayed the exact seed-1610 original state before action `2764`, where Ballista had nine counters and nine-toughness opposing Animar could really be destroyed.

All **nine** actual shot activations were processed and checked with the rules engine, using the legal runtime ability ID at each step (historical serialized IDs are not portable across registry sessions) while retaining the original target. Each of nine archived scoring probes returned `candidateScore = null` and `retainedAdvisorScore = null`, proving the new narrow correction *does not apply* to that lethal sequence. Nine shots were accepted and resolved by the preserved rules-engine control; no fabricated action or opponent pass was used. Original kill demonstrated by accepted R7 forensic artifact remains valid.

## Preserved failed evidence

The prior runs `39332991140`, `39333559973`, `39335029917`, `39335758795`, and `39336438039` failed at distinct stages (unresolved opponent helper, insufficient score floor, and historical ability-ID binding); all failed evidence remained separate. They are superseded for the *targeted regression* by the passing exact source/run above; they must not be rewritten as successes.

## Bounded next gate

One isolated integration branch `gameplay/manual-r7-ballista-integration-20261019` is staged from exactly passing source `a4a4234533c78c3be628fd1e22f0fd7c54f54f8a` with only a diagnostic harness and gated workflow added.

Actions [39338118746](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39338118746) is the prospective integration. It verifies the exact successful targeted-run SHA and frozen input blobs, runs a **64-accepted-action prefix smoke**, and launches **one** same-seed (`202610191609`) four-player complete game only when the prefix succeeds. Do not claim this game passed before reviewing its own completed run/artifact. No additional games or official qualification are authorized.
