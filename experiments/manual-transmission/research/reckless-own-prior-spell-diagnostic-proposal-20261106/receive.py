"""Offline receive/audit of one original ZIP and independently fetched immutable Actions pins.
Never downloads, reruns, accepts missing evidence, or writes to a repository ref.
Usage: python receive.py ORIGINAL.zip RECEIVING_PINS.json OUTPUT_DIRECTORY
"""
from pathlib import Path, PurePosixPath
import hashlib, json, sys, zipfile, traceback
from report_audit import rows, verify
sha = lambda b: hashlib.sha256(b).hexdigest()
blob = lambda b: hashlib.sha1(b'blob ' + str(len(b)).encode() + b'\0' + b).hexdigest()
archive, pins_path, output = map(Path, sys.argv[1:])
assert not output.exists(), 'Receiving output must be new; never overwrite a prior receipt'
pins_bytes = pins_path.read_bytes()
pins = json.loads(pins_bytes)
data = archive.read_bytes()
a = pins['artifact']; run = pins['run']; jobs = pins['jobs']; frozen = pins['frozen']
assert run['status'] == 'completed' and run['run_attempt'] == 1
assert run['event'] == 'push' and run['head_branch'] == 'manual-transmission/quarantine-recovery-20261106'
assert run['head_sha'] == frozen['event'] and run['id'] == a['workflow_run']['id']
assert a['workflow_run']['head_sha'] == run['head_sha'] and not a['expired']
assert a['size_in_bytes'] == len(data) and a['digest'] == 'sha256:' + sha(data)
assert len(jobs['jobs']) == jobs['total_count'] == 1
job = jobs['jobs'][0]
assert job['run_id'] == run['id'] and job['run_attempt'] == 1 and job['status'] == 'completed'
assert job['head_sha'] == run['head_sha']
assert run['id'] == pins['expected_run'] and job['id'] == pins['expected_job'] and a['id'] == pins['expected_artifact']
output.mkdir()
(output/'ORIGINAL.zip').write_bytes(data)
(output/'RECEIVING_PINS.json').write_bytes(pins_bytes)
with zipfile.ZipFile(archive) as z:
    names = z.namelist()
    assert len(names) == len(set(names)), 'Duplicate ZIP members'
    for info in z.infolist():
        p = PurePosixPath(info.filename)
        assert not p.is_absolute() and '..' not in p.parts and '\\' not in info.filename
        assert ((info.external_attr >> 16) & 0o170000) != 0o120000, 'No symlinks'
    z.extractall(output/'unpacked')
root = output/'unpacked'
def manifest(directory):
    declared = json.loads((directory/'MANIFEST.json').read_bytes())
    actual = {p.relative_to(directory).as_posix():sha(p.read_bytes()) for p in sorted(directory.rglob('*'))
              if p.is_file() and p != directory/'MANIFEST.json'}
    assert actual == declared, 'Exact bytes/digest manifest and file multiplicities'
    return len(actual)
count = manifest(root)
envelope = json.loads((root/'RECOVERY_EVENT.json').read_bytes())
for key in ['event','event_tree','first_parent','first_tree','validation','validation_tree','source','source_tree','workflow','protocol_blob']:
    assert envelope[key] == frozen[key], key
assert envelope['event_workflow_blob'] == envelope['validation_workflow_blob'] == frozen['workflow_blob']
assert envelope['run'] == run['id'] and envelope['attempt'] == envelope['run_number'] == 1
assert envelope['epoch'] == 'manual-quarantine-20261106-01'
assert envelope['branch'] == run['head_branch']
evidence = root/'qualification'
qcount = manifest(evidence)
protocol_bytes = (evidence/'protocol/PROTOCOL.json').read_bytes()
assert blob(protocol_bytes) == frozen['protocol_blob']
P = json.loads(protocol_bytes)
assert P['class'] == 'com.wingedsheep.engine.scenarios.SelfAlternativePriorSpellEligibilityDiagnosticTest'
binding = json.loads((evidence/'BINDING.json').read_bytes())
for actual, expected in [('SOURCE','source'),('SOURCE_TREE','source_tree'),('WORKFLOW','workflow'),('validation','validation'),('validation_tree','validation_tree'),('workflow_blob','workflow_blob'),('protocol_blob','protocol_blob')]:
    assert binding[actual] == frozen[expected], actual
assert binding['GITHUB_RUN_ID'] == str(run['id']) and binding['GITHUB_RUN_ATTEMPT'] == binding['GITHUB_RUN_NUMBER'] == '1'
assert blob((evidence/'WORKFLOW.yml').read_bytes()) == frozen['workflow_blob']
for path, digest in P['bindings'].items():
    assert sha((evidence/'initial/source'/path).read_bytes()) == digest, path
