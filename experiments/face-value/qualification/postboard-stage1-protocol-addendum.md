# Postboard Stage 1 protocol addendum

Status at map freeze: the frozen control remains unchanged; seven simulator matchups have accepted preboard Stage 1B blocks; Mono-Blue Faeries remains human-only; no postboard diagnostic or qualification outcomes exist.

## Map construction

`postboard-map-v1.json` is the immutable first postboard map. Every exchange is legal against the frozen 15-card sideboard and leaves a 60-card main deck plus 15-card sideboard. Choices are based on card roles, exposed mechanics, and representative-list composition—not observed Stage 1B matchup percentages. The map digest is frozen separately.

The map intentionally omits Raze and Flash of Defiance because they are not necessary to a defensible Rally plan and would expand the Forge-remediation surface. It uses color blasts and Relic of Progenitus, whose pinned Forge scripts carry `AI:RemoveDeck:Random`; profile v3 removes only those five deck-construction exclusions and changes no card rules or decision code.

## Capability gate

The first postboard run is diagnostic only: four fresh assignments per simulator-valid matchup, balanced 2/2 by Face Value seat. Seeds 730001–730028 and all outcomes are permanently retired and have no qualification, structural-diagnosis, tuning, or challenger authority.

The gate requires all 28 games to terminate cleanly, at least one mapped-in card exposure in every matchup, blast casting across the aggregate, and both casting and activation of Relic of Progenitus. Failure preserves and quarantines the block; remediation must use new diagnostic seeds. Passing the automated gate authorizes gameplay review, not qualification evidence.

## Qualification boundary

Official postboard seeds cannot be created until the diagnostic artifact receives gameplay-quality review. Mono-Blue Faeries cannot enter any Forge aggregate. No postboard result may modify the permanent control or retroactively alter this map.
