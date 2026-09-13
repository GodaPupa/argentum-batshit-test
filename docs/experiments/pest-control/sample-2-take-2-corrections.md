# Pest Control — Sample #2 Take 2 focused corrections

**SHARED ARGENTUM CHANGE: yes**

## Scope and preserved evidence

Sample #2 Take 2 was already rejected in full at
`2b770b353a3d3ec387e9c5607c938cbaa2007eaf`. Its complete 30-seed vector remains permanently
retired and hard-disabled. This correction used only its preserved raw trace, rejection audit, and
deterministic scenario construction. No retired seed or Pest gameplay vector was executed.

Pest Control v1.0 is unchanged. Accepted Sample #1 remains the sole Pest performance/engine sample,
and the challenger remains audit-only and unconstructed.

## Games 1, 8, and 30 — false superior land/setup telemetry

All three flags had the same cause. Immediately before the first turn-four Weather, another copy of
the same spell was in hand. The old detector excluded only the focal card entity, so it treated the
second Weather as a setup spell. After simulating the land it scored the complete
`land → Weather → Weather` result against only the first immediate Weather result. It did not score
the fully executable continuation `Weather → land → Weather`. Generic auto-payment could also choose
different colored sources in the two simulations, making feasibility and value depend on an
incidental tap choice. The allegedly superior sequence was outcome-equivalent: the land added no
Storm count, and both orders produced one Storm-0 and one Storm-1 Weather with the same resources and
resolved value.

The telemetry now:

- explores authoritative legal casts with explicit source choices as well as auto-payment;
- validates the land, colors, tapped status, costs, targets, subsequent focal cast, and same-window
  ordering by simulating the complete sequence;
- compares a proposed setup-first sequence with the best complete same-length focal-first
  continuation, rather than a truncated one-action baseline;
- reports separately a currently executable useful setup, a useful setup unlocked by a land, a
  setup still unexecutable after the land, and a complete but not materially superior sequence;
- raises the missed-superior flag only when a useful completed setup-first line exceeds that full
  continuation by the materiality margin.

Deterministic regressions reproduce the preserved Games 1, 8, and 30 double-Weather shapes and reject
their equivalent-order flags. A tapped-land reconstruction proves that a theoretically present line
still unable to fund both actions stays in the unexecutable category. The existing Game 16
reconstruction remains green: `land → relevant spell → Weather` is still detected because casting
the relevant spell first creates real order-dependent Storm/payoff value that the focal-first order
cannot recover.

## Games 9 and 29 — unproductive friendly removal

The target heuristic knew the Carrier was friendly and ranked it negatively, but it was the only
legal target. At the late terminal window, ordinary removal patience had fully expired and applied no
discount because it only evaluates opposing targets. The simulated leaf then credited the Carrier's
death replacement as generic positive board value. The final scorer therefore treated
`removal card + two mana + Carrier → unused smaller Scion` as a legal positive conversion without
pricing the lost future interaction option. Neither trace had a Warden trigger, same-turn Storm
setup, Scion-mana consumer, lethal, or other downstream benefit.

The shared removal valuation now recognizes structurally when a noncreature, one-card removal intent
targets exactly one friendly permanent and no opposing permanent. It keeps self-targeting legal, but
requires the resolved state to beat passing by an additional fair-trade margin after the ordinary
leaf/pass comparison has already accounted for the preserved permanent, spent card, mana, and
resolved death value. Deterministic lethal bypasses the hold, and any genuinely superior death
trigger, prevention line, resource transition, or engine conversion can still clear the value bar.
No card, deck, experiment, game number, or named permanent is hard-coded in production policy.

Deterministic regressions prove both preserved unsupported-death shapes are held; an opposing
high-value target remains preferred; holding remains preferred when neither a small opposing target
nor a friendly death is valuable enough; and a Carrier/Scion-style death with demonstrably superior
multi-payoff conversion remains available.

## Validation gate

Focused telemetry and production-agent decision regressions, the Pest rules/engine scenarios, the
frozen baseline guard, and the complete AI suite are green locally. Remote full-CI results are
recorded in `docs/lab/PEST_CONTROL_STATUS.md` when the gate reaches a terminal state. No replacement
vector may be generated before that gate is green and separately authorized.
