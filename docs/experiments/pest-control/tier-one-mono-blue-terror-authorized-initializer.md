# Pest Control Tier-1 coverage — authorized Mono-Blue Terror initializer

## Scope

This gate adds the first operational Mono-Blue Terror **initialization** surface after the separate
four-game execution authorization was accepted.

The initializer may create one `GameEnvironment` only when:

- the reviewed execution authorization is green;
- a durable attempt has already been recorded;
- the vector identity matches the frozen vector, assignment CSV and manifest hashes;
- the execution commit is a pinned lowercase 40-character Git identity; and
- the frozen Pest Control and Serpico_CC Mono-Blue Terror deck readiness checks remain green.

It respects the assignment's Pest seat and starting deck and uses that assignment's supplied seed.

## Boundary

The initializer does not:

- decode or download the frozen artifact;
- choose an assignment;
- create durable evidence;
- advance gameplay;
- submit an action;
- expose a runner or workflow;
- regenerate, replace or reroll a seed.

Repository tests use fixed nonofficial assignments only and exercise all four seat/start cells.

After this gate, official games remain uninitialized until a separately reviewed coordinator/runner
binds the decoded frozen assignments to actual durable evidence and this initializer.

Current official counters remain:

- Official seeds consumed: `0`
- Official games initialized: `0/4`
- Actions submitted: `0`
- Outcome exposure: `0/4`
