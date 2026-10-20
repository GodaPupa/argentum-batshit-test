# ARGENTUM — ANIMAR PRECISION R5 THIRD-TURN MANA REALIZATION

**STATUS: THREE SOURCE-BOUND THIRD-TURN REAL-ENGINE CONTINUATIONS SUCCESSFUL · NO NEW FULL GAMES · NO POLICY PATCH · INCONCLUSIVE**

Report date: October 20, 2026. This is strictly opt-in research evidence, not promotion or qualification of a deck or AI policy.

## Authorities and immutable original evidence

- Repository: GodaPupa/argentum-batshit-test.
- Frozen Manual Transmission v0.7 remains Git blob c350bdc86eb2d37e5da4eb712e8ee2fa729b7e89; do not rewrite it based on its historically inconsistent declared SHA-256.
- Accepted R5 AI policy: manual-transmission-r5-survival-opt-in. AiProfile.CURRENT unchanged.
- R1 original four-player run: [39477314282](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39477314282), source 84e6347bbcdfa2990eb304051771007daf849ce9; four complete games, 0 engine rejections. No subsequent full games authorized or executed in R2–R5.
- R2 six-shock exact forensics: successful run [39483315564](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39483315564), artifact 12292402933, original ZIP digest SHA-256 cf60d970a24ff9aefda61cf27b19c5631974f01ba67bd8353c2c02eba5b975e9. Replayed 1,601 matching original actions and their event rows, with 0 ability-ID rebindings.
- R4 accepted second-turn execution: [39490896191](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39490896191), execution SHA a8fdd3d072167d824468d0e7073b3f8f264e6a72, tree 165c115d1233c03d3e0f76c12c7b3db68c971467, original artifact 12296381441 and ZIP SHA-256 cfef51e23fe01f3676d9fc06821ace344b35460d4bdd31a884105968c7424a22.
- Three byte-pinned R4 turn-six states: NO=1a13bbc3f452f799759f06272e61c1b832f05caa0a257c27c7492e102357fe71; YES= a707fb6e95b9303b82d37e2c5c40fd34eecbdba9e3039581a211c21d6d1530e4; forced-elf=ce85f9fe2099d96f2bd61067e3a661a802a9773506bd693fc162c799a8ee582b. Exact R4 combined input report JSON SHA-256 6f95f8b19e4b7b892677458dd2a4d458b24d909501068374a8ed3556254912a5. Each independently checked.
- **R5 isolated execution branch:** research/manual-precision-r5-third-turn-20261020.
- **R5 execution commit:** 852128f460e75d6a4e53a9dc181ca288587b304c; Git tree e14b188d04635c852a1abf60bb8ed034ba7ad96e. Exactly two files added above pinned R4 source: one scoped Kotest and one scope/seed/byte-bound CI workflow. No production code, engine code, frozen deck, main, CURRENT, existing R5 policy or accepted historical test was modified.
- **R5 Actions:** [39495674132](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39495674132), attempt 1, SUCCESS. Original artifact ID 12298541181; SHA-256 **fa6dd3471ffe246c0c40ad8e107ff326a082baa864b02324bb0a893b0acd8dbb**, downloaded and independently verified. Contains original-source receipts, test stdout/JUnit, three bounded action traces, end-state snapshots and the combined structured comparison.

## Mandatory experimental boundaries

Starting states were the three exact archived R4 GameState snapshots at global turn 6, Seat 2 upkeep. Every subsequent action was selected using unchanged R5 across all four seats. Stop as soon as global turn 10 begins, before taking any action on that turn. Each branch has strict 650-action and 95-second local bounds. No full games, fresh seeds, outcome rerolls, synthetic effects, forced R5 third-turn actions, or B/C challenger substitutions.

All three branches naturally reached global turn 10, with zero rejected actions, zero censors, no game-over and no additional full game. The original R2/R3 forced action was already part of one accepted R4 input branch and was not repeated in R5.

## The three actual third-turn results

| Metric at start of global turn 10 | NO + unchanged R5 | YES + unchanged R5 | YES + forced turn-one Elf, then unchanged R5 |
|---|---|---|---|
| R5 accepted actions after R4 boundary | 146 | 147 | 155 |
| Pilot life | 40 | 38 | 38 |
| Land played on pilot's third turn | Stomping Ground | Stomping Ground | Stomping Ground |
| Third-turn shock payment | No (enter tapped) | No (enter tapped) | No (enter tapped) |
| Lands in play | Breeding Pool, Rockfall Vale, Stomping Ground | Same | Same |
| Untapped lands at end of pilot turn | 1 | 2 | 2 |
| Cloud of Faeries cast on turn three | Yes, spent U/R | Yes, spent U/R | Yes, spent U/R |
| Arbor Elf cast on turn three | Yes, spent G | No | No |
| Arbor Elf on battlefield at cutoff | Yes, newly cast | No, killed by opponent | No, killed in R4 |
| Frog Lizard token at cutoff | No | Yes | Yes |
| Actual Elf activated to untap a Forest on third turn | No | No | No |
| Additional mana actually generated/spent due to Arbor Elf on third turn | **None evidenced** | **None evidenced** | **None evidenced** |
| First legally affordable Animar cast offered in the observed windows | None | None | None |
| Actual Animar cast | None | None | None |
| Rejected actions / censors / full games | 0 / 0 / 0 | 0 / 0 / 0 | 0 / 0 / 0 |

Each branch drew Temur Sabertooth on the pilot's third turn. Each ended with the same five hand card names (Chaos Warp, Emrakul's Hatcher, Vizier of the Menagerie, Cinder Glade, Temur Sabertooth), but the in-play creature composition and exact mana expenditure differ.

