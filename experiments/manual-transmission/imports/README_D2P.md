# Manual Transmission — D2-P Up the Beanstalk vs Destiny Spinner

## Decision
Manual Transmission **v0.7 remains frozen**. **Up the Beanstalk -> Destiny Spinner is rejected for promotion.** Together with D2-O's rejection of Lifecrafter's Bestiary -> Spinner, this closes Destiny Spinner as an active cut candidate for now.

## Exploratory evidence
Two fresh 512-start Race-R3 all-route blocks showed the candidate was promising: combined T4 access remained essentially flat (86 control vs 88 Spinner), while ordinary-counter protection improved (Force-only 19 -> 32) and several two-stage shapes improved. Two matched fair-floor blocks showed the low-gear cost was smaller than cutting Bestiary: by T7 the Spinner arm was roughly 0.25-0.28 draws/start behind Beanstalk while board development remained close.

## Fresh confirmation rule
The protocol was written before confirmation seeds. Promotion required all of:
1. T4 access no more than 1.5 percentage points below control.
2. Among T4 access states, Force-only protection improves by at least 5 percentage points.
3. No regression in raw Silence->Force, blue-bounce->Force, or Swords->Force counts.
4. Cruise/Sport T7 mean draw loss <=0.35 and mean cast loss <=0.15.

## Confirmation result
Race block: 1,024 fresh matched starts. Access 82 -> 85 (+0.293 pp). Force-only rate improved **+4.849 pp**, narrowly missing the required +5 pp. Silence->Force improved 2 -> 4 and Swords->Force tied 5 -> 5, but blue-bounce->Force regressed **9 -> 7**, violating the no-regression rule.

Floor block: 512 fresh matched starts. The fair-floor thresholds all passed: Cruise T7 draw/cast deltas were -0.244/-0.123; Sport -0.275/-0.133. Thus the rejection is **not** because Spinner destroys Cruise/Sport; it is because the fresh Race confirmation did not reproduce enough broad protection advantage to justify removing Beanstalk.

## Interpretation
Spinner is a real Manual Transmission card, but neither Bestiary nor Beanstalk is an evidence-supported cut. The incumbent v0.7 keeps both fair engines. No thresholds were relaxed after seeing confirmation.

## Limits
These are bounded development/protection models, not completed Commander games or matchup win rates. Spinner land-animation combat remains outside the floor model. Chord access assumes Chord resolves in the Race screen.