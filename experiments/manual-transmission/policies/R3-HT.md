# R3-HT — Hashaton timing overlay

**Status:** ACCEPTED  
**Scope:** Race public-information timing/classification overlay only  
**Hardware:** unchanged Manual Transmission v0.7  
**Game Changers:** unchanged at 0

Use this overlay only when Hashaton, Scarab's Fist and the relevant discard outlet / stack object are public.

1. **Discard as an activation cost:** if the outlet discards the creature while paying the activation cost, the discard happens before priority returns. Killing Hashaton after that does not erase the Hashaton trigger already created. Do not call ordinary source removal a stop of the current discard attempt.
2. **Discard during resolution:** if the discard happens only when an already-stacked activated or triggered ability resolves, killing Hashaton before that object resolves is a genuine pre-discard prevention window.
3. **Existing trigger:** once a Hashaton trigger exists, removing Hashaton does not erase it.
4. **Lion's Eye Diamond:** LED discards the entire hand as a mana-ability activation cost. Multiple discarded creatures create multiple Hashaton triggers. One Tidebinder can counter one current trigger and can blank Hashaton only while Tidebinder remains; it does not erase sibling triggers already on the stack.
5. **Foil:** if Foil's alternate cost discards a creature while Hashaton is live, that discard creates a Hashaton trigger during the counterwar.
6. **Tidebinder lifecycle:** if Tidebinder leaves before its ETB resolves, the targeted trigger is still countered but Hashaton is not blanked. If Tidebinder leaves after the ETB resolves, the blank ends when Tidebinder leaves.
7. **Token is not automatically a loss:** after a Hashaton trigger resolves, branch on the actual payload. Teferi changes future spell windows; Razaketh exposes activated-ability windows; Rune-Scarred Demon exposes an ETB window; Vilis creates ability/trigger stack interactions; Jin-Gitaxias creates immediate static pressure. Do not score “token made” as “game lost.”

### What R3-HT does not do

- It does not use hidden hand knowledge.
- It does not prescribe a single forced answer sequence.
- It does not say Tidebinder always wins the exchange.
- It does not change the mulligan policy.
- It does not authorize a hardware change.

### Qualification

Protocol: `MT_HASHATON_R3HT_QUAL_R1_2026_09_23`  
Exact states: 2,950  
Reference false stops: 434  
R3-HT false stops: 0  
R3-HT false-live states: 0  
Independent audit errors: 0  
Rows SHA-256: `d59d9f56e67d6003f2a474b07c919ca68c3e820866cf0b15e48fd030bb01c31d`  
GitHub Actions run: 29 / run id 35964480415 — success  
Qualification artifact digest: `sha256:0a3f16f4445dd1ed7dbb861e45034b37abe9bf20be3398a3461745a0dd205c09`
