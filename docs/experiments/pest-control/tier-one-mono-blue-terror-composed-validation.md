# Pest Control Tier-1 coverage — Mono-Blue Terror composed VALIDATE_ONLY

## Purpose

This gate validates the complete operational stack without allowing an official frozen seed to
initialize a game.

The pull-request workflow downloads artifact `10733086089`, verifies the pinned outer archive
SHA-256, and passes the ZIP through the accepted frozen-artifact verifier and strict assignment
decoder.

Those official assignments are validated for identity and order only. They are never passed to the
authorized initializer or production driver.

A separate four-game synthetic shadow then exercises:

`DurableAttempts -> AuthorizedExecutionCoordinator -> AuthorizedInitializer -> ProductionDriver -> raw encoding -> ArtifactContract`

using four fixed synthetic seeds that are asserted not to overlap the official frozen vector.

## Acceptance boundary

A successful validation must show:

- official archive digest matches the frozen provenance;
- exactly four official assignment rows decode in canonical order;
- official seeds consumed: `0`;
- official games initialized: `0/4`;
- official actions submitted: `0`;
- official outcome exposure: `0/4`;
- synthetic shadow games: `4/4`;
- durable attempt / initialization / result evidence: complete and ordered;
- synthetic artifact reconciliation: valid.

Validation evidence contains only official artifact/hash metadata plus synthetic game records. It
does not publish official seed values.

After acceptance, the next gate may add one-shot official execution input/runner wiring. That gate
remains separate from this validation-only workflow.
