# Pest Control Tier-1 coverage — Mono-Blue Terror acknowledgement whitespace incident

## Disposition

Workflow run `35818672369` is permanently classified as
`REJECTED_PRE_ENTROPY_ACK_WHITESPACE_INCIDENT`.

It is **not** a seed-generation attempt.

## Observed boundary

The run targeted `main` at commit
`301a19a660dc7030fb6e7511a72015f7f0ac919e`, workflow attempt `1`.

Repository checkout completed successfully. The next step, `Validate dispatch metadata`, failed
closed before the artifact guard, source audit, entropy draw, quarantine, or evidence upload.

The guard diagnostics showed:

- ref check: pass;
- workflow-attempt check: pass;
- raw acknowledgement length: `61`;
- expected acknowledgement length: `54`;
- acknowledgement semantic token: correct after removing seven leading ASCII spaces.

The raw acknowledgement SHA-256 matched exactly seven leading spaces plus the canonical token.

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

## Correction

The acknowledgement guard now removes only leading/trailing whitespace and then requires the
canonical token exactly. Internal token changes remain prohibited.

Pull-request validation explicitly proves:

1. the canonical token passes;
2. the same token with seven leading spaces passes after trimming; and
3. a wrong token fails closed.

All other production guards and the 550-value seed exclusion boundary remain unchanged.