def tree_inventory(data):
    nested = {}; leaves = {}
    for item in data.split(b'\0'):
        if not item:
            continue
        header, path = item.split(b'\t', 1)
        mode, kind, oid = header.split(b' ')
        leaves[path.decode()] = (mode.decode(), kind.decode(), oid.decode())
        parts = path.split(b'/'); node = nested
        for part in parts[:-1]:
            node = node.setdefault(part, {})
        node[parts[-1]] = (mode, bytes.fromhex(oid.decode()))
    def tree(node):
        body = b''
        for name, value in sorted(node.items(), key=lambda e:e[0]+(b'/' if isinstance(e[1],dict) else b'')):
            mode, oid = (b'40000', tree(value)) if isinstance(value,dict) else value
            body += mode + b' ' + name + b'\0' + oid
        return hashlib.sha1(b'tree '+str(len(body)).encode()+b'\0'+body).digest()
    return tree(nested).hex(), leaves
base_tree, base_leaves = tree_inventory((evidence/'initial/BASE-TREE.bin').read_bytes())
source_tree, source_leaves = tree_inventory((evidence/'initial/SOURCE-TREE.bin').read_bytes())
assert base_tree == P['base_tree'] and source_tree == frozen['source_tree']
assert all(source_leaves.get(p) == entry for p,entry in base_leaves.items()), 'Every existing file binding preserved'
assert sorted(set(source_leaves)-set(base_leaves)) == P['source_delta']
record = P['record']
for path in (evidence/'protocol').rglob('*'):
    if path.is_file():
        relative = record+'/'+path.relative_to(evidence/'protocol').as_posix()
        assert source_leaves[relative][2] == blob(path.read_bytes()), relative
checks = {}; failures = {}
for name in ['normal', *P['mutants']]:
    compile_dir = evidence/('compile-'+name)
    checks['compile-'+name] = compile_dir.exists() and (compile_dir/'exit.txt').read_text().strip() == '0'
    for path in P['normal_forms']:
        form = evidence/'forms'/name/path
        if form.exists():
            expected = P['mutants'][name]['sha256'] if name != 'normal' and path == P['mutants'][name]['path'] else P['bindings'][path]
            assert sha(form.read_bytes()) == expected, (name, path)
        else:
            checks['compile-'+name] = False
compile_first = evidence/'COMPILE-FIRST.json'
checks['compile-first'] = compile_first.exists() and json.loads(compile_first.read_text()) == dict(all_forms_compiled=True, forms=['normal', *P['mutants']]) and all(checks.values())
for name, mutant in [('normal',None), *[('negative-'+n,m) for n,m in P['mutants'].items()], ('restored-normal',None)]:
    stage = evidence/name
    raw = rows(stage/'junit')
    (output/(name+'-ROWS.json')).write_text(json.dumps(raw,indent=2)+'\n')
    try:
        assert checks['compile-first'], 'No behavioral acceptance without all five compiled forms'
        assert stage.exists(), 'Stage missing'
        checks[name] = verify(raw,P,int((stage/'exit.txt').read_text()),mutant)
    except Exception:
        failures[name] = traceback.format_exc()
        checks[name] = False
restore = json.loads((evidence/'RESTORATION.json').read_text())
checks['restored-bytes'] = restore == dict(normal_restored=True,tracked_diff='',status='')
checks['final-rebinding'] = (evidence/'final/TRACKED.diff').exists() and (evidence/'final/TRACKED.diff').read_bytes() == b''
if checks['final-rebinding']:
    for path,digest in P['bindings'].items():
        assert sha((evidence/'final/source'/path).read_bytes()) == digest, path
accepted = all(bool(v) for v in checks.values()) and not failures and run['conclusion'] == job['conclusion'] == 'success'
receipt = dict(state='ORIGINAL_DIAGNOSTIC_RECEIVED',diagnostic_gates_verified=accepted,production_admitted=False,
               source=frozen['source'],source_tree=frozen['source_tree'],validation=frozen['validation'],event=frozen['event'],
               run=run['id'],job=job['id'],attempt=1,artifact=a['id'],bytes=len(data),sha256=sha(data),
               pins_sha256=sha(pins_bytes),manifest_entries=count,qualification_manifest_entries=qcount,checks=checks,
               audit_failures=failures,original_failure_text=(evidence/'FAILURE.txt').read_text() if (evidence/'FAILURE.txt').exists() else None,
               disposition='Review original failure logs and JUnit before diagnosis; compiler errors are not behavioral evidence.' if not accepted else 'Diagnostic evidence only; no production or card admission.')
(output/'RECEIPT.json').write_text(json.dumps(receipt,indent=2)+'\n')
print(json.dumps({k:receipt[k] for k in ['state','diagnostic_gates_verified','run','artifact','sha256']},indent=2))
