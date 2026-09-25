# Colossal Dreadmask — unresolved capability draft

These files are prospective implementation material, outside compiled source roots and outside
accepted coverage. Batch AM excludes this card. Registry resolution and frozen decks are unchanged.
No test execution or behavioral acceptance is claimed for these drafts.

The draft uses existing token creation, selection from the full created-token collection, attachment,
static +6/+6 and trample, and equip {3}{G}{G}. The exact published rulings require both token selection
when creation is doubled and entry-trigger observation before attachment. Source and scenario review
found two separate engine gaps:

1. `AttachEquipmentExecutor` currently acts on raw source identity after departure. The pending patch
   validates the actual originating source against captured object references and battlefield presence,
   preserving source semantics even when a pipeline binds Self elsewhere. Its six pending engine
   regressions cover current source, departure, return as a new object, raw source outside battlefield,
   rebound Self, and an unavailable destination. This patch is not applied to the compiled engine.
2. `CreateTokenExecutor` emits no projected entry snapshot; the composite attaches before the later
   trigger matcher reads live power. An observer can therefore see 6/6 instead of the actual 0/0 entry.
   A canonical entry-time snapshot capability must include preexisting continuous effects, not merely
   printed base statistics. `ZoneChangeEvent.lastKnown` remains reserved for departures and must not
   be repurposed. The retained zero-power observer scenario is the required regression.

Before admission, integrate and qualify the separate canonical engine capabilities, restore these
files to their ordinary source/test paths, run all four Dreadmask scenarios and the source-identity
regressions, then qualify exact registry coverage and the single MH3 golden addition. Do not omit the
observer, doubled-token choice, removed-source, or equip-payment cases to obtain acceptance.

Canonical card: [MH3 #148](https://scryfall.com/card/mh3/148/colossal-dreadmask).
Definition and ruling metadata were retrieved from Scryfall before drafting. All official counters
remain unchanged; this draft authorizes no gameplay and alters no frozen deck.
