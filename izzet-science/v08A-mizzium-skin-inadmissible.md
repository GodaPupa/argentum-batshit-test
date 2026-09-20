# v0.8-A Mizzium Skin — Inadmissible Pilot Rejection

Disposition: `V08A_PILOT_INADMISSIBLE_REJECTED`

## Provenance

- Accepted control: `izzet-science/v0.7-control.md`
- Control SHA256: `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`
- Challenger: `izzet-science/challengers/v08A-mizzium-skin.md`
- Challenger SHA256: `575bfc592082f5999ebc50cca348b1bfeb157e9fa27bff09b5e092cc1aa496d1`
- Frozen source: `1de9955f365533a848d33f434380e0aa15fd6eab`
- Seed-free validation: run `35539906654` / #177, success, sampled games 0
- Sole pilot: run `35539938514` / #178, attempt 1, failure
- Pilot seed: `0x1A22E700C`, consumed and retired
- Samples executed: 10,000 per deck
- Confirmation seed: `0x1A22E700D`, unused and permanently retired with the identity

## Why the run is inadmissible

The paired simulations completed and exposed all turn outcomes. The frozen comparator
then failed because pre-v0.8-A telemetry was not exactly equal. The failure stopped the
job before its provenance manifest and artifact upload steps, so the run is incomplete
under the frozen gate and cannot be accepted.

Audit identified two harness defects:

1. Mizzium Skin was registered as interaction and protection, but omitted from the
   one-blue actionable-spell class. This produced a false T10 U-execution difference
   (95.93% control versus 94.88% challenger).
2. The newly added ability event inherited a predicate that treated any broad spell
   counter as covering a targeted ability. Counterspell and similar cards cannot
   counter activated or triggered abilities. The reported ability-readiness values
   therefore do not represent the preregistered event.

The logged T10 ability values and apparent delta are contaminated by defect 2 and make
no performance claim. They are not eligible for pooling, threshold evaluation, or
promotion.

## Decision

Reject v0.8-A permanently. Do not rerun it under a replacement seed or repaired model,
and do not authorize confirmation. Correct both harness classifications only for
future, distinct hypotheses. Preserve v0.7 unchanged as the accepted control.
