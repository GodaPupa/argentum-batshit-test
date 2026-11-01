import json,zipfile,hashlib,subprocess,tempfile,copy
from pathlib import Path
P=json.loads(Path('/workspace/scratch/b9827535038f/extension-protocol.json').read_text())
sha=lambda b:hashlib.sha256(b).hexdigest()
files={}
def js(k,v):files[k]=(json.dumps(v)+'\n').encode()
js('protocol/PROTOCOL.json',P)
js('BINDING.json',dict(source='9b46148f257b133bc4592729fddd3504732a710b',validation='27a49b82a568eaac19eae07abbefa8535bdef173',run='41291145372',attempt='1',run_number='1'))
js('ALL_FORMS_COMPILED.json',P['forms'])
normal={p:b'SYNTHETIC ONLY' for p in P['paths']+[P['test']]}
for p in P['paths']:normal[p]='\n'.join(m['before'] for m in P['mutations'] if m['path']==p).encode()
import xml.etree.ElementTree as E
for form in P['forms']+['restored']:
 src=normal.copy()
 if form not in ('normal','restored'):
  m=next(m for m in P['mutations'] if m['id']==form)
  src[m['path']]=src[m['path']].replace(m['before'].encode(),m['after'].encode())
 for stage in (['compile-'+form,'observe-'+form] if form!='restored' else ['observe-restored']):
  for p,b in src.items():files[stage+'/source/'+p]=b
  js(stage+'/SOURCE_HASHES.json',{p:sha(b) for p,b in src.items()})
  files[stage+'/output.log']=b'BUILD SUCCESSFUL'
  files[stage+'/exit.txt']=b'0'
  if stage.startswith('observe'):
   root=E.Element('testsuite')
   for name in P['case_names']:
    c=E.SubElement(root,'testcase',name=name,classname=P['class'])
    w=P['required_witnesses'].get(form)
    if name in P['expected_failed_identities'].get(form, []):
     f=E.SubElement(c,'failure',type='org.opentest4j.AssertionFailedError',message='SYNTHETIC')
     f.text='DiscipleOfFreyaliseScenarioTest.kt:'+str(w['line'])
     files[stage+'/exit.txt']=b'1'
   files[stage+'/junit/TEST-synthetic.xml']=E.tostring(root)
for k in ['initial','restored','final']:js(k+'/PROTECTED.json',{'synthetic':'unchanged'})
def run(f):
 f=copy.deepcopy(f);f['MANIFEST.json']=json.dumps({n:sha(b) for n,b in f.items()}).encode()
 with tempfile.NamedTemporaryFile(suffix='.zip') as t:
  with zipfile.ZipFile(t.name,'w') as z:
   for n,b in f.items():z.writestr(n,b)
  return subprocess.run(['python3','/workspace/scratch/b9827535038f/extension-receive.py',t.name],capture_output=True)
good=run(files);assert good.returncode==0,good.stderr.decode()
assert all(json.loads(good.stdout)['candidate_witnesses'].values())
checks=[]
for label,mutate in [
 ('wrong_run',lambda f:f.__setitem__('BINDING.json',f['BINDING.json'].replace(b'41291145372',b'41291145373'))),
 ('missing_compile',lambda f:f.pop('ALL_FORMS_COMPILED.json')),
 ('compile_failed',lambda f:f.__setitem__('compile-normal/exit.txt',b'1')),
 ('source_corrupt',lambda f:f.__setitem__('observe-offer_guard/source/'+P['paths'][0],b'wrong')),
 ('skip',lambda f:f.__setitem__('observe-normal/junit/TEST-synthetic.xml',f['observe-normal/junit/TEST-synthetic.xml'].replace(b' />',b'><skipped/></testcase>',1))),
 ('missing_case',lambda f:f.__setitem__('observe-normal/junit/TEST-synthetic.xml',b'<testsuite/>')),
 ('protected_change',lambda f:f.__setitem__('final/PROTECTED.json',b'{}')),
]:
 f=copy.deepcopy(files);mutate(f);r=run(f);assert r.returncode!=0,label;checks.append(label)
print(json.dumps(dict(synthetic_only=True,valid_shape_accepted=True,corruptions_rejected=checks,real_artifact_received=False),indent=2))


