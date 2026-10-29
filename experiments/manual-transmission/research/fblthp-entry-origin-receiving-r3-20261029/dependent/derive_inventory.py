"""Unexecuted successor inventory draft, to freeze after accepted early artifact and WAR insertion."""
from pathlib import Path
import importlib.util,json
HERE=Path(__file__).resolve().parent
OLD='experiments/manual-transmission/research/spellskite-card-dependent-r1-20261029'
def derive(root):
 root=Path(root);spec=importlib.util.spec_from_file_location('accepted_card_dependent_inventory',root/OLD/'derive_inventory.py')
 old=importlib.util.module_from_spec(spec);spec.loader.exec_module(old)
 inv=old.derive(root)['ordinary']
 extra=json.loads((HERE/'ADDED_FBLTHP_CASES.json').read_text())
 assert len(extra)==25 and set(extra.values())=={1} and not set(extra)&set(inv['identities'])
 inv['identities'].update(extra)
 assert sum(inv['identities'].values())==19048 and sum(inv['skips'].values())==43
 cards={k:{a:b for a,b in v.items() if a.split('\t')[0]=='mtg-sets'} for k,v in inv.items()}
 assert sum(cards['identities'].values())==389 and not cards['skips']
 return {'ordinary':inv,'cards':cards}
