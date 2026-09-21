# v0.9 Phase 9 — Paired Capsize Pilot Inadmissible

Accepted control: `izzet-science/v0.7-control.md`

Accepted control SHA256:
`726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`

## Frozen identities

- experimental source commit: `2035adc350368f8eeec87a2f960ea05c40763fb8`;
- experimental source tree: `b2972f92fb1afaf994e9020362960b2c944a76c1`;
- source-freeze record commit: `20d10227315cc58f826a01e53ccd001469294b8f`;
- workflow arm commit: `48b67ab63b2fd79baca7afc8d28f6b56fdb7db1c`;
- workflow SHA256: `568338c999ded1c0ea0bf4f22bf73819c4eb9e28cef35b838ac9e63ded1ec9b3`;
- runner SHA256: `1dce15e3555f937964ae571140ca43e763f52747af56c2be71d53a7b7bcebb36`;
- master seed: `0x00000001A22E7010`;
- matched pairs: 10,000; trajectories: 20,000; horizon: T1–T10.

## Sole run

- GitHub Actions run: [35548464487](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/35548464487);
- job: `106178640891`, `izzet-v09-capsize-paired`;
- conclusion: failure;
- checkout, frozen-identity verification, and complete seed-free preflight: success;
- paired execution: failure;
- manifest audit and artifact upload: skipped;
- uploaded artifacts: zero.

The master seed was consumed when the paired iterator began. It is permanently
retired and this run may not be rerun, replaced, pooled, or rehabilitated.

## Failure

The runner stopped after approximately one second with:

`ValueError: policy event identity violation`

The aggregate contract correctly rejected a policy turn where tutor selection was
reported but Capsize acquisition did not complete. Static control-flow diagnosis
shows that `choose_capsize_tutor` proves payment with
`ready_payment_feasible_exact`, while `execute_declared_tutor_target` pays with the
older `pay_colored_mutating`. Those implementations do not share semantics:

- the mutating payer omits Izzet Signet entirely;
- it treats Prismatic Lens only as colorless and cannot filter;
- it treats Star Compass as colorless rather than using controlled basic types.

After selection and before execution there is no intervening state or library
mutation, so the failed identity can only arise from this selector/executor payment
disagreement. The fail-closed log did not preserve the first failing game index or
mana-source state, so it would be improper to claim which listed source caused this
specific trajectory.

## Disposition

No complete summary, manifest, or exact four-file artifact exists. No paired outcome
or performance claim is admissible. The Capsize policy earns no promotion, no card
change is authorized, and v0.7 remains the accepted control.

The temporary execution workflow is removed in the same disposition change and the
ordinary workflow is restored byte-for-byte from `origin/main`. Any future pilot
requires a new seed-free gate that unifies selector/executor payment semantics,
adds adversarial fixtures for Signet, Lens, and Compass, and freezes a new execution
identity with a fresh seed.

Disposition: `V09_PHASE9_PILOT_INADMISSIBLE`
