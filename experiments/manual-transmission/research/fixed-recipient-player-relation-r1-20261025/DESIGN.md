# Fixed-recipient selected-player relation repair

The saved draft showed that the selected player reached TargetObject but was dropped before battlefield, spell and card-zone predicate evaluation. This successor also fixes the same loss in both TargetSpellOrPermanent filter branches. The already chosen player is forwarded without changing cardinality, occurrence plans, original provenance or source-characteristic rules. Defaulted private parameters retain callers without authoritative player context.

Fourteen new internal cases cover owned/controlled battlefield targets and owned hand, exile, stack, and mixed spell/permanent targets. Each path must permit a legal dependent-target replacement and reject changing the selected player when that would invalidate a retained target. Existing 42 cases and assertions are byte preserved. Test-only zone construction is explicit; these are not public-flow or gameplay claims.

The executable mutant drops only the four forwarding sites. Both source variants must compile before controls execute. Exactly fourteen assertion failures at frozen lines 30, 44 and 80 are required; compilation errors, absent reports and unrelated failures are not behavioral evidence. All 56 cases must pass normally and again after byte-exact restoration. The old mutant and full regression receipts on source 388f6156ac726c17fd591a87ffbee1bb7224c170 are reused, never re-audited as evidence for this new source.

Review: source changes are limited to TargetValidator and one new test class. Existing resolver and other protected bytes, 23 goldens, 82 legacy fixtures, v0.7, budgets and two reserved Precision games are unchanged. This is owner review, not independent review. No official games, seeds, public registration or multiplayer admission. Full regressions and original-source/player-protection, source-instance, public-choice, ward and trigger gates remain before feature acceptance.

R2 corrects only frozen frame expectations using preserved original R1 evidence a130ad6f3f754e692033dca544313615c429d131. Production, tests, assertions and mutation remain byte identical. R1 remains unaccepted because restored execution is absent.
