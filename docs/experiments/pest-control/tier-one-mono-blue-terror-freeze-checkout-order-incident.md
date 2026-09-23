# Pest Control Tier-1 coverage — Mono-Blue Terror checkout-order pre-entropy incident

## Disposition

Workflow runs `35817231288` and `35818142621` are permanently classified as
`REJECTED_PRE_ENTROPY_CHECKOUT_ORDER_INCIDENT`.

Neither run is a seed-generation attempt.

## Observed boundary

Both runs targeted `main` at commit
`96d68421a7387a57f57becbb472ae5eea30fdfa2`, workflow attempt `1`, with the exact intended
acknowledgement.

Both jobs failed in the `Validate dispatch metadata` step because the reusable guard script was
invoked before `actions/checkout`; the repository files were not yet present on disk.

For both runs:

- artifact-absence audit: skipped;
- repository checkout: skipped;
- frozen source audit: skipped;
- one-shot entropy draw: skipped;
- evidence upload: skipped.

Combined provenance consequence:

- `os.urandom(32)` calls: `0`
- official seeds drawn: `0`
- official seeds retired: `0`
- quarantined vectors: `0`
- freeze artifacts: `0`
- games initialized: `0`
- actions submitted: `0`
- outcome exposure: `0`

The two runs consume no production vector attempt.

## Correction

The production workflow order is changed to:

1. checkout exact workflow source;
2. validate dispatch metadata;
3. audit absence of a prior freeze artifact;
4. audit the frozen source boundary without entropy;
5. draw and quarantine exactly once;
6. upload immutable evidence.

Pull-request validation now also asserts this exact production ordering from the workflow file. A
fresh manual dispatch is permitted only after the ordering correction passes the dedicated freeze
validator and full repository CI.
