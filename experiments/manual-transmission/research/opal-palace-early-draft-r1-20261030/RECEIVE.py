"""Prepared original-artifact receiver. Final pins and immutable protocol are mandatory."""
from pathlib import Path
import hashlib,io,json,re,zipfile
from audit import verify
from mutants import forms
sha=lambda b:hashlib.sha256(b).hexdigest()
blob=lambda b:hashlib.sha1(b'blob '+str(len(b)).encode()+b'\0'+b).hexdigest()
def receive(archive_bytes,pins):
    assert len(archive_bytes)==pins['bytes'] and sha(archive_bytes)==pins['sha256']
    with zipfile.ZipFile(io.BytesIO(archive_bytes)) as archive:
        names=archive.namelist();assert len(names)==len(set(names)) and archive.testzip() is None
        assert all(not n.startswith('/') and '..' not in Path(n).parts for n in names)
        raw={n:archive.read(n) for n in names if not n.endswith('/')}
    manifest=json.loads(raw['MANIFEST.json']);assert set(manifest)==set(raw)-{'MANIFEST.json'}
    for n,digest in manifest.items():assert sha(raw[n])==digest,n
    assert 'FAILURE.txt' not in raw
    binding=json.loads(raw['BINDING.json'])
    for key in ['source','tree','validation','validation_tree','workflow','workflow_blob','run','attempt','run_number']:assert str(binding[key])==str(pins[key]),key
    assert binding['attempt']==binding['run_number']==1
    assert blob(raw['WORKFLOW.yml'])==pins['workflow_blob']
    here=Path(__file__).resolve().parent
    for name in ['RECEIVE.py','audit.py','mutants.py','check_audit.py','run.py','validation.just','EARLY_CONTRACT.json','PROTOCOL.json']:
        assert raw['protocol/'+name]==(here/name).read_bytes(),name
    p=json.loads(raw['protocol/PROTOCOL.json']);c=json.loads(raw['protocol/EARLY_CONTRACT.json'])
    for phase in ['initial','final']:
        assert raw[phase+'/TRACKED.diff']==b''
        for path,digest in p['bindings'].items():assert sha(raw[phase+'/source/'+path])==digest,path
        for path,digest in p['git_bindings'].items():assert blob(raw[phase+'/source/'+path])==digest,path
        for path,digest in p['original_bindings'].items():assert sha(raw[phase+'/original/'+path])==digest,path
        assert set(json.loads(raw[phase+'/UNCHANGED_TREES.json']))==set(p['unchanged_modules'])
    assert raw['initial/UNCHANGED_TREES.json']==raw['final/UNCHANGED_TREES.json']
    normal={path:raw['initial/source/'+path].decode() for path in c['normal_hashes']}
    assert {path:sha(s.encode()) for path,s in normal.items()}==c['normal_hashes']
    mutants=forms(normal)
    assert {key:{path:sha(s.encode()) for path,s in changes.items()} for key,changes in mutants.items()}==c['forms']
    touched=set().union(*(set(changes) for changes in mutants.values()))
    for path in touched:assert raw['forms/normal/'+path]==normal[path].encode()
    for form,changes in mutants.items():
        for path,s in changes.items():assert raw['forms/'+form+'/'+path]==s.encode()
    summaries={}
    stages=[('compile-normal',{},None),('normal',{},'normal'),('compile-restored',{},None),('restored',{},'restored')]
    for form,changes in mutants.items():stages.extend([('compile-'+form,changes,None),('negative-'+form,changes,form)])
    for stage,changes,form in stages:
        expected=dict(p['bindings']);expected.update({path:sha(s.encode()) for path,s in changes.items()})
        for phase in ['before','after']:
            assert json.loads(raw[stage+'/'+phase+'/SOURCE_HASHES.json'])==expected
            diff=raw[stage+'/'+phase+'/SOURCE.diff']
            assert bool(diff)==bool(changes)
        assert raw[stage+'/before/SOURCE.diff']==raw[stage+'/after/SOURCE.diff']
        group={key[len(stage)+1:]:value for key,value in raw.items() if key.startswith(stage+'/')}
        if form is None:
            assert group['exit.txt'].strip()==b'0'
            log=group['output.log'].decode();assert 'BUILD SUCCESSFUL' in log
            assert not re.search(r'^e: |Compilation error|Unresolved reference|^> Task .* FAILED$',log,re.M)
            assert not any(key.startswith('junit/') for key in group)
        else:
            summaries[stage]=verify(group,c,form)
            assert summaries[stage]==json.loads(group['SUMMARY.json'])
    result=json.loads(raw['RESULT.json'])
    assert result==dict(state='EARLY_EXECUTION_COMPLETE_ORIGINAL_RECEIVING_PENDING',feature_admitted=False,card_admitted=False,remaining_mandatory_gates=p['remaining_mandatory_gates'])
    assert len(mutants)==17
    assert summaries['normal']['rows']==summaries['restored']['rows']==56
    negatives=[v for k,v in summaries.items() if k.startswith('negative-')]
    assert sum(len(v['failures']) for v in negatives)==18
    assert sum(v['passing_controls'] for v in negatives)==170
    return dict(state='ORIGINAL_EARLY_VERIFIED_DEPENDENT_GATES_PENDING',pins=pins,manifest_entries=len(manifest),distinct_compiled_forms=18,restored_compile=True,normal=56,restored=56,negative_witnesses=18,negative_controls=170,summaries=summaries,feature_admitted=False,card_admitted=False)
if __name__=='__main__':
    import argparse
    parser=argparse.ArgumentParser();parser.add_argument('archive');parser.add_argument('pins');parser.add_argument('output');args=parser.parse_args()
    value=receive(Path(args.archive).read_bytes(),json.loads(Path(args.pins).read_text()))
    Path(args.output).write_text(json.dumps(value,indent=2)+'\n')
