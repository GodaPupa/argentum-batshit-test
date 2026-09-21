# v0.9 Position 1 — Execution Adapter Qualification Gate

Disposition: `V09_POSITION1_EXECUTION_ADAPTER_GATE_OPEN`

Parent acceptances:
- Phase 31 seed vector freeze accepted.
- Phase 32 execution readiness accepted.
- Phase 33 position-1 authorization accepted.

Purpose: qualify the adapter that bridges the accepted opaque position-1 binding
into a separately qualified gameplay engine while preserving durable
attempt-before-seed-reveal, one-shot consumption, terminal rejection, and canonical
ledger requirements.

This gate remains synthetic-only. It does not download the official quarantine
artifact, reveal an official seed, initialize an official game, or expose an
official outcome.

Exit criterion: fresh CI must pass synthetic success/failure/replay fixtures and
show no official execution path. Only after acceptance may a real gameplay-engine
identity be bound.
