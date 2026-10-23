"""Receive the original failed ZIP; compilation failure is not behavioral evidence."""
from pathlib import Path
import hashlib, json, zipfile, io, base64, re

def receive(archive, metadata):
    sha=lambda b: hashlib.sha256(b).hexdigest()
    blob=lambda b: hashlib.sha1(b'blob '+str(len(b)).encode()+b'\0'+b).hexdigest()
    source='3e55adacde36f96636776165744ebc85508c12c5'
    validation='1540fd7f2e1c8bf17a74cd8440bbf2cdb88d5cc7'
    tree='debfcfaad7294a8a774ab1d278efb28212efe134'
    m=metadata; a=m['artifact']; r=m['run']; j=m['jobs']['jobs'][0]
    assert len(archive)==a['size_in_bytes']==21007
    assert sha(archive)=='63a399dc2fa0701ff515a729fc99d2e02f93499ea3c52e887591d7ed17474e23'
    assert a['digest']=='sha256:'+sha(archive) and a['id']==12529366849
    assert a['workflow_run']['id']==r['id']==40017590859
    assert a['workflow_run']['head_sha']==r['head_sha']==validation
    assert r['status']=='completed' and r['conclusion']=='failure'
    assert r['run_attempt']==r['run_number']==j['run_attempt']==1
    assert m['jobs']['total_count']==1 and j['id']==120633551904 and j['conclusion']=='failure'
    assert m['source_commit']['sha']==source and m['source_commit']['tree']['sha']==tree
    delta=m['validation_delta']; assert delta['ahead_by']==1 and len(delta['files'])==1
    wf=delta['files'][0]; assert wf['status']=='added' and wf['filename']=='.github/workflows/manual-retarget-history-r1-20261023.yml'
    z=zipfile.ZipFile(io.BytesIO(archive)); names=z.namelist()
    assert len(names)==len(set(names))==16 and z.testzip() is None
    assert all(not Path(n).is_absolute() and '..' not in Path(n).parts for n in names)
    manifest={}
    for line in z.read('SHA256SUMS').decode().splitlines():
        digest,name=line.split('  ',1);assert name not in manifest and sha(z.read(name))==digest;manifest[name]=digest
    assert set(manifest)==set(names)-{'SHA256SUMS'} and len(manifest)==15
    assert blob(z.read('protocol/WORKFLOW.yml'))==wf['sha']
    b=json.loads(z.read('BINDING.json'));assert (b['source'],b['tree'],b['validation'])==(source,tree,validation)
    assert z.read('SOURCE_PINS.txt').decode().splitlines()==[validation,tree]
    assert not z.read('FINAL_TRACKED_CHANGES.txt')
    s=json.loads(z.read('mutation/SUMMARY.json'))
    assert (s['source'],s['tree'],s['validation'],s['run'])==(source,tree,validation,'40017590859')
    assert s['totals']==dict(rows=0,suites=0,passed=0,failures=0,errors=0,skipped=0)
    assert s['modules']==s['cases']=={} and s['failures']==s['skips']==[]
    assert not any(n.endswith('.xml') for n in names)
    assert int(z.read('mutation/exit.txt'))!=0
    log=z.read('mutation/gradle.log').decode()
    errors=[line for line in log.splitlines() if line.startswith('e: ')]
    coords=[re.search(r'RetargetHistory.kt:(\d+):(\d+) Unresolved reference \'entityId\'\.',line) for line in errors]
    assert len(coords)==5 and all(coords)
    assert [(int(x[1]),int(x[2])) for x in coords]==[(28,86),(29,64),(30,49),(30,86),(33,33)]
    assert '> Task :rules-engine:compileKotlin FAILED' in log and not re.search(r'^> Task :rules-engine:test(?: |$)',log,re.M)
    assert all(st['conclusion']=='skipped' for st in j['steps'] if 10<=st['number']<=16)
    assert next(st for st in j['steps'] if st['number']==8)['conclusion']=='failure'
    assert next(st for st in j['steps'] if st['number']==9)['conclusion']=='success'
    assert next(st for st in j['steps'] if st['number']==17)['conclusion']=='success'
    disposition=json.loads(z.read('DISPOSITION.json'))
    assert disposition['original_reports_present']==dict(mutation=True,focused=False,broad=False,ordinary=False)
    assert not disposition['assay_receipt_present'] and not disposition['component_adopted']
    return dict(disposition='REJECTED_INJECTION_COMPILATION_ZERO_TESTS_NOT_BEHAVIORAL_EVIDENCE',source=source,tree=tree,validation=validation,run=r['id'],job=j['id'],attempt=1,artifact=a['id'],artifact_bytes=len(archive),artifact_sha256=sha(archive),manifest_entries=15,archive_members=16,original_manifest=manifest,compiler_errors=[dict(line=int(x[1]),column=int(x[2]),message="Unresolved reference 'entityId'") for x in coords],tests=0,positive_gates='NOT_EXECUTED',restoration='SUCCESS_CLEAN_TRACKED_STATE',official_games=0,official_seeds=0,precision_reserved=2)

if __name__=='__main__':
    import sys
    root=Path(sys.argv[1])
    result=receive(base64.b64decode((root/'ORIGINAL_ARTIFACT.zip.base64').read_bytes(),validate=True),json.loads((root/'ORIGINAL_METADATA.json').read_text()))
    assert result==json.loads((root/'FAILURE_RECEIPT.json').read_text())
    print(json.dumps({k:result[k] for k in ['disposition','run','artifact','artifact_sha256','manifest_entries','tests']},indent=2))
