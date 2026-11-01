"""Synthetic receiver integrity tests only; no gameplay or qualification evidence."""
import json,hashlib,zipfile,tempfile,copy,importlib.util,xml.etree.ElementTree as E
from pathlib import Path
spec=importlib.util.spec_from_file_location('receiver','selection-r2/receive.py');receiver=importlib.util.module_from_spec(spec);spec.loader.exec_module(receiver)
protocol=Path('selection-r2/PROTOCOL.json').read_bytes();P=json.loads(protocol)
sha=lambda b:hashlib.sha256(b).hexdigest()
f={'protocol/PROTOCOL.json':protocol,'WORKFLOW.yml':Path('selection-r2/workflow.yml').read_bytes()}
def js(k,v):f[k]=(json.dumps(v)+'\n').encode()
js('BINDING.json',dict(source='c59959c5c9c11b8d26bc3ebd22746262dabb3656',tree='f1c96543db837b24bd504cecb70b1eb3200081ae',validation='baf8c21f2f2756e8c78235c8f551ff4fd9832afa',validation_tree='e99327340203875913eacc6a175418397e99e788',run='41323107878',attempt='1',run_number='1',workflow='.github/workflows/manual-disciple-card-controls-r2-20261101.yml',workflow_blob='e3fc3719d0b8e8752084c54a4ebe5d4fe799e0fe'))
js('ALL_FORMS_COMPILED.json',P['forms'])
z=zipfile.ZipFile('extension-r2-original.zip')
normal={p:z.read('compile-normal/source/'+p) for p in P['paths']}
normal[P['paths'][-1]]=Path('DiscipleOfFreyalise.non-targeted.proposal.kt').read_bytes()
normal[P['test']]=Path('DiscipleOfFreyaliseScenarioTest.non-targeted.proposal.kt').read_bytes()
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
for s in ['initial','restored','final']:js(s+'/PROTECTED.json',{'rules-engine': '70c0e715af78de30ad9fd60c8532ec412b0c58aa', 'mtg-sdk': '51b097156bff57e30aeca2d6338ccfc5a62a65e3', 'game-server': '88b7cb3260dee8b710d5278361dbcb57962f3ab4', 'web-client': '0ce36368d74a34f392e85b261771863a2a9d004c', 'mtg-sets/src/test/resources/snapshots/cards/MH3.json': '25b01370752b8d2fe97389c453a300dce833531a', 'mtg-sets/src/test/kotlin/com/wingedsheep/mtg/sets/DiscipleOfFreyaliseRegistrationTest.kt': '37fb6b51ff23479ab9dee06e89c9ba65cf52e4c0'})
def run(files):
 files=copy.deepcopy(files);files['MANIFEST.json']=json.dumps({n:sha(b) for n,b in files.items()}).encode()
 with tempfile.NamedTemporaryFile(suffix='.zip') as t:
  with zipfile.ZipFile(t.name,'w',zipfile.ZIP_DEFLATED) as z:
   for n,b in files.items():z.writestr(n,b)
  return receiver.inspect_archive(t.name,protocol)[3]
good=run(f);assert good['witnesses']==24 and good['controls']==57
checks=[]
for label,mutate in [
 ('wrong_run',lambda f:f.__setitem__('BINDING.json',f['BINDING.json'].replace(b'41323107878',b'41323107879'))),
 ('selection_witness_shift',lambda f:f.__setitem__('observe-targeting_choice_regression/junit/TEST-synthetic.xml',f['observe-targeting_choice_regression/junit/TEST-synthetic.xml'].replace(b'.kt:1055',b'.kt:1054'))),
 ('compiler_not_behavior',lambda f:f.__setitem__('observe-targeting_choice_regression/junit/TEST-synthetic.xml',f['observe-targeting_choice_regression/junit/TEST-synthetic.xml'].replace(b'org.opentest4j.AssertionFailedError',b'CompilationException'))),
 ('missing_compile',lambda f:f.pop('ALL_FORMS_COMPILED.json')),
 ('compile_failure',lambda f:f.__setitem__('compile-normal/exit.txt',b'1')),
 ('changed_source',lambda f:f.__setitem__('observe-fixed_one/source/'+P['test'],b'corrupt')),
 ('wrong_witness',lambda f:f.__setitem__('observe-fixed_one/junit/TEST-synthetic.xml',f['observe-fixed_one/junit/TEST-synthetic.xml'].replace(b'.kt:86',b'.kt:85'))),
 ('missing_report',lambda f:f.pop('observe-normal/junit/TEST-synthetic.xml')),
 ('wrong_multiplicity',lambda f:f.__setitem__('observe-normal/junit/TEST-synthetic.xml',f['observe-normal/junit/TEST-synthetic.xml'].replace(b'tests="67"',b'tests="66"'))),
 ('report_summary_mismatch',lambda f:f.__setitem__('observe-normal/REPORT.json',f['observe-normal/REPORT.json'].replace(b'"total": 67',b'"total": 66'))),
 ('protected_change',lambda f:f.__setitem__('final/PROTECTED.json',b'{}')),
 ('snapshot_export_mismatch',lambda f:f.__setitem__('registration-restored/candidate/MH3.candidate.json',b'changed')),
]:
 bad=copy.deepcopy(f);mutate(bad)
 try:run(bad)
 except (AssertionError,KeyError,ValueError):checks.append(label)
 else:raise AssertionError(label)
result=dict(synthetic_only=True,valid_shape_accepted=True,corruptions_rejected=checks,real_artifact_received=False)
Path('selection-r2/synthetic-result.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))
