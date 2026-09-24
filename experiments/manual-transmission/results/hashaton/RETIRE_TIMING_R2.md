# Hashaton timing fixture R2 — RETIRED BEFORE DEVELOPMENT

Status: **RETIRED BEFORE DEVELOPMENT / QUALIFICATION**

R2 repaired the Lion's Eye Diamond multi-creature/multi-trigger defect and its GitHub run #10 passed 22 fixtures. A subsequent audit identified a second lifecycle edge in Tishana's Tidebinder itself:

- if Tidebinder leaves before its ETB resolves, the targeted activated/triggered ability is still countered;
- however, the source does not lose its abilities;
- if Tidebinder leaves after its ETB has blanked the source, the blanking effect ends;
- if the Tidebinder spell is countered, there is no ETB at all.

R2 remains preserved as useful intermediate evidence but is not the final Phase-B gate.

Superseding protocol: `MT_HASHATON_TIMING_FIXTURES_R3_2026_09_23`.

No development outcomes, qualification seeds, or qualification outcomes were generated before this retirement.
