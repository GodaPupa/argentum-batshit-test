"""Receive the original five-case source-history diagnostic exactly once. Never runs engine tests."""
from pathlib import Path
import hashlib, json, os, re, zipfile
import xml.etree.ElementTree as ET

ROOT = Path(os.environ.get('MANUAL_RECEIPT_ROOT', Path(__file__).resolve().parent))
SOURCE = 'c0b893b452ae166a75b43dea01383c425f215f4f'
TREE = '6b616b5ea6bae673fdec90b096d51bd16d932725'
BASE = 'e067e4b803d3cb2358d0b363929b940ebcfa2cbf'
VALIDATION = '4bc98b04ae47f676e3016574dc8c8a2253dd48b2'
VTREE = '213578eb275fc7afc44adb0d802ba920d5d30ea6'
RUN, JOB, WORKFLOW_ID = 40179990974, 121128810761, 395811282
WORKFLOW = '.github/workflows/manual-source-history-diagnostic-r2-20261025.yml'
WBLOB = 'd3c66c9fc9dcc40443e5662c8f47371c01f5c30b'
RECORD = 'experiments/manual-transmission/research/source-history-diagnostic-r2-20261025/'
TEST = 'rules-engine/src/test/kotlin/com/wingedsheep/engine/scenarios/CurrentTargetSourceHistoryDiagnosticTest.kt'
BLOBS = {
    RECORD+'gate.py':'65af259bc54253fedfb5b9eff30e0d3584a4c3d0',
    RECORD+'PROTOCOL.json':'b068d0fb0669cdba03cd224ccbd749ebd4e2de61',
    RECORD+'REVIEW.json':'267f90624f8195bb77626f21a32f859c59e84e7c',
    TEST:'e061a176df277f14ba71a8a8ae30bf622edbdded',
}
NAMES = [
    'SH01 live red source respects protection',
    'SH02 departed trigger still destroys an unprotected target',
    'SH03 departed activation still destroys an unprotected target',
    'SH04 departed red trigger respects captured source protection',
    'SH05 departed red activation respects captured source protection',
]
sha=lambda b:hashlib.sha256(b).hexdigest()
blob=lambda b:hashlib.sha1(b'blob '+str(len(b)).encode()+b'\0'+b).hexdigest()

def classify(xml, compile_exit, test_exit, compile_log, test_log):
    assert compile_exit.strip() == '0' and 'BUILD SUCCESSFUL' in compile_log
    assert not re.search(r'^> Task .*compile.* FAILED$', compile_log+'\n'+test_log, re.M)
    suite=ET.fromstring(xml); rows=suite.findall('testcase')
    assert len(rows)==5 and suite.get('tests')=='5'
    cls='com.wingedsheep.engine.scenarios.CurrentTargetSourceHistoryDiagnosticTest'
    assert sorted((r.get('classname'),r.get('name')) for r in rows)==sorted((cls,n) for n in NAMES)
    assert all(r.find('error') is None and r.find('skipped') is None for r in rows)
    failures={r.get('name'):r.find('failure') for r in rows if r.find('failure') is not None}
    assert suite.get('errors')==suite.get('skipped')=='0' and int(suite.get('failures'))==len(failures)
    failed_tasks=re.findall(r'^> Task (\S+) FAILED$',test_log,re.M)
    if not failures:
        assert test_exit.strip()=='0' and not failed_tasks and 'BUILD SUCCESSFUL' in test_log
        return 'HYPOTHESIS_NOT_REPRODUCED_ALL_FIVE_PASS',0
    assert set(failures)==set(NAMES[3:]) and test_exit.strip()!='0'
    assert failed_tasks==[':rules-engine:test'] and 'BUILD FAILED' in test_log
    for name,frame in zip(NAMES[3:],[81,85]):
        f=failures[name]
        assert f.get('type')=='org.opentest4j.AssertionFailedError'
        assert f'CurrentTargetSourceHistoryDiagnosticTest.kt:{frame}' in (f.text or '')
        assert 'expected:<true> but was:<false>' in (f.text or '') or 'expected: true but was: false' in (f.text or '')
    return 'REPRODUCED_TWO_SOURCE_HISTORY_PROTECTION_FAILURES_THREE_CONTROLS_PASS',2

