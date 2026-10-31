"""Synthetic receiver rejection tests; produces no experiment evidence."""
from pathlib import Path
import copy,hashlib,json,tempfile,zipfile,xml.etree.ElementTree as ET
from receive import receive
HERE=Path(__file__).resolve().parent
P=json.loads((HERE/'PROTOCOL.json').read_text());E=json.loads((HERE/'RECEIVING_PINS.json').read_text())
sha=lambda b:hashlib.sha256(b).hexdigest()
enc=lambda o:(json.dumps(o,indent=2)+'\n').encode()
files={'BINDING.json':enc({k:v for k,v in E.items() if k!='protected'}),'scenario.kt':(HERE/'scenario.kt').read_bytes()}
files['WORKFLOW.yml']=(HERE/'WORKFLOW.yml').read_bytes()
for f in ['PROTOCOL.json','run.py','validation.just','SOURCE_REVIEW.json']:
 files['protocol/'+f]=(HERE/f).read_bytes()
for form in P['forms']:
 b=(HERE/'forms'/f'{form}.kt').read_bytes();files['protocol/forms/'+form+'.kt']=b
 prefix='compile-'+form+'/'
 files[prefix+'CARD.kt']=b;files[prefix+'exit.txt']=b'0\n'
 files[prefix+'output.log']=b':mtg-sets:2024:tests:compileTestKotlin\nBUILD SUCCESSFUL\n'
 files[prefix+'SUMMARY.json']=enc({'compile_passed':True})
 prefix='observe-'+form+'/'
 files[prefix+'CARD.kt']=b;files[prefix+'exit.txt']=b'0\n'
 rows=[];root=ET.Element('testsuite')
 for name in P['case_names']:
  ET.SubElement(root,'testcase',name=name,classname=P['class'])
  rows.append(dict(name=name,classname=P['class'],failures=[],errors=[],skipped=0))
 files[prefix+'junit/synthetic/TEST-case.xml']=ET.tostring(root)
 files[prefix+'SUMMARY.json']=enc(dict(total=28,failed=0,skipped=0,rows=rows))
files['ALL_FORMS_COMPILED.json']=enc(dict(forms=P['forms'],all_compiled_before_behavior=True))
for label in ['initial','restored','final']:files[label+'/PROTECTED.json']=enc(E['protected'])
def run(fs,meta_edit=None):
 with tempfile.TemporaryDirectory() as tmp:
  p=Path(tmp)/'synthetic.zip';fs=dict(fs)
  fs['MANIFEST.json']=enc({k:sha(v) for k,v in fs.items()})
  with zipfile.ZipFile(p,'w') as z:
   for k,v in fs.items():z.writestr(k,v)
  m=dict(run_id=int(E['run']),attempt=1,head_sha=E['validation'],event='push',job_id=1,artifact_id=1,size_in_bytes=p.stat().st_size,digest='sha256:'+sha(p.read_bytes()))
  if meta_edit:meta_edit(m)
  return receive(p,m,HERE)
assert run(files)['state']=='ORIGINAL_DIAGNOSTIC_VERIFIED_NOT_ADMISSION'
checks=[]
def rejects(label,edit=None,meta=None):
 fs=copy.deepcopy(files)
 if edit:edit(fs)
 try:run(fs,meta)
 except (ValueError,KeyError):checks.append(label)
 else:raise AssertionError('accepted corrupt synthetic archive: '+label)
rejects('wrong archive digest',meta=lambda m:m.update(digest='sha256:'+'0'*64))
rejects('wrong byte count',meta=lambda m:m.update(size_in_bytes=m['size_in_bytes']+1))
rejects('wrong attempt',meta=lambda m:m.update(attempt=2))
rejects('wrong validation',meta=lambda m:m.update(head_sha='0'*40))
rejects('changed normal source',lambda f:f.update({'protocol/forms/normal.kt':b'changed'}))
rejects('changed test bytes',lambda f:f.update({'scenario.kt':b'changed'}))
rejects('protected bytes mismatch',lambda f:f.update({'final/PROTECTED.json':enc({})}))
rejects('missing compile first marker',lambda f:f.pop('ALL_FORMS_COMPILED.json'))
rejects('compile failure with behavioral reports',lambda f:f.update({'compile-normal/exit.txt':b'1\n','compile-normal/SUMMARY.json':enc({'compile_passed':False})}))
key='observe-normal/junit/synthetic/TEST-case.xml'
def corrupt_xml(f,kind):
 root=ET.fromstring(f[key]);first=root.find('testcase')
 if kind=='missing':root.remove(first)
 elif kind=='duplicate':root.append(copy.deepcopy(first))
 elif kind=='skip':ET.SubElement(first,'skipped')
 elif kind=='class':first.set('classname','unrelated')
 f[key]=ET.tostring(root)
for kind in ['missing','duplicate','skip','class']:rejects('raw report '+kind,lambda f,k=kind:corrupt_xml(f,k))
rejects('summary cannot conceal raw counts',lambda f:f.update({'observe-normal/SUMMARY.json':enc(dict(total=27,failed=0,skipped=0,rows=[]))}))
rejects('unsafe member path',lambda f:f.update({'../outside':b'unsafe'}))
print(json.dumps(dict(state='SYNTHETIC_AUDITOR_TESTS_ONLY',accepted_synthetic_valid_archive=True,rejected=checks,experiment_behavior_executed=False),indent=2))