**NO + R5:** Cast Cloud of Faeries using blue and red mana from Breeding Pool and Rockfall Vale. Its ETB untapped those two lands. Played Stomping Ground, declined paying two more life, then spent one green mana from Breeding Pool to cast Arbor Elf. Elf resolved and survived at the turn-10 boundary, but its summoning sickness means there was no third-turn untap activation or extra mana generated by that Elf.

**YES + unchanged R5:** Before Seat 1's third-turn main phase, during its upkeep at source-bound R5 action 103, Seat 2 (manual-mirror-1) cast Rapid Hybridization for blue mana, targeting Arbor Elf, which had been cast on turn two in R4. At R5 action 107 the original ActionProcessor resolved the spell: Elf left the battlefield and Seat 1 received a 3/3 Frog Lizard. Consequently, YES/R5 had no Elf available for third-turn activation, but did receive a 3/3 body and the opponent expended a removal spell. Later it cast Cloud of Faeries and played tapped Stomping Ground. Its two-life cost from original turn one was not recovered. No Arbor Elf-derived third-turn mana was spent.

**YES + original forced Elf:** The opponent had already cast Rapid Hybridization in R4 against the forced turn-one Elf. On third turn R5 cast Cloud of Faeries, played tapped Stomping Ground and attacked with its Frog Lizard token, which was blocked by an opposing Delighted Halfling. No Arbor Elf remained to untap any Forest or generate extra third-turn mana.

## Source-bound action and snapshot hashes

- Combined R5 comparison JSON: SHA-256 84e1d5e0be80bc81940904933f7697183aaab46d3813a6e7aceb2e832dd8f068.
- NO actions JSONL: bb0f95df1667b721d7731e2b4911b1e7ea4b66b1c8d297f5e24495266af0866a.
- YES/R5 actions JSONL: 8d0142bc26cd28df759b3b119f43c97032551adca033b2bfc5dea06b701a4031.
- Forced-Elf actions JSONL: c1986391b009193f04c6e877b338774f5c4d1390f7a43cd373c067c5c1acb0db.
- NO end state JSON: df46ce178ab54c3c0ec68baa0ce225bb20952333ead69e3158958d809c725d5d.
- YES/R5 end state JSON: 6670417d2ee8f81a391699d5098c6fb0a3841a3b3e7a99c98794624281cded62.
- Forced-Elf end state JSON: d96be73231cac214e1b43607fb455a87e61abdf51defaa6a3f40b3fba9100d41.

## Strategic interpretation and held-out protections

1. It is **proven** that an early payment changed the development sequence, moving Forestcycling into turn one and allowing unchanged R5 to deploy Arbor Elf in turn two rather than turn three.
2. It is **not proven** that this faster deployment produced superior third-turn mana or an earlier Animar. It did not: real opponent interaction destroyed the earlier Elf on turn three before the pilot could use it. The opposite NO branch saved two life and got Elf into play at the end of turn three. The YES branch spent two life, drew a removal spell from an opponent, and retained a 3/3 Frog Lizard; either could be better depending on longer-term combat and opponent behavior.
3. All branches used the same unchanged profile, but cycling/shuffling and combat legitimately caused action/opponent-state divergence. The results are exact-state case forensics, NOT proof of uniform performance over unseen games.
4. The R2 archived **held-out source-bound decisions** show why any unconditional pay-two-life override would be unsafe: Steam Vents at original action 468 and Breeding Pool at 1002 unlocked no additional affordable hand spells; Stomping Ground at 599 unlocked Fyndhorn Elves/Utopia Sprawl (potential development, still needs strategic valuation); Stomping Ground at 273 unlocked Chaos Warp (availability is not a reason to burn removal immediately). Both no-unlock controls should preserve a reasonable decline. No new held-out policy regression has been run because no policy patch has been authored.
5. The inherited race-clock sentinel and 141.9 forced-Elf evaluator score are **invalid measures of early mana or win probability**. In particular, the legacy ThreatAssessment's 99-turn no-attacker clock produces misleadingly large nominal creature values. Evaluate actual event records, mana spent, land taps, legal casts and outcome constraints instead.
6. Because R5 produced no demonstrated third-turn extra mana and no legal early Animar window, the evidence does not support a general or narrowly scoped two-life payment advisor **yet**. No changes to global weights or accepted R5 policy are justified from this one decision.

## R5 decision and next bounded action

**Final disposition: INCONCLUSIVE — LEGAL DEVELOPMENT DIFFERENCE, NO PROVEN REPRODUCIBLE STRATEGIC DEFECT OR POLICY IMPROVEMENT.**

No opt-in policy repair implemented. The user-authorized R5 request forbids new full games; none were executed. Frozen v0.7, AiProfile.CURRENT, accepted R5 survival/combo protections, main and original R1–R4 evidence remain unchanged. The source-only R5 branch is not qualified, merged or promoted.

**Best next strategic delta:** Stop extending a single selected seed's microfork indefinitely. Resume the independent, prospective, three-new-seed CONTROL-vs-Challenger-A matched gameplay program with the same pinned R5 policy for both arms, if separately authorized. Predeclare three novel seeds before execution and run six complete four-player games maximum, without reseeding or policy tweaks. Preserve the original 12-game cap: R1 used four, eight are still available and six would leave two. Record actual land entry payments, mana sources, Animar first cast and survival, and combo conversion in addition to raw winners. The matched games would test whether the land upgrades actually improve the physical Animar build, without the confound of an AI-policy candidate added mid-study.

Challenger B remains blocked until a rules-faithful Fierce Guardianship implementation is compiled/tested; Challenger C remains a separate unpromoted candidate.
