# Position 1 — exact real-engine bootstrap

Disposition: `ACCEPTED_SEED_FREE_BOOTSTRAP_COMPONENT_ONLY`

The accepted Phase-33 adapter still requires a qualified real gameplay engine. This
change supplies its initialization seam without reading an official seed, loading
the quarantined vector, or changing `KEEP_V07`.

## Boundary

`IzzetSciencePosition1Bootstrap` copies and checks the exact control and opponent
bytes against their accepted SHA-256 identities, then compares the parsed card
counts with both Kotlin runtime deck translations. It retains Izzet in seat zero
for either `play` or `draw`, with the exact commanders, 99-card mainboards, 30 life,
16 commander damage, seven-card London mulligans, and hand smoothing disabled.

The dormant internal initializer resolves every frozen identity before its seed
supplier can run. Unknown assignments, source drift, count drift, or unresolved
cards fail before seed access. The accepted attempt-before-reveal journal,
exclusive consumption, conditional Position-1 authorization, and independently
qualified source binding remain the future official adapter's obligations.
This component has no command-line entry point and issues no execution permit.

## Deterministic qualification

`IzzetSciencePosition1BootstrapTest` checks exact frozen bytes and translations;
stable seat assignments; changed-byte and assignment rejection before seed access;
the exact five-card rejection without a seed read; both initialization orders;
and charged London mulligan actions through serialized state/action replay.

The independent initialization and mulligan fixtures use public regression
coordinate `1`, the two exact commanders, and 99 existing basic lands per player.
Only test code replaces those fixture libraries after validating the full runtime
configuration. No missing identity is represented by a substitute definition.
These fixtures qualify the initialization seam, not the full frozen decks or a
gameplay pilot. Existing PDH commander/tax/damage fixtures and the complete
readiness inventory must pass alongside this test.

## Remaining admission blockers

The frozen registry still lacks Benevolent Blessing, Forge of Heroes, Opal Palace,
Snake Umbra, and Vines of Vastwood. Full-pair initialization, commander lifecycle,
damage accounting, and Phase-29 event-ledger extraction remain fail-closed in the
existing readiness gate. Executable pilots, full-game replay and the real engine's
binding to the accepted adapter require qualification before an official game.

Official counters remain 12 generated, 0 consumed, 0 initialized, 0/12 completed,
and 0/12 outcomes exposed. Deck changes: none.

## Accepted evidence

Source `a31d4eac79bd6eb50a3890e90fe50e68862bf565` and PR-177 combined source
`2029ab158862b274c710fc17c7e531704fa890d3` share exact tree
`7287bf827dd611af27ef2b57cdeca0b212e217c9`. CI `36244318267` passed all seven
test groups and the frontend/backend gates. The complete tools transcript proves
7 bootstrap, 7 adjacent PDH and 16 readiness cases passed, with no failed case.
A non-author agent independently fetched and audited the actual remote evidence
and the governing component requirements. The source-scoped receipt is
[`v09-position1-bootstrap-ci-audit.json`](v09-position1-bootstrap-ci-audit.json);
it binds the retained complete decoded transcript and every CI job.

This accepts the bootstrap component only. No full-game guard is removed, no
GitHub review submission is claimed, and no official execution permit is issued.
The known post-blocker-declaration priority defect also requires the canonical
repair and receiving-source qualification before interactive gameplay.
