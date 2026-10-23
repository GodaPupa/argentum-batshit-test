"""Read immutable Git objects; never execute builds or tests."""
from pathlib import Path
import json, subprocess, hashlib, sys
out=Path(__file__).resolve().parent
repo=out.parents[3]
src='418cdfdacd5191549e9fc8e4a071330120b071b7'
val='30904cb20e03178adb713cc4894dfd3b45b92828'
base='a94e01a76df686200812f0cb75611c80c73b9f4b'
def git(*args): return subprocess.check_output(['git','-C',str(repo),*args])
p=json.loads((out/'PROTOCOL.json').read_text())
b=json.loads((out/'EXPECTED_BINDINGS.json').read_text())
records={}
for path,v in (b|p['source_manifest']).items():
    body=git('show',src+':'+path)
    sha=hashlib.sha256(body).hexdigest()
    blob=git('rev-parse',src+':'+path).decode().strip()
    assert sha==v['sha256'],path
    if 'git_blob' in v: assert blob==v['git_blob'],path
    if 'bytes' in v: assert len(body)==v['bytes'],path
    records[path]={'sha256':sha,'git_blob':blob,'bytes':len(body)}
    if 'baseline_sha256' in v:
        assert hashlib.sha256(git('show',base+':'+path)).hexdigest()==v['baseline_sha256'],path
workflow='.github/workflows/manual-counter-metamorph-r1-20261022.yml'
assert git('show',val+':'+workflow)==(out/'WORKFLOW.yml').read_bytes()
diff=git('diff','--name-status',base,src).decode()
validation_diff=git('diff','--name-status',src,val).decode()
changed_tests=[l for l in diff.splitlines() if '/src/test/' in l and '/snapshots/' not in l]
assert all(l.startswith('A\t') for l in changed_tests),changed_tests
assert git('rev-parse',src+'^{tree}').decode().strip()=='fdb37800de1dbabfa076e15f7d43577e5d3a91d2'
assert git('rev-parse',val+'^').decode().strip()==src
assert validation_diff=='A\t'+workflow+'\n'
r={'status':'PASS','source':src,'source_tree':git('rev-parse',src+'^{tree}').decode().strip(),
   'source_parent':git('rev-parse',src+'^').decode().strip(),'validation':val,
   'validation_tree':git('rev-parse',val+'^{tree}').decode().strip(),'baseline':base,
   'protected_bindings':len(b),'source_manifest_entries':len(p['source_manifest']),
   'records':records,'source_diff':diff,'validation_diff':validation_diff,'no_preexisting_tests_edited':True}
if '--write' in sys.argv: (out/'GIT_SOURCE_AUDIT.json').write_text(json.dumps(r,indent=2)+'\n')
else: assert json.loads((out/'GIT_SOURCE_AUDIT.json').read_text())==r
print(json.dumps({k:v for k,v in r.items() if k not in ('records','source_diff')},indent=2))
