# v0.9 Phase 14 — Paired Capsize Policy Pilot Accepted

Disposition: `V09_PHASE14_PILOT_ACCEPTED`

## Provenance

- Control: `izzet-science/v0.7-control.md`
- Control SHA256: `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`
- Experimental source: `5a981b1f4a34b065af0630bf68113f7ac73e94b9`
- Experimental source tree: `777c2d4b0219871dbed04dfe4daa8a928cb0dace`
- Workflow runner: `bbb9387c4dcb8cda43f22b34a889fce0281560fe`
- Workflow SHA256: `dc49b1682743d30e788010f7b1bf33a912003600c75aa036901b22b64111578b`
- GitHub Actions run: [35551616504](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/35551616504), attempt 1
- Job: `106187284230`, `izzet-v09-capsize-paired-v3`
- Seed: `0x00000001A22E7012`, consumed and retired
- Matched pairs: 10,000; trajectories: 20,000; horizon: T1–T10
- Runner SHA256: `760ce035e6dc81fc001b2f080e87ebfda97be7444819a7a1e2fd7f3afb3eb5b3`
- Summary schema: `izzet-v09-capsize-paired-v2`
- Artifact schema: `izzet-v09-capsize-paired-artifact-v2`
- Artifact: `10618248257`, 3,302 bytes, unexpired at audit
- Artifact ZIP SHA256: `154c6d23b0d7ee857d61b3d6bfb1a1b5e2aa83f964f7770917a08425497fc69d`
- Summary SHA256: `e2e1f00d2543e05d5823dbd3c4aff71673ea736bddbbcc4640532f43265ac24d`
- Audit transcript SHA256: `cd1b15c186f3b0c685b1662f28a4675f8003b641ac3187248ae237ad88ce5777`
- Copied runner SHA256: `760ce035e6dc81fc001b2f080e87ebfda97be7444819a7a1e2fd7f3afb3eb5b3`
- Manifest SHA256: `62143bf8bbacb57799f6948f8013d5ef64fd0a64d895fb9a5b3aa94b2682384a`

Checkout, identity verification, the complete seed-free preflight, 1,024-pair
invariant qualification, sole paired execution, summary audit, manifest audit, and
artifact upload all passed. The downloaded ZIP digest matched GitHub's reported
digest. Its exact four nonempty files, manifest, transcript, runner, and all internal
hashes passed the independent auditor again.

## T10 paired results

| Metric | Control | Policy | Paired delta | 95% paired interval | Control-only / policy-only |
|---|---:|---:|---:|---:|---:|
| Capsize present | 16.96% | 41.11% | +24.15 pp | +23.31 to +24.99 pp | 12 / 2,427 |
| Capsize buyback ready | 15.11% | 37.50% | +22.39 pp | +21.56 to +23.22 pp | 18 / 2,257 |
| Primary pair | 10.02% | 10.26% | +0.24 pp | -0.08 to +0.56 pp | 125 / 149 |
| Primary lethal | 7.05% | 7.12% | +0.07 pp | -0.21 to +0.35 pp | 100 / 107 |
| Cumulative lethal | 7.07% | 7.13% | +0.06 pp | -0.22 to +0.34 pp | 100 / 106 |
| Commander battlefield | 95.02% | 94.94% | -0.08 pp | -0.33 to +0.17 pp | 85 / 77 |

Intervals are descriptive normal intervals for the mean matched-pair difference,
computed from the discordant cells; they were not used to alter or rerun the pilot.

By T10, 2,880 trajectories (28.80%) had acquired Capsize by tutor. The ledger
contained 2,883 acquisition events: 1,480 Merchant Scroll events and 1,403 Drift of
Phantasms events. The three events beyond first acquisitions confirm that the v2
reacquisition contract was exercised in the accepted run.

## Timing tradeoff

The policy spends mana early. At T3, commander presence was 55.45% in control and
53.40% under policy, a paired delta of -2.05 percentage points. The gap narrowed to
-0.27 points at T5 and -0.08 points at T10. Despite that early deployment delay,
the policy did not reduce primary-pair, primary-lethal, or cumulative-lethal rates at
T10 in this pilot.

## Decision

Accept the artifact and promote the Phase-5 Capsize tutor policy for future stateful
modeling: an executable primary-combo tutor action retains global precedence;
otherwise Merchant Scroll is preferred over Drift of Phantasms when Capsize remains
in the library and exact ready mana pays the search.

This is a behavior-policy promotion, not a card promotion. The experiment measures
access, readiness, primary assembly/lethal timing, and commander deployment in a
goldfish model; it does not establish Capsize interaction value, opponent disruption,
backup-plan wins, or match win rate.

Preserve the exact v0.7 decklist as the accepted card control. Retire seed
`0x00000001A22E7012` and the v3 runner; do not rerun, confirm, or pool this pilot.
Restore the ordinary workflow byte-for-byte. Future challenger work may use the
accepted policy but must retain this historical control and artifact provenance.
