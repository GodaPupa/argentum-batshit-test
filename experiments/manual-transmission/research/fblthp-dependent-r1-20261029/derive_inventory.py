from pathlib import Path
import importlib.util,json,hashlib
HERE=Path(__file__).resolve().parent
OLD='experiments/manual-transmission/research/spellskite-card-dependent-r1-20261029'
def derive(root):
 root=Path(root);spec=importlib.util.spec_from_file_location('accepted_cards',root/OLD/'derive_inventory.py');mod=importlib.util.module_from_spec(spec);spec.loader.exec_module(mod);inv=mod.derive(root)['ordinary']
 extra=json.loads((HERE/'ADDITIONS.json').read_text());assert len(extra)==51 and set(extra.values())=={1};assert not set(extra)&set(inv['identities']);inv['identities'].update(extra)
 assert sum(inv['identities'].values())==19074 and sum(inv['skips'].values())==43
 cards={k:{a:b for a,b in v.items() if a.split('\t')[0]=='mtg-sets'} for k,v in inv.items()};assert sum(cards['identities'].values())==389 and not cards['skips']
 return dict(ordinary=inv,cards=cards)
