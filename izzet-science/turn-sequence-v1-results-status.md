# Turn-Sequenced Mana Baseline v1 — Results

Control: Izzet Science?! v0.1
Freeze: 19cb49f91e5f4aebeb470a40a07cb6b7bb31b756
Protocol: 6f536a52c35425176a722f279643fd400fb40a5f
Mana semantics: 16222d134877f1dee76e2f0126143cbb2c702855

Execution note:
A trustworthy turn-sequence result requires an executable simulator implementing the frozen semantics and policy. No such simulator has yet been committed for Izzet Science. Therefore no numerical turn-sequence percentages are accepted in this record.

Why this matters:
- The earlier 100,000-hand baseline is a raw combinatorial sample and remains accepted.
- Turn-by-turn claims require stateful sequencing for tapped lands, Boilerworks returns, Ash Barrens cycling, Signet input, Chalice kick choice, summoning sickness, and conditional Fellwar Stone color.
- Producing percentages without that executable implementation would not be reproducible from the repository.

Required next artifact:
- deterministic simulator source
- exact PRNG/seed handling
- deck parser tied to frozen v0.1
- regression tests for each mana semantic
- output artifact generated from execution

Disposition: TURN_SEQUENCE_RESULTS_WITHHELD_PENDING_EXECUTABLE_HARNESS
