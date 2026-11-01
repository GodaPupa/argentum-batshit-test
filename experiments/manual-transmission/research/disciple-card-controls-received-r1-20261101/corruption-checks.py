"""Corruption checks on throwaway copies; original artifact never modified."""
from pathlib import Path
import importlib.util,json,zipfile,tempfile,hashlib,copy
spec=importlib.util.spec_from_file_location('receiver','card-r1-failure-receive.py');r=importlib.util.module_from_spec(spec);spec.loader.exec_module(r)
protocol=Path('card-controls-protocol.json').read_bytes();P=json.loads(protocol)
z=zipfile.ZipFile('card-r1-original.zip');original={n:z.read(n) for n in z.namelist() if not n.endswith('/') and n!='MANIFEST.json'}
xml=next(n for n in original if n.startswith('observe-normal/junit/') and n.endswith('.xml'))
def run(f):
 f=dict(f);f['MANIFEST.json']=json.dumps({n:hashlib.sha256(b).hexdigest() for n,b in f.items()}).encode()
 with tempfile.NamedTemporaryFile(suffix='.zip') as t:
  with zipfile.ZipFile(t.name,'w',zipfile.ZIP_DEFLATED) as z:
   for n,b in f.items():z.writestr(n,b)
  return r.inspect_archive(t.name,protocol)[3]
checks=[]
for label,mutate in [
 ('wrong_run',lambda f:f.__setitem__('BINDING.json',f['BINDING.json'].replace(b'41293652735',b'41293652736'))),
 ('missing_compile',lambda f:f.pop('ALL_FORMS_COMPILED.json')),
 ('compile_error',lambda f:f.__setitem__('compile-normal/exit.txt',b'1')),
 ('changed_mutant_source',lambda f:f.__setitem__('compile-fixed_one/source/'+P['paths'][0],b'changed')),
 ('missing_raw_report',lambda f:f.pop(xml)),
 ('wrong_multiplicity',lambda f:f.__setitem__(xml,f[xml].replace(b'tests="65"',b'tests="64"'))),
 ('wrong_assertion_frame',lambda f:f.__setitem__(xml,f[xml].replace(b'.kt:1055',b'.kt:1054'))),
 ('summary_mismatch',lambda f:f.__setitem__('observe-normal/REPORT.json',f['observe-normal/REPORT.json'].replace(b'"total": 65',b'"total": 64'))),
 ('protected_changed',lambda f:f.__setitem__('final/PROTECTED.json',b'{}')),
 ('false_success',lambda f:f.__setitem__('RESULT.json',b'{}')),
 ('false_negative_witness',lambda f:f.__setitem__('WITNESSES.json',b'{}')),
 ('unexpected_mutant_behavior',lambda f:f.__setitem__('observe-fixed_one/exit.txt',b'1')),
]:
 bad=copy.deepcopy(original);mutate(bad)
 try:run(bad)
 except (AssertionError,KeyError,ValueError):checks.append(label)
 else:raise AssertionError(label)
assert len(checks)==12
result=dict(corruption_checks_only=True,corruptions_rejected=checks,original_archive_unchanged=True)
Path('card-r1-failure-synthetic-result.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))
