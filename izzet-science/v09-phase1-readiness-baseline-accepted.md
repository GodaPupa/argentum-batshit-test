# v0.9 Phase 1 Commander-Independent Readiness — Accepted Baseline

Disposition: `V09_PHASE1_PILOT_ACCEPTED`

## Provenance

- Control: `izzet-science/v0.7-control.md`
- Control SHA256: `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`
- Source: `ea7549be570318e3ae2af64fd44d425f329db033`
- GitHub Actions run: `35544856015` / #181, attempt 1
- Job: `106168839094`
- Seed: `0x1A22E700E`, consumed
- Samples: 10,000
- Horizon: T1–T10
- Artifact: `10616381670`, 3,344 bytes
- Artifact ZIP SHA256: `054e5923176dd7046efd86d5c426def6526754aa190dcc36fa6bfdf1f86c469c`
- Pilot output SHA256: `2e38766bc7c3fa768ee8b588e23aee81364fff827d0ceefecd9e12172118a92e`
- Audit output SHA256: `efd78aecb6f282234e1a7e3c17936a95087d435b8769d230fbbd26ba4f95ddae`
- Manifest SHA256: `3ec66c9555523922fb513a81cc99dc4d0199bf6aa4f9c89016badc4e10cfa5c4`

The control hash, seed-free preflight, auditor self-test, sampled output audit,
manifest creation, and nonempty artifact upload all passed.

## T10 results

| Backup card | Present | Ready | Ready given present | Additional capacity |
|---|---:|---:|---:|---:|
| Murmuring Mystic | 16.26% | 16.00% castable | 98.40% | — |
| Rolling Thunder | 17.16% | 16.85% positive X | 98.19% | 15.37% of all trajectories at X>=5 |
| Kaervek's Torch | 16.80% | 16.42% positive X | 97.74% | 14.94% of all trajectories at X>=5 |
| Capsize | 16.92% | 15.39% with buyback | 90.96% | — |

Average maximum X across all trajectories was 1.1912 for Rolling Thunder and 1.1738
for Kaervek's Torch. Conditional on positive-X readiness, those averages were 7.07
and 7.15 respectively.

## Interpretation

By T10, mana availability is rarely the limiting factor after one of these singleton
backup cards is present. The larger observable constraint is access: each card is
present in only about 16–17% of trajectories. This supports investigating acquisition
or multifunctional access before adding another redundant finisher.

This baseline does not establish commander-independent damage, token production,
combat survival, a table kill, or a win rate. The simulator still observes these
cards without casting them. No deck change or promotion is authorized by this result.

## Control decision

Preserve v0.7 unchanged. Consume and retire seed `0x1A22E700E`; do not rerun or pool
the pilot. Use this accepted descriptive baseline only to preregister a distinct
future challenger or to justify stateful backup-line simulator work.
