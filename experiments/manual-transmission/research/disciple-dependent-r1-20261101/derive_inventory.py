"""Deterministic derived inventory only; never evidence that tests ran."""
from pathlib import Path
from collections import Counter
import json,gzip,base64,hashlib
HERE=Path(__file__).resolve().parent
def derive(root):
 path=Path(root)/'experiments/manual-transmission/research/opal-palace-dependent-r2-20261031/INVENTORY.json.gz.base64'
 raw=gzip.decompress(base64.b64decode(path.read_bytes()))
 assert hashlib.sha256(raw).hexdigest()=='111b5366daa39dc97d0d1184f7b31323935ade5b4639ab9867aa939eed342f40'
 inventory=json.loads(raw)
 additions=json.loads((HERE/'dependent-additions.draft.json').read_text())
 for section in ['ordinary','cards']:
  identities=inventory[section]['identities']
  assert not set(identities)&set(additions[section])
  assert all(n==1 for n in additions[section].values())
  identities.update(additions[section])
 assert sum(inventory['ordinary']['identities'].values())==19212
 assert sum(inventory['ordinary']['skips'].values())==43
 assert sum(inventory['cards']['identities'].values())==396 and not inventory['cards']['skips']
 raw=json.dumps(inventory,sort_keys=True,separators=(',',':')).encode()
 assert hashlib.sha256(raw).hexdigest()=='32ff6d748e30118f20b4784ad83cbbf126db86f1feb6b24b49711af1ce0ed107'
 return inventory
