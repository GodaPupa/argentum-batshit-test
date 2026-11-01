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
 assert B['source']=='c59959c5c9c11b8d26bc3ebd22746262dabb3656'
 assert B['tree']=='f1c96543db837b24bd504cecb70b1eb3200081ae'
 assert B['validation']=='baf8c21f2f2756e8c78235c8f551ff4fd9832afa'
 assert B['validation_tree']=='e99327340203875913eacc6a175418397e99e788'
 assert B['run']=='41323107878' and B['attempt']==B['run_number']=='1'
 assert B['workflow']=='.github/workflows/manual-disciple-card-controls-r2-20261101.yml'
 assert B['workflow_blob']=='e3fc3719d0b8e8752084c54a4ebe5d4fe799e0fe'
 assert gitsha(f['WORKFLOW.yml'])==B['workflow_blob']
 assert json.loads(f['ALL_FORMS_COMPILED.json'])==P['forms'] and len(P['forms'])==20
 paths=P['paths']+[P['test'],P['registration_file']]
 def source(stage):
  hashes=json.loads(f[stage+'/SOURCE_HASHES.json']);assert set(hashes)==set(paths)
  out={p:f[stage+'/source/'+p] for p in paths}
  assert all(sha(b)==hashes[p] for p,b in out.items());return out
 normal=source('compile-normal')
 assert {p:sha(b) for p,b in normal.items()}==P['normal_sha256']
 assert len(P['case_names'])==67 and len(set(P['case_names']))==67
 assert len(P['registration_cases'])==6
 assert sha(normal[P['test']])=='cd76d90cddd88ed3d8df810f7c85707b5b030fca981233f95fe4a98e040394a8'
 assert sha(normal[P['paths'][-1]])=='f659920fc36d1737f22dd3679fcb6a62d0799767f66aec3770530f02db88381c'
 for form in P['forms']:
  expected=normal.copy()
  if form!='normal':
   m=next(m for m in P['mutations'] if m['id']==form);s=expected[m['path']].decode()
   for e in m['edits']:
    assert s.count(e['before'])==1;s=s.replace(e['before'],e['after'])
   expected[m['path']]=s.encode();assert sha(s.encode())==m['source_sha256']
  for stage in ['compile-'+form,'observe-'+form]:assert source(stage)==expected
  log=f['compile-'+form+'/output.log']
  assert f['compile-'+form+'/exit.txt'].strip()==b'0' and b'BUILD SUCCESSFUL' in log
  assert all(t.encode() in log for t in P['compile_tasks'])
  assert not any(n.startswith('compile-'+form+'/junit/') for n in f)
 for stage in ['observe-restored','registration-normal','registration-restored']:assert source(stage)==normal
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
 observed={form:rows('observe-'+form) for form in P['forms']}
 restored=rows('observe-restored')
 reg=rows('registration-normal',True);rreg=rows('registration-restored',True)
 for rs in [observed['normal'],restored,reg,rreg]:assert not any(r['failures'] for r in rs.values())
 witnesses={};controls=0
 for m in P['mutations']:
  witnesses[m['id']]=[]
  for w in m['witnesses']:
   r=observed[m['id']][w['case']]
   assert any(x['type']=='org.opentest4j.AssertionFailedError' and ('DiscipleOfFreyaliseScenarioTest.kt:'+str(w['line'])) in (x['text'] or '') for x in r['failures']),(m['id'],w)
   assert normal[P['test']].decode().splitlines()[w['line']-1].strip()==w['assertion']
   witnesses[m['id']].append(r)
  for name in m['unaffected_controls']:
   assert not observed[m['id']][name]['failures'];controls+=1
 assert sum(map(len,witnesses.values()))==24
 assert json.loads(f['WITNESSES.json'])==witnesses and controls==57
 assert json.loads(f['UNAFFECTED_CONTROLS.json'])=={m['id']:m['unaffected_controls'] for m in P['mutations']}
 for name in ['MH3.candidate.json','DiscipleOfFreyalise.block.json']:
  assert f['registration-normal/candidate/'+name]==f['registration-restored/candidate/'+name]
 assert not any(n in f for n in ['FAILURE.txt','FINAL_BINDING_FAILURE.txt'])
 assert f['initial/PROTECTED.json']==f['restored/PROTECTED.json']==f['final/PROTECTED.json']
 assert json.loads(f['initial/PROTECTED.json'])=={'rules-engine': '70c0e715af78de30ad9fd60c8532ec412b0c58aa', 'mtg-sdk': '51b097156bff57e30aeca2d6338ccfc5a62a65e3', 'game-server': '88b7cb3260dee8b710d5278361dbcb57962f3ab4', 'web-client': '0ce36368d74a34f392e85b261771863a2a9d004c', 'mtg-sets/src/test/resources/snapshots/cards/MH3.json': '25b01370752b8d2fe97389c453a300dce833531a', 'mtg-sets/src/test/kotlin/com/wingedsheep/mtg/sets/DiscipleOfFreyaliseRegistrationTest.kt': '37fb6b51ff23479ab9dee06e89c9ba65cf52e4c0'}
 counts={form:dict(total=len(rs),failed=sum(bool(r['failures']) for r in rs.values())) for form,rs in observed.items()}
 result=json.loads(f['RESULT.json']);assert result['counts']==counts and result['restored_failed']==0 and result['registration_total']==result['restored_registration_total']==6
 return f,P,B,dict(counts=counts,restored_cases=67,registration_cases=6,restored_registration_cases=6,witnesses=sum(map(len,witnesses.values())),controls=controls,manifest_entries=len(manifest),skips=0,errors=0,card_admitted=False,full_qualification=False)
