# Pest Control Tier-1 coverage — generator acknowledgement pre-entropy incident

## Disposition

Workflow run `35819323454` is permanently classified as
`REJECTED_PRE_ENTROPY_GENERATOR_ACK_INCIDENT`.

It is **not** a seed-generation attempt.

## Observed boundary

The run targeted `main` at commit
`81a6135f20b7e87cc9053f7bb340bdae2104361e`, workflow attempt `1`.

The run successfully completed:

- repository checkout;
- dispatch metadata validation;
- prior-artifact absence audit; and
- the complete frozen source / 550-seed exclusion preflight.

The generator then rejected the production acknowledgement before the line that calls
`os.urandom(32)`. The evidence-upload step found no output directory because no quarantine record
had been created.

## Provenance consequence

- `os.urandom(32)` calls: `0`
- official seeds drawn: `0`
- official seeds retired: `0`
- quarantined vectors: `0`
- freeze artifacts: `0`
- games initialized: `0`
- actions submitted: `0`
- outcome exposure: `0`

The run consumes no production vector attempt.

## Resolution

The manual-input production path is retired. No further user-triggered workflow run is required.

A repository-bound one-time authorization record now controls production:
`tier-one-mono-blue-terror-auto-freeze-authorization.json`.

The production workflow is triggered only by the merge that adds that record to `main`. It
preserves the prior artifact-absence guard, complete source audit, 550-value collision exclusion,
one-shot entropy draw, quarantine-before-validation discipline, no rerolls/replacements, and zero
gameplay authority.
