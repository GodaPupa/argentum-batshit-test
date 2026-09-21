# Pest Control Tier-1 Grixis — fourth preflight-only execution incident

GitHub Actions run `35621541278` is permanently retired as a fourth pre-execution infrastructure
failure.

The v4 diagnostics isolated the failure to the execution-acknowledgement step. GitHub's log rendered
the supplied value as `EXECUTE_FROZEN_TIER_ONE_GRIXIS_4_EXACTLY_ONCE`, but the exact shell equality
test still returned nonzero. This is consistent with invisible input whitespace or another
representation artifact.

All later steps were skipped: pinned-identity syntax validation, checkout, commit/tree verification,
frozen-artifact download, durable envelope creation, official runner execution, and evidence
generation. Therefore the frozen official vector remains unconsumed and outcome exposure remains
zero.

## v5 correction

v5 removes the fragile user-entered acknowledgement string entirely. Dispatching the uniquely named
v5 workflow is itself the explicit execution action. The workflow still:

- rejects GitHub reruns with `GITHUB_RUN_ATTEMPT=1`;
- requires exact commit and tree inputs and verifies them after checkout;
- verifies the exact frozen artifact archive and its internal checksum inventory;
- supplies the canonical acknowledgement internally to the already-reviewed execution loader/runner;
- preserves all four retired preflight-only run IDs in the execution envelope;
- remains authorized for exactly one v5 dispatch by the research protocol.

Runs `35611002403`, `35614730818`, `35619166656`, and `35621541278` are permanently retired and
must never be rerun.
