"""Compare every original differential block. No normalization or semantic folds."""
from pathlib import Path
import hashlib,io,json,re,sys,zipfile
root=Path(__file__).resolve().parent
meta=json.loads((root/'ARTIFACT.json').read_text())
data=b''.join((root/p['name']).read_bytes() for p in meta['parts'])
assert hashlib.sha256(data).hexdigest()==meta['digest'].removeprefix('sha256:')
z=zipfile.ZipFile(io.BytesIO(data));current=z.read('assay/differential.log').decode()
baseline=(root/'BASELINE_DIFFERENTIAL.log').read_text()
def parse(text):
 headers=list(re.finditer(r'^  (.+)  \[([A-Z0-9]+)\]$',text,re.M));blocks={}
 for i,h in enumerate(headers):
  key=h.group(1)+' ['+h.group(2)+']';assert key not in blocks
  blocks[key]=text[h.end():headers[i+1].start() if i+1<len(headers) else len(text)].strip()
 return blocks
old,new=parse(baseline),parse(current)
added=sorted(new.keys()-old.keys());removed=sorted(old.keys()-new.keys())
changed=sorted(k for k in old.keys()&new.keys() if old[k]!=new[k])
report={'baseline_artifact_id':12430330947,'baseline_report_sha256':hashlib.sha256(baseline.encode()).hexdigest(),'current_artifact_id':meta['id'],'current_report_sha256':hashlib.sha256(current.encode()).hexdigest(),'baseline_divergences':len(old),'current_divergences':len(new),'added':added,'removed':removed,'changed':changed,'all_original_blocks_byte_identical':old==new,'retained_divergent_cards':sorted(new),'qualification':'UNCHANGED_DIVERGENCES_RETAINED' if old==new and len(new)==51 else 'REVIEW_REQUIRED','limitations':'Original divergences remain unresolved; exact unchanged report blocks do not prove card semantics or full-game admission.'}
if '--write' in sys.argv:(root/'DIFFERENTIAL_COMPARISON.json').write_text(json.dumps(report,indent=2)+'\n')
else:assert json.loads((root/'DIFFERENTIAL_COMPARISON.json').read_text())==report
print(json.dumps(report,indent=2))
