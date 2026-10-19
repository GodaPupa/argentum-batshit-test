Observed 40 attempts in 20 matched cells: 38 completed, 2 incomplete. Full requested schedule observed: True.

| Arm | Attempts | Complete | Designated-seat wins | Designated casts | Deployment games | Mean first personal turn¹ | Accepted actions, all attempts |
| --- | --- | --- | --- | --- | --- | --- | --- |
| control | 20 | 20 | 5 | 18 | 14 | 8.64 | 75708 |
| candidate | 20 | 18 | 6 | 22 | 14 | 7.14 | 62283 |

| Seat | Wins C / R | Complete C / R | Animar casts C / R | Deployment games C / R | Mean first personal turn C / R¹ |
| --- | --- | --- | --- | --- | --- |
| 1 | 1 / 1 | 5 / 5 | 7 / 6 | 4 / 4 | 5.25 / 5.75 |
| 2 | 2 / 2 | 4 / 5 | 6 / 4 | 4 / 4 | 8.25 / 11.50 |
| 3 | 2 / 1 | 5 / 5 | 5 / 5 | 3 / 3 | 6.33 / 7.33 |
| 4 | 1 / 1 | 4 / 5 | 4 / 3 | 3 / 3 | 9 / 10 |

| Seat | Arm | Available cast windows² | Declined windows² | Mean global turns³ | Mean accepted actions³ |
| --- | --- | --- | --- | --- | --- |
| 1 | control | 102 | 96 | 84.60 | 3785.40 |
| 1 | candidate | 37 | 30 | 62.80 | 2807.20 |
| 2 | control | 34 | 30 | 84.60 | 3785.40 |
| 2 | candidate | 22 | 16 | 58.75 | 2629 |
| 3 | control | 56 | 52 | 84.60 | 3785.40 |
| 3 | candidate | 38 | 33 | 67.80 | 3165.20 |
| 4 | control | 31 | 28 | 84.60 | 3785.40 |
| 4 | candidate | 21 | 17 | 78.25 | 3505 |

Advisor overrides: 31 scored windows; 17 chosen adjusted options; 17 changed rankings. 16 changed rankings were followed by a same-turn creature cast. Absence of a follow-up alone is not classified as an error without checking responses; the concrete turn-39 counterexample is documented below. All reference controls have zero advisor notes.

control: 0 repeated same-turn Statue cast groups, 36 accepted Ballista casts and 64 accepted Ballista activations. These are observed actions, not inferred intentions. The unbounded Statue sequence is analyzed separately.

candidate: 1 repeated same-turn Statue cast groups, 33 accepted Ballista casts and 60 accepted Ballista activations. These are observed actions, not inferred intentions. The unbounded Statue sequence is analyzed separately.

¹ Conditional on an observed cast, including casts before incomplete attempts stopped; never-cast games are counted separately. C=candidate pilot, R=the same designated reference seat.

² Repeated priority decisions count separately. An available-but-declined cast is not automatically a mistake. Counts include incomplete prefixes.

³ Completed games only; global turns advance across all active players, not multiplayer rounds.

Commander-cast counts are verified against the four physically designated commander IDs and include hand recasts: designated reference pilots cast 17 times from command and once from hand; designated candidate pilots cast 22 times from command. Across all four pilots, control pods cast commanders 72 times and candidate pods 73 times. Available-window counts concern command-zone opportunities only. Shorter completed-game averages exclude the two incomplete candidate games and are not evidence of improved performance.

Only five independent seed setups were used. Four separately executed controls per seed are rotations of the designated comparison seat; they are not twenty independent shuffled games. This exploratory sample does not establish superiority.