def receive():
    m=json.loads((ROOT/'metadata.json').read_text()); a=m['artifact']; r=m['run']
    assert r['id']==RUN and r['head_sha']==VALIDATION and r['event']=='push' and r['path']==WORKFLOW
    assert r['run_attempt']==r['run_number']==1 and r['workflow_id']==WORKFLOW_ID
    assert r['status']=='completed' and r['conclusion']=='success'
    assert m['jobs']['total_count']==1 and len(m['jobs']['jobs'])==1
    j=m['jobs']['jobs'][0]
    assert j['id']==JOB and j['run_id']==RUN and j['run_attempt']==1 and j['name']=='diagnostic'
    assert j['status']=='completed' and j['conclusion']=='success'
    required=['Bind accepted production and exact four added diagnostic paths','Compile unchanged production and new diagnostic tests','Execute exactly five novel source-history diagnostic cases','Rebind unchanged accepted source after diagnostic','Preserve original source logs reports and manifest','Run actions/upload-artifact@v4']
    for name in required:
        hits=[s for s in j['steps'] if s['name']==name]
        assert len(hits)==1 and hits[0]['conclusion']=='success'
    assert m['main']['object']['sha']=='37a603617a03dfd2d81b39dae004f7f336a70dfc'
    for key,commit,tree,parent in [('source_commit',SOURCE,TREE,BASE),('validation_commit',VALIDATION,VTREE,SOURCE)]:
        c=m[key]; assert c['sha']==commit and c['tree']['sha']==tree and [p['sha'] for p in c['parents']]==[parent]
    assert m['source_delta']['base_commit']['sha']==BASE
    delta=m['source_delta']['files']; assert len(delta)==4 and {f['filename'] for f in delta}==set(BLOBS)
    assert all(f['status']=='added' and f['sha']==BLOBS[f['filename']] for f in delta)
    vd=m['validation_delta']['files'];assert len(vd)==1 and vd[0]['filename']==WORKFLOW and vd[0]['status']=='added' and vd[0]['sha']==WBLOB
    assert a['workflow_run']['id']==RUN and a['workflow_run']['head_sha']==VALIDATION
    assert a['name']=='manual-source-history-diagnostic-r2-'+str(RUN) and not a['expired']
    archive=ROOT/('original-'+str(a['id'])+'.zip'); data=archive.read_bytes()
    assert len(data)==a['size_in_bytes'] and 'sha256:'+sha(data)==a['digest']
    with zipfile.ZipFile(archive) as z:
        names=z.namelist();assert len(names)==len(set(names)) and z.testzip() is None
        assert all(not Path(n).is_absolute() and '..' not in Path(n).parts for n in names)
        raw={n:z.read(n) for n in names if not n.endswith('/')}
    manifest=json.loads(raw['MANIFEST.json']);assert set(manifest)==set(raw)-{'MANIFEST.json'}
    for path,spec in manifest.items():assert spec==dict(bytes=len(raw[path]),sha256=sha(raw[path])),path
    for path,digest in BLOBS.items():assert blob(raw['source/'+path])==digest,path
    assert blob(raw['validation-workflow.yml'])==WBLOB
    binding=json.loads(raw['BINDING.json'])
    assert binding['source']==SOURCE and binding['tree']==TREE and binding['validation']==VALIDATION and binding['base']==BASE
    assert int(binding['run'])==RUN and binding['attempt']==1 and binding['job']=='diagnostic'
    assert binding['protected_bindings']==143 and binding['accepted_resolver_unchanged'] and binding['goldens']==23 and binding['fixtures']==82
    assert binding['delta']==[['A',f['filename']] for f in delta]
    assert not raw['tracked-status.txt'].strip()
    # The complete change is four added files. Reconstruct every added blob from the archived patch.
    chunks=raw['source-delta.patch'].decode().split('diff --git ')[1:];assert len(chunks)==4
    reconstructed={}
    for chunk in chunks:
        lines=chunk.splitlines();match=re.fullmatch(r'a/(\S+) b/\1',lines[0]);assert match
        path=match[1];assert path in BLOBS and 'new file mode 100644' in lines
        hunk=next(i for i,line in enumerate(lines) if line.startswith('@@'))
        assert all(line.startswith('+') for line in lines[hunk+1:])
        body=('\n'.join(line[1:] for line in lines[hunk+1:])+'\n').encode()
        assert blob(body)==BLOBS[path] and body==raw['source/'+path];reconstructed[path]=True
    assert set(reconstructed)==set(BLOBS)
    reports=[n for n in raw if n.startswith('original-junit/') and n.endswith('.xml')];assert len(reports)==1
    assert raw[reports[0]]==raw['junit/'+reports[0].removeprefix('original-junit/')]
    state,failures=classify(raw[reports[0]],raw['compile-exit.txt'].decode(),raw['test-exit.txt'].decode(),raw['compile.log'].decode(),raw['diagnostic.log'].decode())
    recorded=json.loads(raw['DIAGNOSTIC_RESULT.json']);assert recorded['state']==state and recorded['cases']==5 and recorded['failures']==failures and recorded['errors']==recorded['skips']==0
    assert recorded['source_unchanged'] and not recorded['implementation_acceptance']
    result=dict(state=state,source=SOURCE,tree=TREE,base=BASE,validation=VALIDATION,run=RUN,job=JOB,attempt=1,artifact=a,verified_manifest_entries=len(manifest),cases=5,passes=5-failures,assertion_failures=failures,frames=[81,85] if failures else [],errors=0,skips=0,production_unchanged=True,protected_bindings=143,accepted_resolver_unchanged=True,goldens=23,fixtures=82,accepted_resolver_receipt_reused='7ee1fe7e3848146b6089a73e5fa5951d81e3a630',implementation_acceptance=False,official_games=0,official_seeds=0,precision_reserved=2)
    (ROOT/'VERIFIED_DIAGNOSTIC.json').write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps(result,indent=2))

if __name__=='__main__':receive()
