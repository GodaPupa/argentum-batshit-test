"""One authorized identity replacement; all prior identities/counts/skips retained."""
from pathlib import Path
import importlib.util,json
HERE=Path(__file__).resolve().parent
OLD='experiments/manual-transmission/research/fblthp-dependent-r1-20261029'
def derive(root):
 root=Path(root);spec=importlib.util.spec_from_file_location('prior_fblthp_dependent',root/OLD/'derive_inventory.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);inv=m.derive(root)
 change=json.loads((HERE/'IDENTITY_REPLACEMENT.json').read_text());old=change['old_identity'];new=change['new_identity'];ordinary=inv['ordinary']
 assert ordinary['identities'].pop(old)==1 and new not in ordinary['identities'] and old not in ordinary['skips']
 ordinary['identities'][new]=1
 assert sum(ordinary['identities'].values())==19074 and sum(ordinary['skips'].values())==43
 assert sum(inv['cards']['identities'].values())==389 and not inv['cards']['skips']
 return inv
