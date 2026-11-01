"""Offline original artifact gate; API JSON must contain original run/jobs/artifacts/source/validation/deltas."""
from pathlib import Path
from collections import Counter
import sys,zipfile,json,hashlib,xml.etree.ElementTree as ET,subprocess,tempfile
sha=lambda b:hashlib.sha256(b).hexdigest()
gitsha=lambda b:hashlib.sha1(b'blob '+str(len(b)).encode()+b'\0'+b).hexdigest()
def inspect_archive(archive, protocol):
 z=zipfile.ZipFile(archive);names=z.namelist()
 assert len(names)==len(set(names)) and z.testzip() is None
 assert all(not n.startswith('/') and '..' not in Path(n).parts for n in names)
 f={n:z.read(n) for n in names if not n.endswith('/')}
 manifest=json.loads(f['MANIFEST.json'])
 assert set(f)==set(manifest)|{'MANIFEST.json'}
 assert all(sha(f[n])==h for n,h in manifest.items())
 assert f['protocol/PROTOCOL.json']==protocol
 P=json.loads(protocol);B=json.loads(f['BINDING.json'])
 assert B['source']=='a15c7859ea1e0a3155168338c8e465b975bc6a19'
 assert B['tree']=='dff07f6d0f71c72542b429038e8440b6ff7b9457'
 assert B['validation']=='23f6e3f6d79567dab5562c7b57f2a143916802de'
 assert B['validation_tree']=='dcd32a235b47ba9aeeb5080b8197e85faf8e9806'
 assert B['run']=='41293652735' and B['attempt']==B['run_number']=='1'
 assert B['workflow_blob']=='a6d3c66febba3707c20891dab0b4a950d47868d9'
 assert gitsha(f['WORKFLOW.yml'])==B['workflow_blob']
 assert json.loads(f['ALL_FORMS_COMPILED.json'])==P['forms'] and len(P['forms'])==19
 paths=P['paths']+[P['test'],P['registration_file']]
 def source(stage):
  hashes=json.loads(f[stage+'/SOURCE_HASHES.json']);assert set(hashes)==set(paths)
  out={p:f[stage+'/source/'+p] for p in paths}
  assert all(sha(b)==hashes[p] for p,b in out.items());return out
 normal=source('compile-normal')
 assert {p:sha(b) for p,b in normal.items()}==P['normal_sha256']
 for form in P['forms']:
  expected=normal.copy()
  if form!='normal':
   m=next(m for m in P['mutations'] if m['id']==form);s=expected[m['path']].decode()
   for e in m['edits']:
    assert s.count(e['before'])==1;s=s.replace(e['before'],e['after'])
   expected[m['path']]=s.encode();assert sha(s.encode())==m['source_sha256']
  assert source('compile-'+form)==expected
  log=f['compile-'+form+'/output.log']
  assert f['compile-'+form+'/exit.txt'].strip()==b'0' and b'BUILD SUCCESSFUL' in log
  assert all(t.encode() in log for t in P['compile_tasks'])
  assert not any(n.startswith('compile-'+form+'/junit/') for n in f)
 assert source('observe-normal')==normal
 def rows(stage, registration=False):
  out=[]
  for n,b in f.items():
   if n.startswith(stage+'/junit/') and n.endswith('.xml'):
    root=ET.fromstring(b);cases=list(root.iter('testcase'))
    for attr,child in [('failures','failure'),('errors','error'),('skipped','skipped')]:
     assert int(root.get(attr))==sum(bool(c.findall(child)) for c in cases)
    assert int(root.get('tests'))==len(cases)
    for c in cases:
     out.append(dict(name=c.get('name'),classname=c.get('classname'),failures=[dict(type=x.get('type'),message=x.get('message'),text=x.text) for x in c.findall('failure')],errors=[dict(type=x.get('type'),message=x.get('message'),text=x.text) for x in c.findall('error')],skipped=len(c.findall('skipped'))))
  expected=P['registration_cases'] if registration else P['case_names']
  cls=P['registration_class'] if registration else P['class']
  assert Counter(r['name'] for r in out)==Counter(expected)
  assert all(r['classname']==cls and not r['errors'] and not r['skipped'] for r in out)
  report=json.loads(f[stage+'/REPORT.json'])
  assert Counter(json.dumps(r,sort_keys=True) for r in out)==Counter(json.dumps(r,sort_keys=True) for r in report['rows'])
  failed=sum(bool(r['failures']) for r in out)
  assert report['total']==len(out) and report['failed']==failed
  rc=int(f[stage+'/exit.txt']);assert rc==report['rc'] and rc in [0,1] and bool(rc)==bool(failed)
  return {r['name']:r for r in out}
 observed=rows('observe-normal')
 failures=[r for r in observed.values() if r['failures']]
 assert len(failures)==1
 failure=failures[0]
 assert failure['name']=='DF12 shroud hexproof ward fodder sacrifices without tax or intermediate priority'
 assert len(failure['failures'])==1
 witness=failure['failures'][0]
 assert witness['type']=='org.opentest4j.AssertionFailedError'
 assert 'DiscipleOfFreyaliseScenarioTest.kt:1055' in witness['text']
 assert 'expected:<25> but was:<20>' in witness['message']
 assert normal[P['test']].decode().splitlines()[1054].strip()=='d.getLifeTotal(me) shouldBe 25'
 assert 'AssertionError: normal scenario failure' in f['FAILURE.txt'].decode()
 assert not any(n.startswith(('registration-','observe-restored','restored/')) for n in f)
 assert not any(n.startswith('observe-'+form+'/') for form in P['forms'][1:] for n in f)
 assert not any(n in f for n in ['RESULT.json','WITNESSES.json','UNAFFECTED_CONTROLS.json','FINAL_BINDING_FAILURE.txt'])
 assert f['initial/PROTECTED.json']==f['final/PROTECTED.json']
 protected=json.loads(f['final/PROTECTED.json'])
 assert set(protected)==set(P['unaffected'])
 assert protected=={'rules-engine': '70c0e715af78de30ad9fd60c8532ec412b0c58aa', 'mtg-sdk': '51b097156bff57e30aeca2d6338ccfc5a62a65e3', 'game-server': '88b7cb3260dee8b710d5278361dbcb57962f3ab4', 'web-client': '0ce36368d74a34f392e85b261771863a2a9d004c', 'mtg-sets/2024/src/main': '94b790b000e428e5d591cea2d6920efcdfb0ec44', 'mtg-sets/src/test/resources/snapshots/cards/MH3.json': '25b01370752b8d2fe97389c453a300dce833531a'}
 return f,P,B,dict(compiled_forms=19,normal_total=65,normal_failed=1,normal_passed=64,negative_behavior_observed=False,registration_observed=False,restored_behavior_observed=False,final_binding_passed=True,protected_bindings=protected,failure=failure,manifest_entries=len(manifest),skips=0,errors=0,card_admitted=False,full_qualification=False)

