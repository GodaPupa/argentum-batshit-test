"""Diagnose remaining frozen gates after failed original completeness; never accept R1."""
import ast, hashlib, importlib.util, json, os, zipfile
from pathlib import Path
root=Path(__file__).parent
os.environ['MANUAL_RECEIPT_ROOT']=str(root)
spec=importlib.util.spec_from_file_location('pinned_receiver',root/'RECEIVE.py')
receiver=importlib.util.module_from_spec(spec);spec.loader.exec_module(receiver)
m=json.loads((root/'metadata.json').read_text())
with zipfile.ZipFile(root/'original-12599508412.zip') as z:
    raw={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
manifest={p:d for d,p in (l.split('  ',1) for l in raw['SHA256SUMS'].decode().splitlines())}
supplement=json.loads((root/'supplemental-source.json').read_text())
assert set(supplement)==set(manifest)-set(raw)
for path,entry in supplement.items():
    body=entry['content'].encode()
    assert receiver.sha(body)==manifest[path] and receiver.blob(body)==entry['sha']
    raw[path]=body
# Execute the unchanged downstream assertions only. The original archive remains
# incomplete; supplemental immutable repository bytes are diagnostic, not original evidence.
tree=ast.parse((root/'RECEIVE.py').read_text())
body=next(n.body for n in tree.body if isinstance(n,ast.FunctionDef) and n.name=='receive')
start=next(i for i,n in enumerate(body) if isinstance(n,ast.Assert) and ast.unparse(n.test).startswith("blob(raw['protocol/WORKFLOW.yml'])"))
end=next(i for i,n in enumerate(body) if isinstance(n,ast.Assign) and ast.unparse(n.value).startswith("dict(common, state='ACCEPTED_BOUNDED"))
ns=dict(receiver.__dict__,m=m,r=m['run'],a=m['artifact'],job=m['jobs']['jobs'][0],delta=m['source_delta']['files'],raw=raw,manifest=manifest)
fn=ast.FunctionDef(name='downstream_diagnostic',args=ast.arguments(posonlyargs=[],args=[],kwonlyargs=[],kw_defaults=[],defaults=[]),body=body[start:end]+[ast.Return(value=ast.Call(func=ast.Name(id='locals',ctx=ast.Load()),args=[],keywords=[]))],decorator_list=[])
exec(compile(ast.fix_missing_locations(ast.Module(body=[fn],type_ignores=[])),'<unchanged downstream receiver assertions>','exec'),ns)
ns.update(ns['downstream_diagnostic']())
result={'state':'DIAGNOSTIC_REMAINING_GATES_PASSED_WITH_EXPLICIT_SOURCE_SUPPLEMENT_NOT_ACCEPTANCE',
'original_artifact_accepted':False,'missing_original_members':sorted(supplement),
'supplemental_blobs':{p:e['sha'] for p,e in supplement.items()},
'original_raw_stages':{k:v['totals'] for k,v in ns['stages'].items()},
'compilation_controls':list(ns['compilations']),'protected_bindings':143,'goldens':23,'legacy_fixtures':82,
'qualification_repeated':False,'receiver_assertions_modified':False,
'limitation':'Three protected files absent from original ZIP; original complete-manifest gate failed and remains failed.'}
(root/'REMAINING_GATES_DIAGNOSTIC.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result,indent=2))
