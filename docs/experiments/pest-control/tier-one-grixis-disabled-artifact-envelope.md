# Pest Control Tier-1 coverage — disabled artifact digest envelope

## Result

This gate binds the complete frozen-artifact digest set to the accepted compiled vector binding. Its
canonical SHA-256 is `76788177bdb6263a6b0f29ffb2390ac089c678b65ac3e78f3dbb261eea8e2edd`,
and its only successful status is
`ARTIFACT_DIGESTS_VERIFIED_BYTES_NOT_LOADED_EXECUTION_NOT_AUTHORIZED`.

The envelope pins the workflow run, artifact, ordered vector, assignment CSV, freeze manifest,
quarantine, checksum inventory, and archive identities. Its type can represent only those digests
and numeric GitHub identities. It has no byte array, path, URL, seed, assignment row, environment,
action, event, result, or outcome field.

## Fail-closed state

- Artifact digest set: verified
- Artifact bytes loaded: `false`
- Seed values visible: `0`
- Official assignments: `4`
- Official seeds generated: `4`
- Initializer enabled: `false`
- Runner enabled: `false`
- Official games authorized: `0`
- Official games initialized: `0`
- Actions submitted: `0`
- Outcome-bearing artifacts: `0`
- Outcome exposure: `0/4`

Digest substitution changes the canonical proof and fails closed without loading bytes.
`PestControlTierOneGrixisDisabledArtifactEnvelope` exposes only `inspect`; it cannot read the GitHub
artifact, decode seeds, initialize a game, or write an output.

The next construction gate may define a private, disabled parser boundary exercised solely with
synthetic nonexperimental fixture bytes. Official artifact loading and execution remain unauthorized.
