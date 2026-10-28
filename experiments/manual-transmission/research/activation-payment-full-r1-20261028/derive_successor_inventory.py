"""Derive successor inventories from accepted inputs, never from observed candidate failures."""
from pathlib import Path
from collections import Counter
import json,gzip,base64,hashlib
H=Path(__file__).resolve().parent
C=json.loads((H/'FULL_COMPATIBILITY_CONTRACT.json').read_text())
def derive(root):
    old=Path(root)/'experiments/manual-transmission/research/final-compatibility-inventory-r1-20261027'
    raw=gzip.decompress(base64.b64decode(''.join(p.read_text() for p in sorted(old.glob('INVENTORIES.part*.b64')))))
    assert hashlib.sha256(raw).hexdigest()==C['base_inventory']['raw_sha256']
    inventory=json.loads(raw)
    for oldkey,newkey in C['approved_key_mapping'].items():
        assert inventory['ordinary']['identities'].pop(oldkey)==1 and newkey not in inventory['ordinary']['identities']
        inventory['ordinary']['identities'][newkey]=1
    added=json.loads((H/'ADDED_CASES.json').read_text())
    assert len(added)==43 and set(added.values())=={1}
    for stage in ['focused','broad','ordinary']:
        assert not set(added)&set(inventory[stage]['identities'])
        inventory[stage]['identities'].update(added)
    raw=json.dumps(inventory,sort_keys=True,separators=(',',':')).encode()
    assert hashlib.sha256(raw).hexdigest()==C['successor_inventory_sha256']
    for stage,d in inventory.items():
        counts=dict(rows=sum(d['identities'].values()),skips=sum(d['skips'].values()),passed=sum(d['identities'].values())-sum(d['skips'].values()))
        assert counts==C['expected'][stage]
    return inventory
if __name__=='__main__':
    import sys
    result=derive(sys.argv[1]);print(json.dumps({stage:sum(d['identities'].values()) for stage,d in result.items()}))
