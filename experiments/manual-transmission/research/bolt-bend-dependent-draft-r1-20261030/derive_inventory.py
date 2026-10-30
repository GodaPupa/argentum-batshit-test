"""Retain all accepted identities and exact43 skips, adding only16 Bolt Bend cases."""
from pathlib import Path
import importlib.util,json
HERE=Path(__file__).resolve().parent
OLD='experiments/manual-transmission/research/fblthp-one-test-exception-r1-20261029'
def derive(root):
 root=Path(root);spec=importlib.util.spec_from_file_location('accepted_one_test_inventory',root/OLD/'derive_inventory.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);inv=m.derive(root)
 extra=json.loads((HERE/'ADDITIONS.json').read_text());assert len(extra)==16 and set(extra.values())=={1}
 ordinary=inv['ordinary'];assert sum(ordinary['identities'].values())==19074 and sum(ordinary['skips'].values())==43
 assert not set(extra)&set(ordinary['identities']);ordinary['identities'].update(extra)
 assert sum(ordinary['identities'].values())==19090 and sum(ordinary['skips'].values())==43
 assert sum(inv['cards']['identities'].values())==389 and not inv['cards']['skips']
 return inv
