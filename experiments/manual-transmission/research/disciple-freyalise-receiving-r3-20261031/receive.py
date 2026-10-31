"""Run with original ZIP, pinned metadata JSON, and a checkout of the frozen source."""
from pathlib import Path
import hashlib,json,sys,zipfile,xml.etree.ElementTree as ET
archive,metadata,source=map(Path,sys.argv[1:4])
meta=json.loads(metadata.read_text());sha=lambda b:hashlib.sha256(b).hexdigest()
pins={"source":"2a0b8842a79909d10111bd5e3cac089f43c77cb9","tree":"6e73cf0427d225352ed852ad2b3d7e20a0d5ab6b","validation":"5f950abbf1f06847651c93e6aed13b292dfbd245","validation_tree":"76dfae809ecb0b309604dacd3efd71be09c42e77","workflow":".github/workflows/manual-disciple-primitives-diagnostic-r3-20261031.yml","workflow_blob":"8b3df22c06ec78cf079a4d359db7fa08562e59ec","record":"experiments/manual-transmission/research/disciple-freyalise-primitives-diagnostic-r3-20261031"}
protocol=json.loads((source/pins['record']/'PROTOCOL.json').read_bytes())
assert meta['head_sha']==pins['validation'] and meta['event']=='push' and meta['attempt']==1
assert len(archive.read_bytes())==meta['size_in_bytes'] and sha(archive.read_bytes())==meta['digest'].removeprefix('sha256:')
with zipfile.ZipFile(archive) as z:
 assert len(z.namelist())==len(set(z.namelist())) and z.testzip() is None
 files={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
j=lambda n:json.loads(files[n])
m=j('MANIFEST.json')
assert set(m)==set(files)-{'MANIFEST.json'} and all(sha(files[k])==v for k,v in m.items())
b=j('BINDING.json')
for k in ('source','tree','validation','validation_tree','workflow','workflow_blob'):assert b[k]==pins[k]
assert int(b['run'])==meta['run_id'] and b['attempt']==1
for f in (source/pins['record']).rglob('*'):
 if f.is_file():assert files['protocol/'+str(f.relative_to(source/pins['record']))]==f.read_bytes()
assert files['scenario.kt']==(source/protocol['test']).read_bytes()
for label in ['initial','final','restored']:assert j(label+'/PROTECTED.json')=={"game-server":"88b7cb3260dee8b710d5278361dbcb57962f3ab4","mtg-sdk":"51b097156bff57e30aeca2d6338ccfc5a62a65e3","rules-engine":"ba2e20898cc69d709d895933952301c8ff23ce2e","web-client":"0ce36368d74a34f392e85b261771863a2a9d004c","mtg-sets/src/test/resources/snapshots/cards/MH3.json":"25b01370752b8d2fe97389c453a300dce833531a"}
assert j('ALL_FORMS_COMPILED.json')=={'forms':protocol['forms'],'all_compiled_before_behavior':True}
for form in protocol['forms']:
 for stage in ['compile','observe']:
  d=f'{stage}-{form}/'
  assert sha(files[d+'CARD.kt'])==protocol['form_hashes'][form]
  assert sha(files[d+'scenario.kt'])==protocol['test_form_hashes'].get(form,protocol['test_sha256'])
  if stage=='compile':
   assert int(files[d+'exit.txt'])==0 and b'BUILD SUCCESSFUL' in files[d+'output.log']
   continue
  rows=[r for n,v in files.items() if n.startswith(d+'junit/') and n.endswith('.xml') for r in ET.fromstring(v).iter('testcase')]
  assert sorted(r.get('name') for r in rows)==sorted(protocol['case_names'])
  assert all(r.get('classname')==protocol['class'] and r.find('skipped') is None for r in rows)
  failures=[r for r in rows if r.find('error') is not None or r.find('failure') is not None]
  assert bool(int(files[d+'exit.txt']))==bool(failures) and j(d+'SUMMARY.json')['failed']==len(failures)
  print(form,len(rows),len(failures))
print('Structural audit only. Compare original failure identities/types/values/lines, normal passing controls, run/job metadata and workflow bytes before receipt. Never infer admission.')
