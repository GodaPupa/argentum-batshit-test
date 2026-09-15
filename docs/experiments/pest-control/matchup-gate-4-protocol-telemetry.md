# Pest Control Matchup Gate 4 — preboard protocol and telemetry contract

## Frozen identity and boundary

- Protocol: `PEST_CONTROL_V10_VS_MONO_RED_MADNESS_SOTERX_2026_09_11_PREBOARD_V1`
- Construction base: `b6fc0fb6fc31efa2148e3e3174782d267656e44c`
- Pest Control v1.0 maindeck SHA-256:
  `7be61a66e2c7654428043d56b411afb4d406f02dfcc4eb7f15a62295d4e906f5`
- Pest Control v1.0 sideboard SHA-256:
  `c1910468c228662b21647eb7ca8481cd11691906e56e0a37990e00f22886368c`
- Pest Control v1.0 complete-75 SHA-256:
  `2927737eb084657cda58fd1877db933037c383273062f0bd209c7ff3046c1cf5`
- SoterX Mono Red maindeck SHA-256:
  `38c7850d1b9b070637502cedfffc6116d3504a525db8b51223505d7935134258`
- SoterX Mono Red sideboard SHA-256:
  `d0aab592e6c82ad019dba0eadc028db75ef5cf13c9b3e4e0531a04d513bfc77a`
- SoterX complete-75 SHA-256:
  `e9ff7ecbdbc8f41ebe526fe8fee4f87f706e0630491f9d78121677922fbd647d`
- Scope: independent preboard games. `match_result` is always
  `NOT_APPLICABLE_PREBOARD_INDEPENDENT_GAMES`.

Both complete frozen 75s remain provenance identities, but the driver instantiates only each exact
60-card maindeck. Neither frozen sideboard is placed in a game zone. Both players start at 20 life.
Pest's seat and the starting deck are independent parameters for a later, separately authorized
25/25 play/draw allocation.

Gate 4 creates no experimental seed vector and runs no sampled game. Every construction fixture uses
an identifier beginning `NONEXPERIMENTAL_GATE4_`; its fixed entropy is fixture material, is explicitly
not an experimental seed, and is excluded from every future seed-overlap registry.

## Authoritative advancement boundary

The protocol submits only one selected `GameAction` at a time through
`GameEnvironment.stepExactlyOne`. It never calls `GameEnvironment.step`, never automatically answers
a decision, and never quietly passes priority. A cast remains on the stack until each real priority
pass is separately selected and recorded. Pending decisions likewise require their own explicit
`SubmitDecision` action.

Before each action the driver constructs the acting player's normal masked `TrainingObservation`.
That is the decision-facing information set. A separate reveal-all observation and full zone/state
snapshot are written only to the omniscient audit record and are never returned in the player
decision context. Legal-action and state digests are deterministic SHA-256 values.

An engine rejection, wrong-player action, illegal fallback, wedge, watchdog, action limit, or turn
limit produces a `ProtocolDefect` and rejects the record. It is never serialized as a draw, terminal
game result, or ordinary match outcome.

The production London mulligan evaluator and bottom-card policy are applied to the real opening
hands. Every keep, mulligan, and bottom action crosses the same exact-one processor boundary and
records the pre-action hand, mulligan count, and bottomed entity identities.

## Raw telemetry contract

The canonical raw JSON records:

- protocol/schema/source commit, both frozen list identities, both agent profiles, JVM/OS/locale/
  timezone, Pest seat, starting deck, preboard scope, and sideboard non-instantiation;
- both ordered opening libraries and their digests, opening hands, and exact zone counts;
- all mulligan and London-bottom actions;
- every priority point: phase, step, active player, priority holder, acting player, pending-decision
  type, player-visible digest, omniscient digest, legal-action hash, exact serialized action, targets,
  modes, costs/payments, before/after state and digests, emitted events, processor acceptance,
  rejection, and fallback status;
