"""Independent card successor inventory; never infer expected rows from a candidate run."""
from pathlib import Path
import importlib.util,json,hashlib
HERE=Path(__file__).resolve().parent
OLD='experiments/manual-transmission/research/spellskite-ordinary-r1-20261028'
def derive(root):
    root=Path(root);path=root/OLD/'derive_successor_inventory.py'
    spec=importlib.util.spec_from_file_location('accepted_spellskite_inventory',path)
    mod=importlib.util.module_from_spec(spec);spec.loader.exec_module(mod)
    inv=mod.derive(root)['ordinary']
    extra=json.loads((HERE/'ADDED_CARD_CASES.json').read_text())
    assert len(extra)==9 and set(extra.values())=={1}
    assert not set(extra)&set(inv['identities'])
    inv['identities'].update(extra)
    raw=json.dumps(inv,sort_keys=True,separators=(',',':')).encode()
    assert hashlib.sha256(raw).hexdigest()=='931a0d25a1adbed6efd4034c1fd74744513b69f5f10b1ed03259d2edf5739081'
    assert sum(inv['identities'].values())==19023 and sum(inv['skips'].values())==43
    cards={k:{a:b for a,b in v.items() if a.split('\t')[0]=='mtg-sets'} for k,v in inv.items()}
    assert sum(cards['identities'].values())==388 and not cards['skips']
    assert cards==json.loads((HERE/'CARD_GATE_IDENTITIES.json').read_text())
    return dict(ordinary=inv,cards=cards)
