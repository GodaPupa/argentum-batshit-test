# Pest Control Tier-1 Grixis — fifth preflight-only execution incident

GitHub Actions run `35625731799` is permanently retired as a fifth pre-execution infrastructure
failure.

The v5 run passed the rerun guard and then failed during the pinned-identity syntax check. GitHub's
log rendered both values as 40-character lowercase hexadecimal strings:

- commit: `9455460058f3c46e18f43619c5a542ea1d7500ea`
- tree: `3666c0db55f0e0d118fc9edee5c194a5e2ee0c16`

Nevertheless the shell validation returned nonzero. Checkout and every later step were skipped,
including frozen-artifact download, durable attempt creation, game initialization, action submission,
and outcome generation. The official vector therefore remains unconsumed.

## v6 correction

v6 removes all user-entered identity values. The uniquely named v6 workflow is itself the explicit
execution action. It checks out the exact workflow-dispatch commit using `github.sha`, records that
commit and its computed tree after checkout, verifies the frozen artifact, and then passes that exact
checked-out commit to the authorized runner.

The workflow remains non-rerunnable via `GITHUB_RUN_ATTEMPT=1`. Exactly one v6 dispatch is
authorized by the research protocol.

Runs `35611002403`, `35614730818`, `35619166656`, `35621541278`, and `35625731799` are
permanently retired preflight-only infrastructure failures and must never be rerun.
