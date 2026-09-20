# Gate 4 Madness Burn policy audit v1

Status: planned; seed-free deterministic diagnosis.

## Question

Did frozen v0 underperform with the sourced Madness Burn list because it cannot execute the deck's
identity-critical lines, or did the replication simply expose a favorable Industrial Waste matchup?

## Fixtures

The audit uses no deck shuffles and no experimental namespace. It requires v0 to:

1. discard Sneaky Snacker to Grab the Prize when drawing two will trigger its return;
2. deploy Guttersnipe before spending a same-turn Grab the Prize;
3. deploy Kessig Flamebreather before a same-turn Grab the Prize; and
4. use Fireblast and Lava Dart land-sacrifice costs when each is immediately lethal.

Any failed fixture blocks more matchup sampling. A full pass clears the policy concern raised by the
replication but does not retroactively change its failed gate or promote either Industrial list.
