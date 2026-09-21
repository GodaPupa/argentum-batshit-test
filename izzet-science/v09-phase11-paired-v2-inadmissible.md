# v0.9 Phase 11 — Recovered Paired Pilot Inadmissible

Accepted control: `izzet-science/v0.7-control.md`

Accepted control SHA256:
`726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`

## Frozen identities

- experimental source commit: `cd93b7e427b4e8c1b67cbf23560770d2ad19b5bc`;
- experimental source tree: `d5b4ce36672181cdcb26b34644ca9dd56862ad4e`;
- source-freeze record commit: `f123e2980231d8fd0239047cdee6dbf88aceece3`;
- workflow arm commit: `f235481d03e5735eaa6dc1740b770907e668651f`;
- workflow SHA256: `db5057a74f944b22e4d1fd00994775b81d3b0fb4e0c14eab1fcfe6c2c36958ef`;
- runner SHA256: `2db5bdc6351836ffa3f5c76c98f9966b0a516fbeafc796eb9a2ae85f70932bbe`;
- master seed: `0x00000001A22E7011`;
- matched pairs: 10,000; trajectories: 20,000; horizon: T1–T10.

## Sole run

- GitHub Actions run: [35549404361](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/35549404361) (`#193`);
- job: `106181236113`, `izzet-v09-capsize-paired-v2`;
- conclusion: failure;
- checkout, frozen-identity verification, and complete recovered preflight: success;
- paired execution: failure;
- manifest audit and artifact upload: skipped;
- uploaded artifacts: zero.

The master seed was consumed when the paired iterator began. It is permanently
retired and this run may not be rerun, replaced, pooled, or rehabilitated.

## Failure

The runner stopped after approximately two seconds with:

`ValueError: more than one Capsize acquisition in a game`

The aggregate contract rejects a second successful tutor event in one trajectory.
That invariant is unsound in the modeled game: `resolve_brainstorm` may put the
lowest-priority cards from the full hand back on top of the library. An acquired
Capsize can therefore legally return to the library and be acquired again later.
The run log does not preserve the first failing game or its zone trace, so this
record does not claim that Brainstorm was certainly the observed path; it identifies
the explicit modeled mechanism that invalidates the one-acquisition assumption.

This defect is in aggregation semantics, not the repaired selector/executor payment
path. All three payment recovery fixtures and the complete preflight passed before
the failure.

## Disposition

No complete summary, manifest, or exact four-file artifact exists. No paired outcome
or performance claim is admissible. The Capsize policy earns no promotion, no card
change is authorized, and v0.7 remains the accepted control.

The v2 runner is unconditionally retired before argument parsing or iterator
construction. The temporary workflow is removed and the ordinary workflow restored
byte-for-byte. Any future pilot requires a seed-free contract redesign that
distinguishes tutor-event counts from the per-game `ever acquired` estimand and
covers a deterministic acquire→Brainstorm put-back→reacquire trajectory.

Disposition: `V09_PHASE11_PILOT_INADMISSIBLE`