def main():
 archive=Path(sys.argv[1]);api=json.loads(Path(sys.argv[2]).read_text());protocol=Path(sys.argv[3]).read_bytes()
 f,P,B,result=inspect_archive(archive,protocol)
 r=api['run'];j=api['jobs']['jobs'];arts=api['artifacts']['artifacts']
 assert r['id']==41323107878 and r['head_sha']==B['validation'] and r['run_attempt']==r['run_number']==1 and r['event']=='push' and r['status']=='completed' and r['conclusion']=='success'
 assert len(j)==1 and j[0]['id']==124897314878 and j[0]['conclusion']=='success'
 assert len(arts)==1 and not arts[0]['expired'];a=arts[0]
 assert a['workflow_run']['id']==r['id'] and a['workflow_run']['head_sha']==B['validation']
 assert a['size_in_bytes']==archive.stat().st_size and a['digest']=='sha256:'+sha(archive.read_bytes())
 assert api['source']['sha']==B['source'] and api['validation']['sha']==B['validation']
 assert api['source']['tree']['sha']==B['tree'] and api['source']['parents'][0]['sha']==P['base']
 assert api['validation']['tree']['sha']==B['validation_tree'] and api['validation']['parents'][0]['sha']==B['source']
 delta=api['delta']['files'];assert {d['filename'] for d in delta}==set(P['changed_paths'])|{P['record']+'/'+n for n in P['record_files']}
 for d in delta:
  name=d['filename'];b=f['compile-normal/source/'+name] if name in P['changed_paths'] else f['protocol/'+name.split('/')[-1]]
  assert gitsha(b)==d['sha']
 vd=api['validation_delta']['files'];assert len(vd)==1 and vd[0]['filename']==B['workflow'] and vd[0]['sha']==B['workflow_blob']
 # Validate the additive export against the exact unchanged original, without applying it.
 with tempfile.TemporaryDirectory() as d:
  d=Path(d)
  for name in ['MH3.candidate.json','DiscipleOfFreyalise.block.json']:(d/name).write_bytes(f['registration-normal/candidate/'+name])
  proc=subprocess.run([sys.executable,sys.argv[4],sys.argv[5],str(d/'MH3.candidate.json'),str(d/'DiscipleOfFreyalise.block.json')],capture_output=True,text=True)
  assert proc.returncode==0,proc.stderr
  result['candidate_snapshot_audit']=json.loads(proc.stdout)
 result.update(source=B['source'],tree=B['tree'],validation=B['validation'],validation_tree=B['validation_tree'],workflow_blob=B['workflow_blob'],run=r['id'],job=j[0]['id'],attempt=1,artifact=a['id'],archive_bytes=archive.stat().st_size,archive_sha256=sha(archive.read_bytes()),state='ORIGINAL_CARD_CONTROLS_VERIFIED_NOT_FULL_ADMISSION')
 print(json.dumps(result,indent=2))
if __name__=='__main__':main()
