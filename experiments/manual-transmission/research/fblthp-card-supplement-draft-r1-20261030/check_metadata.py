"""Exercise exact metadata validation with original archived inputs and rejected alterations."""
from pathlib import Path
import json,base64,copy,sys
from metadata_audit import verify
H=Path(__file__).resolve().parent;Q=json.loads((H/'PROTOCOL.json').read_text())
root=Path(sys.argv[1]) if len(sys.argv)>1 else Path.cwd()
snapshot=(root/Q['snapshot']).read_bytes();archived=(H/'ARCHIVED_WAR_METADATA.json').read_bytes()
raw={n:base64.b64decode(b) for n,b in json.loads((H/'ARCHIVED_METADATA_CONTROLS.json').read_text()).items()}
verify(raw,archived,snapshot);rejected=[]
def reject(name,f):
 try:f()
 except (AssertionError,KeyError,ValueError):rejected.append(name);return
 raise AssertionError('accepted '+name)
for key,value in [('oracle_text','changed'),('collector_number','51'),('id','other'),('artist','other'),('flavor_text','invented')]:
 r=copy.deepcopy(raw);c=json.loads(r['metadata/war.json']);c[key]=value;r['metadata/war.json']=json.dumps(c).encode()
 reject(key,lambda r=r:verify(r,archived,snapshot))
r=copy.deepcopy(raw);d=json.loads(r['metadata/printings-0.json']);d['has_more']=True;d['next_page']='missing';r['metadata/printings-0.json']=json.dumps(d).encode();reject('missing printing page',lambda:verify(r,archived,snapshot))
r=copy.deepcopy(raw);d=json.loads(r['metadata/printings-0.json']);d['data'].append(d['data'][0]);r['metadata/printings-0.json']=json.dumps(d).encode();reject('duplicate printing',lambda:verify(r,archived,snapshot))
r=copy.deepcopy(raw);d=json.loads(r['metadata/rulings-0.json']);d['data'][0]['comment']='changed';r['metadata/rulings-0.json']=json.dumps(d).encode();reject('ruling drift',lambda:verify(r,archived,snapshot))
r=copy.deepcopy(raw);d=json.loads(r['metadata/image.json']);d['status']=404;r['metadata/image.json']=json.dumps(d).encode();reject('missing image',lambda:verify(r,archived,snapshot))
reject('snapshot stat drift',lambda:verify(raw,archived,snapshot.replace(b'"power": 1,',b'"power": 9,')))
print(json.dumps({'synthetic_only':True,'valid_archived_shape':1,'rejected':rejected},indent=2))