- ordered player life, mana, hand/library/graveyard/exile/battlefield/sideboard contents, stack, combat,
  terminal result, terminal reason, and winner;
- a raw-event index for Warden/Researcher/Mascot; lifegain and payoff counters; Weather and Follow;
  Carrier Thrall/Scion; removal; mana and Jungle Hollow; burn targets; madness/discard outlets;
  Sneaky Snacker; Fireblast/Lava Dart; Moxite/Robot; and Guttersnipe/Flamebreather.

The index contains references only. It cannot introduce facts absent from the canonical action,
event, or state snapshots. Mana/color/tapland constraints and stranded functional roles are derived
from the ordered decision snapshots, legal-action sets, actual payments, and subsequent raw actions;
they may not be inferred from a report narrative.

## Frozen derived definitions

All definitions below are computed solely from raw actions, events, and their attached state/turn
snapshots.

- **Meaningful development:** the earliest turn with a zone change putting a nonland permanent onto
  the battlefield, nonzero damage, nonzero life gain, or a spell/ability that removes an opposing
  battlefield object.
- **Weather stabilization:** a Weather the Storm cast for which ordered raw events through the next
  priority boundary show life gained and its controller does not lose before receiving that next
  priority.
- **Productive removal:** a removal action targeting an opposing object whose caused raw events move
  that object from the battlefield, prevent deterministic lethal, or produce a strictly favorable
  immediate resource transition.
- **Survival duration:** for Essence Warden, Blood Researcher, or Pest Mascot, the number of completed
  turn changes from its battlefield-entry event through its battlefield-departure event or the game
  terminal event. Same-turn departure is zero.
- **Lethal/loss turn:** the state turn number on the first `GameEndedEvent`. Its mechanism is classified
  only from preceding ordered damage, empty-library draw, concession, or explicit loss events.

## Artifact contract

Raw JSON is canonical Kotlin serialization with defaults and nulls retained and a trailing LF. The
human report is produced only by decoding those raw bytes. The manifest binds protocol, schema,
source commit, all six maindeck/sideboard/complete-75 SHA-256 identities, and SHA-256 identities for
raw JSON, report, and compressed artifact.

Compression is deterministic RFC 1952 gzip: DEFLATE body, MTIME zero, XFL zero, OS 255, and canonical
CRC32/input-size trailer. Rebuilding identical raw bytes must yield byte-identical JSON, report,
manifest, and gzip. Verification recomputes every hash, recompresses the raw bytes, re-renders the
report, reconciles protocol/source identities, and rejects tampering.

## Gate 4 deterministic validation matrix

| Requirement | Fixture evidence |
| --- | --- |
| Frozen 60/15/75 identities, 20 life, two players, no instantiated sideboards | `PestControlPreboardProtocolTest` frozen-identity fixture |
| Independent seat/start parameters | seat-one/Pest-plays fixture |
| London mulligan and bottom recording | validated London-policy fixture |
| Exact-one advancement and spell response | exact-one Bolt/priority fixture |
| Combat and noncombat terminals | deterministic combat terminal and concession fixtures |
| Hidden-information separation | masked opponent hand/library fixture |
| Stable state/legal-action digests | unchanged-decision digest fixture |
| Rejection and limit handling | protocol-defect fixtures |
| Deterministic JSON/gzip/report/manifest | `PestControlMatchupArtifactContractTest` |
| Historical defaults and explicit round trip | artifact compatibility fixture |
| Tamper rejection and report-from-raw | artifact reconciliation fixtures |

These fixtures are construction evidence only. They are not matchup observations, are inadmissible
for performance inference, and cannot be pooled with any goldfish or future matchup sample.

## Gate boundary

Gate 4 acceptance requires focused tests, full CI, and Argentum Validation on one exact published
candidate. All 13 Pest gameplay runners remain disabled. Gate 5 seed generation is authorized only
after the sideboard/75 provenance correction is accepted; every matchup game remains blocked.
