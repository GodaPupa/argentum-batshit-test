# Elves preboard replacement V1 — provenance quarantine

Date: 2026-09-21
Branch: `face-value/lab`

## Disposition

**QUARANTINED — SEED SERIALIZATION / GENERATION PROVENANCE DEFECT**

Workflow run: `35572397418`

The run completed 32/32 games and the automated gameplay audit was green. Raw outcome was 12 Face Value wins and 20 losses, with 0 gameplay flags and 34 successful Undercity Arena targets.

Those outcomes are **inadmissible** and may not be used for matchup estimation, structural diagnosis, challenger design, sideboard tuning, or promotion.

## Defect

The replacement vector was created through JavaScript numeric literals larger than the exact-integer range of IEEE-754 doubles. Values above 2^53 were rounded before being written to JSON.

Examples:

- intended `4358007955011148085` became `4358007955011148300`
- intended `1946619012029358695` became `1946619012029358600`

The committed registry therefore did not preserve the exact originally specified 64-bit integers.

More importantly, the frozen protocol requires official qualification seeds to be generated from an operating-system cryptographic source. The V1 replacement vector was manually supplied rather than generated inside a provenance-captured cryptographic workflow.

Both defects are sufficient to reject the entire block.

## Preservation

- Run `35572397418` remains preserved as historical rejected evidence.
- Every seed actually present in its committed registry is permanently retired.
- No seed from this vector may be reused or rehabilitated.
- The 12–20 outcome must remain sealed from tuning authority.
- The Undercity remediation itself remains accepted; this quarantine concerns only the replacement qualification vector.

## Next gate

Generate exactly one **Elves preboard replacement V2** vector:

- 32 fresh, unique, nonzero positive signed 64-bit seeds;
- generated with Python `secrets` / OS CSPRNG inside GitHub Actions;
- written and hashed without JavaScript numeric conversion;
- audited against every tracked repository occurrence and all retired diagnostic ranges;
- 16 Face Value play / 16 draw assignments;
- committed and frozen before any outcome exposure;
- then executed exactly once under the accepted Undercity remediation profile.

Postboard Elves remains blocked.
No challenger is authorized.
