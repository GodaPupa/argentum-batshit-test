"""Run once on the original GitHub artifact after live successor reconciliation."""
from pathlib import Path
import argparse,hashlib,importlib.util,io,json,zipfile
def receive(raw,pins):
    assert len(raw)==pins['bytes']
    assert hashlib.sha256(raw).hexdigest()==pins['sha256']
    with zipfile.ZipFile(io.BytesIO(raw)) as archive:
        names=archive.namelist();assert len(names)==len(set(names))
        assert archive.testzip() is None
        assert all(not n.startswith('/') and '..' not in Path(n).parts for n in names)
        files={n:archive.read(n) for n in names if not n.endswith('/')}
    manifest=json.loads(files['MANIFEST.json'])
    assert set(manifest)==set(files)-{'MANIFEST.json'}
    for name,digest in manifest.items():assert hashlib.sha256(files[name]).hexdigest()==digest,name
    assert 'FAILURE.txt' not in files
    binding=json.loads(files['BINDING.json'])
    for key in ['source','tree','validation','validation_tree','workflow','workflow_blob','run','attempt','run_number']:
        assert str(binding[key])==str(pins[key]),key
    assert binding['attempt']==1 and binding['run_number']==1
    workflow=files['WORKFLOW.yml']
    assert hashlib.sha1(b'blob '+str(len(workflow)).encode()+b'\0'+workflow).hexdigest()==pins['workflow_blob']
    assert json.loads(files['RESULT.json'])==dict(state='NORMAL_COMPILE_AND_SNAPSHOT_CANDIDATE_ONLY',feature_admitted=False,card_admitted=False)
    here=Path(__file__).resolve().parent
    assert files['protocol/RECEIVE.py']==Path(__file__).read_bytes()
    assert files['protocol/audit.py']==(here/'audit.py').read_bytes()
    spec=importlib.util.spec_from_file_location('diagnostic_audit',here/'audit.py');module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
    protocol=json.loads(files['protocol/PROTOCOL.json'])
    summary=module.verify(files,protocol)
    assert summary==json.loads(files['AUDIT.json'])
    return dict(state='ORIGINAL_NORMAL_COMPILE_VERIFIED_BEHAVIOR_PENDING',pins=pins,manifest_entries=len(manifest),summary=summary,feature_admitted=False,card_admitted=False)
if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('archive');parser.add_argument('pins');parser.add_argument('output');args=parser.parse_args()
    result=receive(Path(args.archive).read_bytes(),json.loads(Path(args.pins).read_text()))
    Path(args.output).write_text(json.dumps(result,indent=2)+'\n')
