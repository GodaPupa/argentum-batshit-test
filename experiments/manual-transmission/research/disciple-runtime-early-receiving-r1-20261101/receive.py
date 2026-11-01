"""Read original early diagnostic bytes; metadata pins are checked separately before receipt."""
import sys,zipfile,json,hashlib,xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path
z=zipfile.ZipFile(sys.argv[1]); names=z.namelist()
assert len(names)==len(set(names))
assert z.testzip() is None
assert all(not n.startswith('/') and '..' not in Path(n).parts for n in names)
files={n:z.read(n) for n in names if not n.endswith('/')}
sha=lambda b:hashlib.sha256(b).hexdigest()
manifest=json.loads(files['MANIFEST.json'])
assert set(files)==set(manifest)|{'MANIFEST.json'}
assert all(sha(files[n])==h for n,h in manifest.items())
P=json.loads(files['protocol/PROTOCOL.json'])
B=json.loads(files['BINDING.json'])
assert B['source']=='80b5a52d775dad05801b103ee5e12c0e31edcf39'
assert B['validation']=='260261ebb5207c8265ab4575a38e5b4f5f2aa63d'
assert B['run']=='41282463247' and str(B['attempt'])==str(B['run_number'])=='1'
assert P['forms']==['normal','offer_guard','resume_guard','modal_land']
assert json.loads(files['ALL_FORMS_COMPILED.json'])==P['forms']
paths=P['paths']+[P['test']]
def source(stage):
 out={}
 hashes=json.loads(files[stage+'/SOURCE_HASHES.json'])
 for p in paths:
  out[p]=files[stage+'/source/'+p]
  assert sha(out[p])==hashes[p]
 return out
normal=source('compile-normal')
for form in P['forms']:
 expected=normal.copy()
 if form!='normal':
  m=next(m for m in P['mutations'] if m['id']==form)
  b=expected[m['path']].decode();assert b.count(m['before'])==1
  expected[m['path']]=b.replace(m['before'],m['after']).encode()
 for stage in ['compile-'+form,'observe-'+form]:assert source(stage)==expected
 assert files['compile-'+form+'/exit.txt'].strip()==b'0'
 assert b'BUILD SUCCESSFUL' in files['compile-'+form+'/output.log']
assert source('observe-restored')==normal
def rows(stage):
 out=[]
 for n,b in files.items():
  if n.startswith(stage+'/junit/') and n.endswith('.xml'):
   for c in ET.fromstring(b).iter('testcase'):
    assert c.get('classname')==P['class'] and not c.findall('skipped')
    out.append(dict(name=c.get('name'),failed=bool(c.findall('failure') or c.findall('error')),failures=[dict(type=f.get('type'),text=f.text or '',message=f.get('message')) for f in c.findall('failure')],errors=len(c.findall('error'))))
 assert Counter(x['name'] for x in out)==Counter(P['case_names'])
 assert int(files[stage+'/exit.txt']) in (0,1)
 assert (int(files[stage+'/exit.txt'])!=0)==any(x['failed'] for x in out)
 return {x['name']:x for x in out}
observed={form:rows('observe-'+form) for form in P['forms']}
restored=rows('observe-restored')
witnesses={}
for form,w in P['required_witnesses'].items():
 n=observed['normal'][w['case']];m=observed[form][w['case']]
 witnesses[form]=not n['failed'] and not restored[w['case']]['failed'] and not m['errors'] and any(f['type']=='org.opentest4j.AssertionFailedError' and ('DiscipleOfFreyaliseScenarioTest.kt:'+str(w['line'])) in f['text'] for f in m['failures'])
for k in ['initial','restored','final']:
 assert json.loads(files[k+'/PROTECTED.json'])==json.loads(files['initial/PROTECTED.json'])
result=dict(state='ARCHIVE_STRUCTURE_VERIFIED_REQUIRES_METADATA_SOURCE_AND_FAILURE_REVIEW',zip_bytes=Path(sys.argv[1]).stat().st_size,zip_sha256=sha(Path(sys.argv[1]).read_bytes()),manifest_entries=len(manifest),counts={k:dict(total=len(v),failed=sum(r['failed'] for r in v.values())) for k,v in observed.items()},restored_failed=sum(r['failed'] for r in restored.values()),candidate_witnesses=witnesses,card_admitted=False,full_qualification=False)
print(json.dumps(result,indent=2))
