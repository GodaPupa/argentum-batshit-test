"""Original early-feedback receiver; this does not accept a component."""
from pathlib import Path
import json,hashlib,zipfile,re,xml.etree.ElementTree as ET
R=Path(__file__).resolve().parent;M=json.loads((R/'metadata.json').read_text());A=M['artifact'];O=R/'original'
S='a219991f8802ed9b95403815b69460bf5d7e931d';V='8bf2f9cc416666a1dd1d5f49008619b3666b76a1'
assert M['run']['id']==40083463089 and M['run']['head_sha']==V
assert M['run']['run_attempt']==M['run']['run_number']==1 and M['run']['event']=='push'
assert M['jobs']['total_count']==1 and M['jobs']['jobs'][0]['id']==120841547178
assert A['workflow_run']['id']==40083463089 and A['workflow_run']['head_sha']==V
assert A['id']==M['artifact_id_pin'] and A['name']=='manual-activation-target-capture-preflight-r2-40083463089'
p=R/('original-'+str(A['id'])+'.zip');sha=lambda b:hashlib.sha256(b).hexdigest()
assert p.stat().st_size==A['size_in_bytes']==M['artifact_bytes_pin']
assert sha(p.read_bytes())==M['artifact_sha256_pin'] and A['digest']=='sha256:'+M['artifact_sha256_pin']
with zipfile.ZipFile(p) as z:
 names=z.namelist();assert len(names)==len(set(names)) and z.testzip() is None
 assert all(not Path(n).is_absolute() and '..' not in Path(n).parts for n in names)
 z.extractall(O)
manifest={}
for line in (O/'SHA256SUMS').read_text().splitlines():
 h,n=line.split('  ',1);assert n not in manifest and sha((O/n).read_bytes())==h;manifest[n]=h
assert set(manifest)=={str(p.relative_to(O)) for p in O.rglob('*') if p.is_file()}-{'SHA256SUMS'}
b=json.loads((O/'BINDING.json').read_text());assert b['source']==S and b['validation']==V and b['tree']=='99a3fe21ded51eecfb37515e8e8e5e071e8b3d56'
assert (O/'FINAL_TRACKED_CHANGES.txt').read_text().strip()==''
P=json.loads((O/'protocol/PROTOCOL.json').read_text())
result={'run':40083463089,'job':120841547178,'source':S,'validation':V,'artifact':A,'manifest_entries':len(manifest),'conclusion':M['run']['conclusion'],'scope':'EARLY_FEEDBACK_ONLY_NOT_COMPONENT_ACCEPTANCE','official_games':0,'official_seeds':0,'precision_reserved':2}
rows={}
for stage in ['normal','mutation']:
 rows[stage]=[]
 for p in (O/stage/'junit').glob('TEST-*.xml'):
  suite=ET.parse(p).getroot();ts=list(suite.iter('testcase'));assert len(ts)==int(suite.attrib['tests'])
  for t in ts:
   f=t.find('failure');e=t.find('error');sk=t.find('skipped')
   rows[stage].append({'name':t.attrib['name'],'type':None if f is None else f.get('type'),'stack':None if f is None else f.text,'error':e is not None,'skip':sk is not None})
result['rows']=rows
if M['run']['conclusion']=='success':
 for stage in ['normal','mutation']:
  assert int((O/('compile-'+stage)/'exit.txt').read_text())==0
  log=(O/('compile-'+stage)/'gradle.log').read_text();assert 'BUILD SUCCESSFUL' in log and not re.search(r'^e: |Compilation error|Unresolved reference',log,re.M)
  rr=rows[stage];assert sorted(x['name'] for x in rr)==sorted(P['targeted_cases'])
  assert all(not x['error'] and not x['skip'] for x in rr)
  fails=[x for x in rr if x['type'] is not None]
  assert sorted(x['name'] for x in fails)==(sorted(P['expected_mutation_failures']) if stage=='mutation' else [])
  for x in fails:
   assert x['type']=='org.opentest4j.AssertionFailedError'
   frame=re.search(r'ActivationTargetCaptureTest.kt:(\d+)',x['stack'])
   assert frame and int(frame.group(1))=={'AC01':55,'AC02':60,'AC07':95}[x['name'][:4]]
  log=(O/stage/'gradle.log').read_text()
  assert not re.search(r'^e: |Compilation error|Unresolved reference',log,re.M)
  assert re.findall(r'^> Task (\S+) FAILED$',log,re.M)==([':rules-engine:test'] if stage=='mutation' else [])
 result['state']='EARLY_FEEDBACK_PASSED_FULL_QUALIFICATION_PENDING'
else:result['state']='FAILED_ORIGINAL_EVIDENCE_RECEIVED_DIAGNOSIS_REQUIRED'
(R/'RECEIPT.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps({k:v for k,v in result.items() if k!='rows'},indent=2))
