"""Original full compatibility receiving, with explicit classification still required.

Inputs: original ZIP, original API.json, output directory. Never dispatches a build.
The sibling receiver-data directory contains frozen, independently read-back files.
"""
from pathlib import Path, PurePosixPath
import sys, json, hashlib, zipfile, tempfile, importlib.util, re
from collections import Counter

HERE = Path(__file__).resolve().parent
DATA = HERE / 'receiver-data'
sha = lambda b: hashlib.sha256(b).hexdigest()
gitsha = lambda b: hashlib.sha1(b'blob ' + str(len(b)).encode() + b'\0' + b).hexdigest()
E = json.loads((DATA / 'EXPECTED.json').read_text())
P = json.loads((DATA / 'PROTOCOL.json').read_text())

def load_auditor(name):
    spec = importlib.util.spec_from_file_location(name, DATA / (name + '.py'))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module

def manifest(z):
    names = z.namelist()
    assert len(names) == len(set(names)), 'duplicate ZIP entries'
    for n in names:
        p = PurePosixPath(n)
        assert not p.is_absolute() and '..' not in p.parts and '\\' not in n
        assert not z.getinfo(n).external_attr >> 16 & 0o170000 == 0o120000
    files = {n: z.read(n) for n in names if not n.endswith('/')}
    declared = json.loads(files['MANIFEST.json'])
    observed = {n: sha(b) for n, b in files.items() if n != 'MANIFEST.json'}
    assert observed == declared, 'manifest mismatch'
    return files

def api_binding(api, archive):
    run, jobs, artifact = api['run'], api['jobs'], api['artifact']
    assert run['id'] == E['run'] and run['head_sha'] == E['validation']
    assert run['head_branch'] == E['branch'] and run['path'] == E['workflow']
    assert run['run_attempt'] == run['run_number'] == 1 and run['event'] == 'push'
    assert run['status'] == 'completed' and run['conclusion'] == 'success'
    assert jobs['total_count'] == len(jobs['jobs']) == 1
    job = jobs['jobs'][0]
    assert job['id'] == E['job'] and job['run_id'] == E['run']
    assert job['run_attempt'] == 1 and job['status'] == 'completed' and job['conclusion'] == 'success'
    assert artifact['name'] == E['artifact_name'] and not artifact['expired']
    assert artifact['workflow_run']['id'] == E['run']
    assert artifact['workflow_run']['head_sha'] == E['validation']
    assert artifact['size_in_bytes'] == len(archive)
    assert artifact['digest'] == 'sha256:' + sha(archive)
    source, validation = api['source_commit'], api['validation_commit']
    assert source['sha'] == E['source'] and source['tree']['sha'] == E['tree']
    assert [x['sha'] for x in source['parents']] == [P['base']]
    assert validation['sha'] == E['validation'] and validation['tree']['sha'] == E['validation_tree']
    assert [x['sha'] for x in validation['parents']] == [E['source']]

def source_binding(files):
    b = json.loads(files['BINDING.json'])
    for k in ['source','tree','validation','validation_tree','workflow','workflow_blob']:
        assert b[k] == E[k], k
    for k in ['run','attempt','run_number']:
        assert int(b[k]) == E[k], k
    assert gitsha(files['WORKFLOW.yml']) == E['workflow_blob']
    assert files['WORKFLOW.yml'] == (DATA / 'WORKFLOW.yml').read_bytes()
    for name, digest in E['protocol_hashes'].items():
        assert sha(files['protocol/' + name]) == digest, name
        assert files['protocol/' + name] == (DATA / name).read_bytes(), name
    assert set(n.removeprefix('protocol/') for n in files if n.startswith('protocol/')) == set(E['protocol_hashes'])
    assert gitsha(files['EARLY_RECEIPT.json']) == P['early_receipt_blob']
    early = json.loads(files['EARLY_RECEIPT.json'])
    assert early['source'] == P['early_source'] and early['bounded_controls_verified']
    assert early['compiled_forms'] == 20 and early['witnesses'] == 24 and early['controls'] == 57
    for label in ['initial', 'final']:
        assert files[label + '/TRACKED.diff'] == b''
        assert json.loads(files[label + '/UNCHANGED_TREES.json']) == E['protected_trees']
        for path, digest in P['bindings'].items():
            assert sha(files[label + '/source/' + path]) == digest, path
        for path, digest in P['git_bindings'].items():
            assert gitsha(files[label + '/source/' + path]) == digest, path
    initial = {n.removeprefix('initial/source/'): b for n,b in files.items() if n.startswith('initial/source/')}
    final = {n.removeprefix('final/source/'): b for n,b in files.items() if n.startswith('final/source/')}
    assert initial == final
    assert set(initial) == set(P['bindings']) | set(P['git_bindings'])

