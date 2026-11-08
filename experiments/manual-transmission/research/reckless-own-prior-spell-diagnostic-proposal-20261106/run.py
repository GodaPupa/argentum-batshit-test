"""One frozen test-only qualification. Temporary mutations are restored even after failure."""
from pathlib import Path
import hashlib, json, os, subprocess, traceback
from report_audit import rows, verify
R = Path.cwd()
H = Path(__file__).resolve().parent
P = json.loads((H / 'PROTOCOL.json').read_text())
O = R / P['evidence']
sha = lambda b: hashlib.sha256(b).hexdigest()
git = lambda *a: subprocess.check_output(['git', *a], text=True).strip()
def save(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(value)
def js(path, value):
    save(path, (json.dumps(value, indent=2) + '\n').encode())
normal = {path: (R / path).read_bytes() for path in P['normal_forms']}
def bind(label):
    source = os.environ['SOURCE']
    assert git('rev-parse', source + '^{tree}') == os.environ['SOURCE_TREE']
    assert git('show', '-s', '--format=%P', 'HEAD') == source
    assert git('diff', '--name-only', source, 'HEAD') == os.environ['WORKFLOW']
    assert git('show', '-s', '--format=%P', source) == P['base']
    assert git('diff', '--name-only', P['base'], source).splitlines() == P['source_delta']
    assert all(line.startswith('A\t') for line in git('diff', '--name-status', P['base'], source).splitlines())
    assert not git('status', '--porcelain', '--untracked-files=no')
    # All accepted shipping, SDK, fixtures, tests and frozen experiment originals remain unchanged.
    allowed = [P['test'], 'experiments/manual-transmission/', '.github/workflows/']
    differences = git('diff', '--name-only', P['accepted_runtime_source'], source).splitlines()
    assert all(p == allowed[0] or p.startswith(tuple(allowed[1:])) for p in differences), differences
    for path, digest in P['bindings'].items():
        data = (R / path).read_bytes()
        assert sha(data) == digest, path
        save(O / label / 'source' / path, data)
    save(O / label / 'BASE-TREE.bin', subprocess.check_output(['git', 'ls-tree', '-rz', P['base']]))
    save(O / label / 'SOURCE-TREE.bin', subprocess.check_output(['git', 'ls-tree', '-rz', source]))
    save(O / label / 'TRACKED.diff', subprocess.check_output(['git', 'diff', '--']))
def form(name):
    for path, data in normal.items():
        (R / path).write_bytes(data)
    if name != 'normal':
        m = P['mutants'][name]
        patch_bytes = (R / m['patch']).read_bytes()
        assert sha(patch_bytes) == m['patch_sha256']
        patch = json.loads(patch_bytes)
        data = normal[m['path']]
        assert sha(data) == m['normal_sha256']
        assert data.count(patch['before'].encode()) == 1
        changed = data.replace(patch['before'].encode(), patch['after'].encode())
        assert sha(changed) == m['sha256']
        (R / m['path']).write_bytes(changed)
    for path, digest in P['bindings'].items():
        expected = P['mutants'][name]['sha256'] if name != 'normal' and path == P['mutants'][name]['path'] else digest
        assert sha((R / path).read_bytes()) == expected, path
    for path in normal:
        save(O / 'forms' / name / path, (R / path).read_bytes())
    save(O / 'forms' / name / 'TRACKED.diff', subprocess.check_output(['git', 'diff', '--']))
def stage(name, recipe):
    for path in R.glob('**/build/test-results/test/TEST-*.xml'):
        if O not in path.parents:
            path.unlink()
    folder = O / name
    folder.mkdir()
    with (folder / 'output.log').open('wb') as log:
        code = subprocess.run(['just', '--working-directory', str(R), '--justfile', str(H / 'validation.just'), recipe],
                              stdout=log, stderr=subprocess.STDOUT,
                              env={k:v for k,v in os.environ.items() if k not in ['KOTEST_INCLUDE_PATTERN', 'UPDATE_SNAPSHOTS']}).returncode
    (folder / 'exit.txt').write_text(str(code) + '\n')
    for path in R.glob('**/build/test-results/test/TEST-*.xml'):
        if O not in path.parents:
            save(folder / 'junit' / path.relative_to(R), path.read_bytes())
    return code

def behavior(name, mutant=None):
    code = stage(name, 'behavior')
    report_rows = rows(O / name / 'junit')
    js(O / (name + '-ROWS.json'), dict(exit=code, rows=report_rows))
    js(O / (name + '-CHECK.json'), verify(report_rows, P, code, mutant))

try:
    O.mkdir()
    assert os.environ['GITHUB_RUN_ATTEMPT'] == os.environ['GITHUB_RUN_NUMBER'] == '1'
    bind('initial')
    js(O / 'BINDING.json', {k:os.environ[k] for k in ['SOURCE','SOURCE_TREE','WORKFLOW','GITHUB_RUN_ID','GITHUB_RUN_ATTEMPT','GITHUB_RUN_NUMBER']} |
       dict(validation=git('rev-parse', 'HEAD'), validation_tree=git('rev-parse','HEAD^{tree}'),
            workflow_blob=git('hash-object', os.environ['WORKFLOW']), protocol_blob=git('hash-object',str(H/'PROTOCOL.json'))))
    for path in H.rglob('*'):
        if path.is_file() and '__pycache__' not in path.parts:
            save(O / 'protocol' / path.relative_to(H), path.read_bytes())
    save(O / 'WORKFLOW.yml', (R / os.environ['WORKFLOW']).read_bytes())
    for name in ['normal', *P['mutants']]:
        form(name)
        assert stage('compile-' + name, 'compile') == 0, ('Compile-first failure', name)
    js(O / 'COMPILE-FIRST.json', dict(all_forms_compiled=True, forms=['normal', *P['mutants']]))
    form('normal')
    behavior('normal')
    for name, mutant in P['mutants'].items():
        form(name)
        behavior('negative-' + name, mutant)
    form('normal')
    behavior('restored-normal')
    bind('final')
    js(O / 'RESULT.json', dict(state='DIAGNOSTIC_GATES_PASSED_ORIGINAL_RECEIVING_REQUIRED', accepted=False,
                              normal_rows=24, compiled_forms=5, negative_witnesses=18, restored_normal=True))
except Exception:
    O.mkdir(exist_ok=True)
    (O / 'FAILURE.txt').write_text(traceback.format_exc())
    raise
finally:
    for path, data in normal.items():
        (R / path).write_bytes(data)
    js(O / 'RESTORATION.json', dict(normal_restored=all((R/p).read_bytes()==b for p,b in normal.items()),
                                   tracked_diff=git('diff','--'), status=git('status','--porcelain','--untracked-files=no')))
    js(O / 'MANIFEST.json', {p.relative_to(O).as_posix():sha(p.read_bytes()) for p in sorted(O.rglob('*'))
                            if p.is_file() and p != O / 'MANIFEST.json'})
