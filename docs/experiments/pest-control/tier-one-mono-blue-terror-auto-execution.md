# Pest Control Tier-1 coverage — automatic one-shot Mono-Blue Terror execution

## Authorization

The official frozen four-game Mono-Blue Terror smoke is authorized for exactly one automatic
production attempt after the complete composed `VALIDATE_ONLY` stack passed.

Immutable authorization record:

`tier-one-mono-blue-terror-auto-execution-authorization.json`

SHA-256:

`80b1097c88059668d9b7740db5213690430cf6bf1e9299a0c3a94d466a9e7c1f`

It binds:

- composed validation merge `97924f57522528507ce0e71ffc0cc0f942a83fef`;
- composed validation tree `1a34844a03698e3b67f7cafe9aec4e73067de7c0`;
- composed validation run `35888858865`, attempt `1`;
- composed validation artifact `10765360051` and its archive digest;
- frozen vector artifact `10733086089` and its archive digest;
- exact protocol/block/qualified-runner identities;
- exactly four authorized games;
- attempt limit `1`;
- no rerolls, replacements, or seed regeneration.

## Automatic trigger

There is no `workflow_dispatch`.

The production job is triggered only by the push to `main` that adds the immutable authorization
record. Pull requests run validation-only checks and cannot enter the official job.

Production replay barriers are independent:

1. workflow attempt must equal `1`;
2. the authorization record bytes must match the pinned SHA-256;
3. the accepted composed-validation merge must be an ancestor of the triggering commit;
4. no earlier production `push` run for this workflow may exist; and
5. no prior official execution artifact may exist.

## Durable execution order

Before gameplay the workflow uploads enough context to reconstruct the source boundary and creates a
durable execution envelope.

The official runner then:

1. verifies and decodes the exact frozen ZIP;
2. exclusively claims the canonical evidence block;
3. records each official attempt durably;
4. records initialization-entry intent durably;
5. initializes exactly that assignment through the authorized initializer;
6. drives it with the qualified production driver;
7. records the raw game durably;
8. reconciles the coordinator and artifact contracts; and
9. writes summary/index/event evidence before requiring the final disposition to be `VALIDATED`.

If any game fails, later games are not attempted. Partial durable evidence is retained and the
workflow artifact is uploaded with `if: always()`. A rerun is prohibited.

## Pre-execution state

Until the production push occurs:

- official seeds consumed: `0`;
- official games initialized: `0/4`;
- official actions: `0`;
- official outcome exposure: `0/4`.

This gate is intentionally merge-triggered so no manual GitHub workflow action is required.