def main():
 archive=Path(sys.argv[1]);api=json.loads(Path(sys.argv[2]).read_text());protocol=Path(sys.argv[3]).read_bytes()
 f,P,B,result=inspect_archive(archive,protocol)
 r=api['run'];j=api['jobs']['jobs'];arts=api['artifacts']['artifacts']
 assert r['id']==41293652735 and r['head_sha']==B['validation'] and r['run_attempt']==r['run_number']==1 and r['event']=='push' and r['status']=='completed' and r['conclusion']=='failure'
 assert len(j)==1 and j[0]['id']==124809895359 and j[0]['conclusion']=='failure'
 assert len(arts)==1 and not arts[0]['expired'];a=arts[0]
 assert a['workflow_run']['id']==r['id'] and a['workflow_run']['head_sha']==B['validation']
 assert a['size_in_bytes']==archive.stat().st_size and a['digest']=='sha256:'+sha(archive.read_bytes())
 assert api['source']['tree']['sha']==B['tree'] and api['source']['parents'][0]['sha']==P['base']
 assert api['validation']['tree']['sha']==B['validation_tree'] and api['validation']['parents'][0]['sha']==B['source']
 delta=api['delta']['files'];assert {d['filename'] for d in delta}==set(P['changed_paths'])|{P['record']+'/'+n for n in P['record_files']}
 for d in delta:
  name=d['filename'];b=f['compile-normal/source/'+name] if name in P['changed_paths'] else f['protocol/'+name.split('/')[-1]]
  assert gitsha(b)==d['sha']
 vd=api['validation_delta']['files'];assert len(vd)==1 and vd[0]['filename']==B['workflow'] and vd[0]['sha']==B['workflow_blob']
 result.update(source=B['source'],tree=B['tree'],validation=B['validation'],validation_tree=B['validation_tree'],workflow_blob=B['workflow_blob'],run=r['id'],job=j[0]['id'],attempt=1,artifact=a['id'],archive_bytes=archive.stat().st_size,archive_sha256=sha(archive.read_bytes()),state='ORIGINAL_CARD_CONTROLS_FAILURE_RECEIVED_NOT_ADMITTED')
 print(json.dumps(result,indent=2))
if __name__=='__main__':main()
