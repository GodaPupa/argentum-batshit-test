from pathlib import Path
import json,hashlib
H=Path(__file__).parent;P=json.loads((H/'PROTOCOL.json').read_text());raw=(H/'ORIGINAL_WAR_DIFFERENTIAL.txt').read_bytes()
def verify(b):
 assert hashlib.sha256(b).hexdigest()==P['assay_baseline_sha256']
 assert b==raw
verify(raw)
rejected=[]
for label,b in [('missing',b''),('zero-divergences',raw.replace('DIVERGENT — read every one         1'.encode(),'DIVERGENT — read every one         0'.encode())),('wrong-amount',raw.replace(b'"amount": 3',b'"amount": 2')),('missing-power',raw.replace(b'PowerAtLeast',b'AlwaysTrue')),('extra-divergence',raw+b'Unexpected divergence\n')]:
 try:verify(b)
 except AssertionError:rejected.append(label)
 else:raise AssertionError(label)
print(json.dumps({'unchanged_baseline_accepted':True,'rejected':rejected,'engine_evidence':False}))
