"""Conditional full compatibility inventory; preserve the exact accepted baseline."""
from pathlib import Path
import importlib.util,json,hashlib
HERE=Path(__file__).resolve().parent
sha=lambda b:hashlib.sha256(b).hexdigest()
def derive(root):
 upstream=Path(root)/'experiments/manual-transmission/research/self-alternative-paid-choice-full-r1-20261101/derive_inventory.py'
 assert sha(upstream.read_bytes())=='a81aa86fad3575111ae47d1533fcebe72c78f4b8928287a74009b65078b641e9'
 spec=importlib.util.spec_from_file_location('accepted_paid_choice_inventory',upstream)
 module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
 inventory=module.derive(root)
 assert sha(json.dumps(inventory,sort_keys=True,separators=(',',':')).encode())=='eb9eb3ed64834d90ad5ca54e47a29de56c57726c3b482984bc06ddd8cf20ca57'
 before_skips=dict(inventory['ordinary']['skips'])
 additions=json.loads((HERE/'ADDITIONS.json').read_text())
 assert len(additions)==91 and set(additions.values())=={1}
 assert not set(inventory['ordinary']['identities'])&set(additions)
 inventory['ordinary']['identities'].update(additions)
 assert sum(inventory['ordinary']['identities'].values())==19330
 assert inventory['ordinary']['skips']==before_skips and sum(before_skips.values())==43
 assert sum(inventory['cards']['identities'].values())==396 and not inventory['cards']['skips']
 return inventory
