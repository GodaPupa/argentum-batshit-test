"""Isolated inventory preparation. This file never qualifies or dispatches a run."""
from pathlib import Path
import importlib.util,json,hashlib
HERE=Path(__file__).resolve().parent
BASE='experiments/manual-transmission/research/disciple-dependent-r1-20261101'
sha=lambda b:hashlib.sha256(b).hexdigest()
def derive(root):
 upstream=Path(root)/BASE/'derive_inventory.py'
 assert sha(upstream.read_bytes())=='e1124b040a8e5c8582128e6801cba96f48576b8163af38347562529a69b6da42'
 spec=importlib.util.spec_from_file_location('accepted_disciple_inventory',upstream)
 module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
 inventory=module.derive(root)
 assert sha(json.dumps(inventory,sort_keys=True,separators=(',',':')).encode())=='32ff6d748e30118f20b4784ad83cbbf126db86f1feb6b24b49711af1ce0ed107'
 additions=json.loads((HERE/'paid-choice-dependent-additions.draft.json').read_text())
 assert len(additions)==27 and set(additions.values())=={1}
 assert not set(inventory['ordinary']['identities'])&set(additions)
 inventory['ordinary']['identities'].update(additions)
 assert sum(inventory['ordinary']['identities'].values())==19239
 assert sum(inventory['ordinary']['skips'].values())==43
 assert sum(inventory['cards']['identities'].values())==396 and not inventory['cards']['skips']
 assert sha(json.dumps(inventory,sort_keys=True,separators=(',',':')).encode())=='eb9eb3ed64834d90ad5ca54e47a29de56c57726c3b482984bc06ddd8cf20ca57'
 return inventory
