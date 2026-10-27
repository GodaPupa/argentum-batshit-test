"""Executable exact-inventory checks. No behavior or compatibility acceptance."""
import json,copy,gzip,base64
from pathlib import Path
from collections import Counter
P=Path(__file__).resolve().parent
I=json.loads(gzip.decompress(base64.b64decode((P/'EXACT_INVENTORIES.json.gz.b64').read_text())))
def verify(stage,names,skips):
 assert Counter(names)==Counter(I[stage]['identities'])
 assert Counter(skips)==Counter(I[stage]['skips'])
 assert not Counter(skips)-Counter(names)
rejected=[]
for stage,v in I.items():
 verify(stage,v['identities'],v['skips'])
 for variant in ['missing','duplicate','unexpected','skip']:
  n=v['identities'].copy();s=v['skips'].copy();k=next(iter(n))
  if variant=='missing':n[k]-=1
  elif variant=='duplicate':n[k]+=1
  elif variant=='unexpected':n['unexpected\tidentity\tcase']=1
  else:s[k]=s.get(k,0)+1
  try:verify(stage,n,s)
  except AssertionError:rejected.append(stage+':'+variant)
  else:raise AssertionError('accepted '+variant)
print(json.dumps(dict(state='INVENTORY_CHECKS_ONLY_NOT_RUNTIME_QUALIFICATION',accepted_stages=len(I),rejections=rejected),indent=2))