def assay_binding(files):
    prefix = 'assay-mh3/'
    b = json.loads(files[prefix + 'BINDINGS.json'])
    original = files[prefix + 'original/mtg-sets/src/test/resources/snapshots/cards/MH3.json']
    candidate = files[prefix + 'candidate/mtg-sets/src/test/resources/snapshots/cards/MH3.json']
    assert sha(original) == b['original_snapshot'] == 'b021d21c371237f2f080f751b9ccc256e5de91f4bb559a0c5fb4c50ae537b4c3'
    assert sha(candidate) == b['candidate_snapshot'] == P['bindings']['mtg-sets/src/test/resources/snapshots/cards/MH3.json']
    assert sha(files[prefix + 'oracle-cards.jsonl.gz']) == b['corpus']
    assert b['distribution'] and any(k.startswith('lib/') for k in b['distribution'])
    assert files[prefix+'original/settings.gradle.kts'] == files[prefix+'candidate/settings.gradle.kts']
    assert b['classification_required'] and not b['admitted']
    reports = {}
    for name, expected in [('original',24),('candidate',25)]:
        assert files[prefix+name+'/exit.txt'].strip() == b'0'
        text = files[prefix+name+'/stdout.log'].decode()
        assert int(re.search(r'Hand-written cards\s+(\d+)',text)[1]) == expected
        assert int(re.search(r'golden would not decode\s+(\d+)',text)[1]) == 0
        assert 'examples are capped' not in text and 'raise --top' not in text
        reports[name] = text
    return {'original_cards':24,'candidate_cards':25,'corpus_sha256':b['corpus'],
            'classification_required':True,'semantic_pass_inferred':False}

def receive(zip_path, api_path, output):
    archive = Path(zip_path).read_bytes()
    api = json.loads(Path(api_path).read_text())
    api_binding(api, archive)
    with zipfile.ZipFile(zip_path) as z:
        files = manifest(z)
    source_binding(files)
    assert 'FAILURE.txt' not in files
    assert json.loads(files['RESULT.json']) == {'state':'DEPENDENT_EXECUTION_COMPLETE_ORIGINAL_RECEIVING_PENDING','feature_admitted':False,'card_admitted':False}
    I = json.loads(files['INVENTORY.json'])
    assert sha(json.dumps(I,sort_keys=True,separators=(',',':')).encode()) == P['inventory_sha256']
    audit, reports = load_auditor('audit'), load_auditor('report_audit')
    with tempfile.TemporaryDirectory() as temporary:
        root = Path(temporary)
        for n,b in files.items():
            p=root/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(b)
        results = {'compile':reports.compile_gate(root/'compile'),
                   'focused':reports.reports(root/'focused',P['focused_cases'])}
        for name in ['cards','ordinary']:
            results[name] = audit.junit(root/name,I[name])
        contract = json.loads(files['initial/source/'+P['client_contract']])
        results['client'] = audit.client(root/'client',contract)
    for name, result in results.items():
        assert result == json.loads(files[name+'/SUMMARY.json']), name
    assert results['focused']['rows'] == 137 and results['cards']['rows'] == 396
    assert results['ordinary']['rows'] == 19212 and results['ordinary']['skips'] == 43
    assert results['client']['passed'] == 64
    for name in ['corpus','printing']:
        assert files[name+'/exit.txt'].strip() == b'0'
    assay = assay_binding(files)
    output = Path(output);output.mkdir(parents=True,exist_ok=False)
    (output/'RAW_REPORT_AUDIT.json').write_text(json.dumps(results,indent=2)+'\n')
    report = {'state':'ORIGINAL_RAW_GATES_VERIFIED_CLASSIFICATION_PENDING','accepted':False,
              'source':E['source'],'validation':E['validation'],'run':E['run'],'job':E['job'],
              'artifact':api['artifact']['id'],'archive_bytes':len(archive),'archive_sha256':sha(archive),
              'manifest_entries':len(files)-1,'source_bindings':len(P['bindings']),
              'git_bindings':len(P['git_bindings']),'focused':137,'cards':396,
              'ordinary':19212,'historical_skips':43,'client':64,'assay':assay,
              'remaining':'Read and classify every original Assay finding/decline and printing output; verify protected snapshot original-byte preservation and API artifact list; only then evaluate full admission.'}
    (output/'RECEIVING.json').write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps(report))

if __name__ == '__main__':
    receive(*sys.argv[1:])
