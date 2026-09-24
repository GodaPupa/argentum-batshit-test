# R3-M — Magda / Clock timing overlay

**Status:** ACCEPTED  
**Scope:** Race public-information timing/classification overlay only  
**Hardware:** unchanged Manual Transmission v0.7  
**Game Changers:** unchanged at 0

Use this overlay only from public stack/board state plus Manual Transmission's own known hand/resources.

1. **Magda activation already on stack:** sacrificing five Treasures is already paid. Removing Magda does not erase the tutor activation. Tishana's Tidebinder is a direct current-activation answer only when its ETB trigger is live.
2. **Clock activation already on stack:** tapping two artifacts is already paid. Removing Clock of Omens does not erase the untap activation.
3. **Clock target is different from Clock source:** if the Clock activation has one legal target and Manual Transmission can remove that target artifact with Beast Within or Chaos Warp before resolution, the activation can be stopped by losing its target. Do not confuse this with removing Clock itself.
4. **Torpor Orb:** while Torpor Orb is active, Tidebinder's ETB trigger does not happen. Do not count Tidebinder alone.
5. **Orb -> Tidebinder two-layer line:** with Torpor Orb active, Beast Within or Chaos Warp may remove Orb first; Tidebinder can then answer the still-stacked activation only if the full sequence is legal and affordable before that activation resolves. Spend the permanent-removal card once.
6. **No refunds:** countering the activation does not return the five Treasures or untap Clock's activation-cost artifacts.
7. **Not a forced line:** prefer the legal line that preserves the most useful downstream interaction; this overlay classifies timing, it does not require Tidebinder whenever another current-activation answer is cleaner.

### Qualification

Protocol: `MT_MAGDA_R3M_QUAL_R1_2026_09_24`  
Exact states: 960  
Generic-reference false stops: 444  
R3-M false stops: 0  
R3-M false-live states: 0  
Independent audit errors: 0  
Rows SHA-256: `00c3c9cf65d5d59b9be396ec3c615468be4a3fa122d0f0df17cc22d2d05305c9`  
GitHub Actions run: 52 / run id 35997515885 — success  
Qualification artifact digest: `sha256:0f2a4db14e732d0e17027551be3c298cbb86e324ae73fc2275a461783ee60502`
