# Face Value postboard Stage 1 — preserved-evidence gameplay review

Date: 2026-09-21
Source qualification run: `35543054441`
Source artifact: `10616218744`
Artifact digest: `sha256:e5015966ac764f7e5991572b82788bdd8aba6a4e8f05253f7f19095deea8e5eb`

## Method

No qualification seed was rerun.

The preserved raw-log archive was audited directly. For each target matchup and each Face Value seat, two games were selected outcome-independently by lowest SHA-256 rank of:

`face-value-postboard-stage1-gameplay-audit-v1|<seed>`

This produced four trace samples per matchup. In addition, all 32 logs in each block were scanned for terminal/runtime defects and role-critical card execution.

## Global mechanical findings

For all five reviewed blocks:
- 32/32 preserved logs present.
- No Exception, UnsupportedOperation, slow-match cutoff, or qualification process-exit marker found.
- Mulligan/keep and terminal-result logging were present in sampled traces.
- Sampled Face Value play showed normal engine development, sideboard use, legal interaction, and emerge/cast-trigger behavior.
- No material sampled targeting or sequencing defect was found.

## Block dispositions

### Grixis Affinity — ACCEPTED_POSTBOARD_SIMULATOR_EVIDENCE

Recorded score: **26-6**.

Role-critical execution across 32 logs:
- Reckoner's Bargain casts: 47
- Krark-Clan Shaman casts / activations: 56 / 11
- Cast into the Fire casts: 39
- Extract a Confession casts: 20

Outcome-independent sampled indices:
- Face Value seat 1: 129, 115
- Face Value seat 2: 130, 32

Sampled traces showed functional sacrifice/value play, Shaman access, artifact interaction, Face Value Relic/Ancient Grudge usage, and normal terminal bookkeeping.

### Jund Wildfire — ACCEPTED_POSTBOARD_SIMULATOR_EVIDENCE

Recorded score: **24-8**.

Role-critical execution:
- Krark-Clan Shaman casts / activations: 37 / 5
- Cleansing Wildfire casts: 37
- Duress casts: 39

Sampled indices:
- Face Value seat 1: 75, 5
- Face Value seat 2: 188, 48

No material sequencing, targeting, runtime, or terminal defect found.

### Mono-Blue Terror — ACCEPTED_POSTBOARD_SIMULATOR_EVIDENCE

Recorded score: **25-7**.

Role-critical execution:
- Lórien Revealed cast/use traces: 14 casts plus 20 activated uses
- Tolarian Terror casts: 28
- Counterspell casts: 38
- Hydroblast casts: 10

Sampled indices:
- Face Value seat 1: 161, 21
- Face Value seat 2: 8, 120

The Unicode-normalized Lórien path was exercised. Countermagic/threat execution and Face Value postboard interaction were present without a sampled material defect.

### Mono-Red Madness — ACCEPTED_POSTBOARD_SIMULATOR_EVIDENCE

Recorded score: **28-4**.

Role-critical execution:
- Faithless Looting casts: 45
- Highway Robbery casts: 47
- Fireblast casts: 8
- Face Value Weather the Storm casts: 23

Sampled indices:
- Face Value seat 1: 23, 93
- Face Value seat 2: 220, 122

The previously remediated discard/draw and burn paths were naturally exercised. No material sampled pilot or terminal defect found.

### Monster Tron — ACCEPTED_POSTBOARD_SIMULATOR_EVIDENCE

Recorded score: **25-7**.

Role-critical execution:
- Prophetic Prism casts: 20
- Expedition Map casts / activations: 37 / 36
- Crop Rotation casts: 27
- Bramble Wurm casts / activations: 16 / 4
- Face Value Ancient Grudge casts: 25

Sampled indices:
- Face Value seat 1: 223, 55
- Face Value seat 2: 196, 42

The prior Prism coverage defect is not present in this postboard profile: Prism was naturally cast. Sampled traces showed functional Tron setup, threats, artifact interaction, and legal Face Value emerge lines.

## Accepted subtotal

These five independently accepted postboard simulator blocks total:

**128-32 (80.0%) across 160 games.**

This is an unweighted engineering subtotal over these five matchups only. It is not a metagame-weighted win rate, does not include accepted Elves replacement authority, does not include Rally, and does not by itself establish Tier 1 status.

## Remaining gate

- Elves: use separate accepted replacement authority, 13-19.
- Mono-Red Rally: Stage 1 result remains quarantined. Diagnostic sequencing replication has no qualification authority. Freeze and execute a fresh Rally replacement block before any seven-matchup postboard coverage summary.
