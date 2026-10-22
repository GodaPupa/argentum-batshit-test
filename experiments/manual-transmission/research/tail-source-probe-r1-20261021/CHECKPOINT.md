# Manual remaining-card source intake R1

Source: `f31071375f44eb132ed976810607695316d4c707`; validation: `a3f6f2725b42c0d153d27818ab5e5778f8b6a010`; original run [39722087104](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39722087104), attempt 1, SUCCESS.

Ten current exact Scryfall printings, their complete printing searches and rulings were received. Expected canonical sets confirmed: Fblthp WAR; Reckless Bushwhacker OGW; Amphin Mutineer CMR; Hope-Ender Coatl MH3; Spellskite NPH; Malevolent Hermit MID; Phyrexian Metamorph NPH; Disciple of Freyalise MH3; Veil of Summer M20; Opal Palace C13. All twelve face image URLs returned HTTP 200.

**No card was read whole by Assay.** Every card has at least one DECLINED line. The emitter produced eight SCAFFOLD drafts; Malevolent Hermit and Disciple of Freyalise were absent from mtgish IR (exit 1), which is an inspected missing-draft result, not a passing card implementation. All Assay parse/explain commands exited 0 because those commands report declines as information. Zero JUnit tests and zero games executed by this workflow.

Original artifact 12401637647: `manual-tail-source-probe-r1-39722087104.zip`, 109159 bytes, SHA-256 `6f2a85e9b08d85dd1fe244d3509b96cded0a246d9039b5fcaa79bd6666d5ea32`. All 68 unique ZIP members, CRC, internal SHA256SUMS and embedded validation pin were checked. Verbatim artifact retained next to this report. This is self-audit, not independent review.

Next implementation candidates: Hope-Ender Coatl (existing cast-self trigger, opponent spell target, CounterUnlessPays, Devoid/Flash/Flying) and Malevolent Hermit (existing sacrifice cost, CounterUnlessPays, Disturb, GrantCantBeCountered and RedirectZoneChange). Existing donor scenarios contain nine Coatl cases and four Hermit cases; they still require receiving against the modern integration source. Hermit metadata must be updated from current source, including both face images, current Oracle wording and mechanically significant rulings. Other cards remain separate feature/source qualification tasks; parser declines alone do not prove SDK gaps.

Frozen v0.7 and official protocols unchanged. Official capability 0/36, primary 0/864, Precision 10 used / 2 reserved. No card coverage increase, full-game admission or competitive claim follows from this source-intake success.
