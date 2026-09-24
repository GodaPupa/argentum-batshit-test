# Pest Control Tier-1 qualification — postboard support audit boundary

This is a seed-free inventory gate for the fixed Tier-1 gauntlet.

It audits the frozen 15-card sideboards for:

1. Pest Control v1.0;
2. SoterX Mono Red Madness;
3. Pasquale Grixis Affinity;
4. Serpico_CC Mono-Blue Terror;
5. mehanske Monster Tron;
6. Dr_dej96 Spy Combo.

It does not choose boarding plans, change a deck, generate a seed, initialize a game, submit an action,
or expose an outcome. The report exists only to batch implementation work before the postboard
robustness smokes.

No opponent is added by this audit.
