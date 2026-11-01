"""Synthetic receiver integrity tests only; no gameplay or qualification evidence."""
import json,hashlib,zipfile,tempfile,copy,importlib.util,xml.etree.ElementTree as E
from pathlib import Path
spec=importlib.util.spec_from_file_location('receiver','card-controls-receive.py');receiver=importlib.util.module_from_spec(spec);spec.loader.exec_module(receiver)
protocol=Path('card-controls-protocol.json').read_bytes();P=json.loads(protocol)
sha=lambda b:hashlib.sha256(b).hexdigest()
f={'protocol/PROTOCOL.json':protocol,'WORKFLOW.yml':Path('card-controls-workflow.yml').read_bytes()}
def js(k,v):f[k]=(json.dumps(v)+'\n').encode()
js('BINDING.json',dict(source='a15c7859ea1e0a3155168338c8e465b975bc6a19',tree='dff07f6d0f71c72542b429038e8440b6ff7b9457',validation='23f6e3f6d79567dab5562c7b57f2a143916802de',validation_tree='dcd32a235b47ba9aeeb5080b8197e85faf8e9806',run='41293652735',attempt='1',run_number='1',workflow_blob='a6d3c66febba3707c20891dab0b4a950d47868d9'))
js('ALL_FORMS_COMPILED.json',P['forms'])
z=zipfile.ZipFile('extension-r2-original.zip')
normal={p:z.read('compile-normal/source/'+p) for p in P['paths']}
normal[P['test']]=Path('disciple-card-controls-isolated.kt').read_bytes()
normal[P['registration_file']]=Path('DiscipleOfFreyaliseRegistrationTest.draft.kt').read_bytes()
witnesses={};counts={}
stages=[(form,False,False) for form in P['forms']]+[('restored',False,False),('normal',True,False),('restored',True,False)]
for form,registration,_ in stages:
 src=normal.copy();m=next((m for m in P['mutations'] if m['id']==form),None)
 if m:
  s=src[m['path']].decode()
  for e in m['edits']:s=s.replace(e['before'],e['after'])
  src[m['path']]=s.encode()
 for stage in ([('compile-'+form)] if not registration and form!='restored' else [])+[(('registration-' if registration else 'observe-')+form)]:
  for p,b in src.items():f[stage+'/source/'+p]=b
  js(stage+'/SOURCE_HASHES.json',{p:sha(b) for p,b in src.items()})
  f[stage+'/exit.txt']=b'0';f[stage+'/output.log']=('BUILD SUCCESSFUL\n'+'\n'.join(P['compile_tasks'])).encode()
  if stage.startswith('compile'):continue
  names=P['registration_cases'] if registration else P['case_names'];cls=P['registration_class'] if registration else P['class']
  root=E.Element('testsuite',tests=str(len(names)),errors='0',skipped='0');rows=[]
  for name in names:
   c=E.SubElement(root,'testcase',name=name,classname=cls);failures=[]
   w=next((w for w in m['witnesses'] if w['case']==name),None) if m else None
   if w:
    failure=dict(type='org.opentest4j.AssertionFailedError',message='SYNTHETIC ONLY',text='DiscipleOfFreyaliseScenarioTest.kt:'+str(w['line']))
    n=E.SubElement(c,'failure',type=failure['type'],message=failure['message']);n.text=failure['text'];failures=[failure]
   rows.append(dict(name=name,classname=cls,failures=failures,errors=[],skipped=0))
  failed=sum(bool(r['failures']) for r in rows);root.set('failures',str(failed));f[stage+'/junit/TEST-synthetic.xml']=E.tostring(root)
  f[stage+'/exit.txt']=str(int(bool(failed))).encode()
  js(stage+'/REPORT.json',dict(form=form,total=len(rows),failed=failed,rows=rows,rc=int(bool(failed))))
  if m:witnesses[form]=[next(r for r in rows if r['name']==w['case']) for w in m['witnesses']]
  if not registration and form!='restored':counts[form]=dict(total=len(rows),failed=failed)
  if registration:
   for name in ['MH3.candidate.json','DiscipleOfFreyalise.block.json']:f[stage+'/candidate/'+name]=b'SYNTHETIC ONLY - never an actual snapshot'
js('WITNESSES.json',witnesses);js('UNAFFECTED_CONTROLS.json',{m['id']:m['unaffected_controls'] for m in P['mutations']})
js('RESULT.json',dict(counts=counts,restored_failed=0,registration_total=6,restored_registration_total=6))
for s in ['initial','restored','final']:f[s+'/PROTECTED.json']=z.read('final/PROTECTED.json')
def run(files):
 files=copy.deepcopy(files);files['MANIFEST.json']=json.dumps({n:sha(b) for n,b in files.items()}).encode()
 with tempfile.NamedTemporaryFile(suffix='.zip') as t:
  with zipfile.ZipFile(t.name,'w',zipfile.ZIP_DEFLATED) as z:
   for n,b in files.items():z.writestr(n,b)
  return receiver.inspect_archive(t.name,protocol)[3]
good=run(f);assert good['witnesses']==23 and good['controls']==54
checks=[]
for label,mutate in [
 ('wrong_run',lambda f:f.__setitem__('BINDING.json',f['BINDING.json'].replace(b'41293652735',b'41293652736'))),
 ('missing_compile',lambda f:f.pop('ALL_FORMS_COMPILED.json')),
 ('compile_failure',lambda f:f.__setitem__('compile-normal/exit.txt',b'1')),
 ('changed_source',lambda f:f.__setitem__('observe-fixed_one/source/'+P['test'],b'corrupt')),
 ('wrong_witness',lambda f:f.__setitem__('observe-fixed_one/junit/TEST-synthetic.xml',f['observe-fixed_one/junit/TEST-synthetic.xml'].replace(b'.kt:86',b'.kt:85'))),
 ('missing_report',lambda f:f.pop('observe-normal/junit/TEST-synthetic.xml')),
 ('wrong_multiplicity',lambda f:f.__setitem__('observe-normal/junit/TEST-synthetic.xml',f['observe-normal/junit/TEST-synthetic.xml'].replace(b'tests="65"',b'tests="64"'))),
 ('report_summary_mismatch',lambda f:f.__setitem__('observe-normal/REPORT.json',f['observe-normal/REPORT.json'].replace(b'"total": 65',b'"total": 64'))),
 ('protected_change',lambda f:f.__setitem__('final/PROTECTED.json',b'{}')),
 ('snapshot_export_mismatch',lambda f:f.__setitem__('registration-restored/candidate/MH3.candidate.json',b'changed')),
]:
 bad=copy.deepcopy(f);mutate(bad)
 try:run(bad)
 except (AssertionError,KeyError,ValueError):checks.append(label)
 else:raise AssertionError(label)
result=dict(synthetic_only=True,valid_shape_accepted=True,corruptions_rejected=checks,real_artifact_received=False)
Path('card-controls-synthetic-result.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))
