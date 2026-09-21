# Pest Control Tier-1 coverage — opaque four-slot plan

## Purpose

This gate binds the frozen four-row assignment artifact to the accepted cell order without opening
the artifact or representing any seed value. Each slot contains only its ordinal, an opaque row
reference, its predecessor reference, seat assignment, and starting deck. No slot can contain an
entropy value, filesystem path, environment, action, result, or outcome.

The plan composes the green fail-closed admission gate with coordinator schema
`2e453fd0cf6626925fcfc60a1ee5a55b6aa880c2df62374430884b0e6be0bc9f`. It requires global order,
durable attempt recording before initialization, no retries, and terminal stop on failure.
Its canonical proof SHA-256 is
`0ddf928336155a0de27ff29c26af0b2d008f55768b257acd7c4f7eb7f6ca1bee`.

## Current state

- Opaque slots: `4`
- Official assignment rows bound by artifact digest: `4`
- Official seed values exposed: `0`
- Official seeds consumed: `0`
- Initializer enabled: `false`
- Runner enabled: `false`
- Execution authorized: `false`
- Executable methods: `0`
- Official games initialized: `0/4`
- Actions submitted: `0`
- Outcome artifacts written: `0`
- Outcome exposure: `0/4`

The pull-request workflow has no manual trigger or artifact-download path. It validates only the
opaque plan and cannot initialize or execute a game.
