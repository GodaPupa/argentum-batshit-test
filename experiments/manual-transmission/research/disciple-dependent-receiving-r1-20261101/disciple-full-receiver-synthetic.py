"""Synthetic receiver fault controls only; these are not qualification evidence."""
from pathlib import Path
import importlib.util, json, copy, io, zipfile
spec=importlib.util.spec_from_file_location('receiver',Path(__file__).with_name('disciple-full-receive.py'))
r=importlib.util.module_from_spec(spec);spec.loader.exec_module(r)
passed=[]
def rejected(name, fn):
    try:fn()
    except (AssertionError,KeyError,ValueError):passed.append(name);return
    raise AssertionError('accepted corruption: '+name)

def packed(entries):
    out=io.BytesIO()
    with zipfile.ZipFile(out,'w') as z:
        for name,data in entries:z.writestr(name,data)
    out.seek(0)
    return zipfile.ZipFile(out)

valid=[('raw.txt',b'original'),('MANIFEST.json',json.dumps({'raw.txt':r.sha(b'original')}).encode())]
assert r.manifest(packed(valid))['raw.txt']==b'original'
rejected('altered original bytes',lambda:r.manifest(packed([('raw.txt',b'changed'),valid[1]])))
rejected('missing manifest member',lambda:r.manifest(packed([valid[1]])))
rejected('unlisted raw file',lambda:r.manifest(packed(valid+[('extra',b'x')])))
rejected('duplicate archive identity',lambda:r.manifest(packed(valid+[valid[0]])))
rejected('unsafe extraction path',lambda:r.manifest(packed(valid+[('../escape',b'x')])))

E=r.E;archive=b'synthetic example archive only'
run={'id':E['run'],'head_sha':E['validation'],'head_branch':E['branch'],'path':E['workflow'],'run_attempt':1,'run_number':1,'event':'push','status':'completed','conclusion':'success'}
job={'id':E['job'],'run_id':E['run'],'run_attempt':1,'status':'completed','conclusion':'success'}
api={'run':run,'jobs':{'total_count':1,'jobs':[job]},'artifact':{'name':E['artifact_name'],'expired':False,'workflow_run':{'id':E['run'],'head_sha':E['validation']},'size_in_bytes':len(archive),'digest':'sha256:'+r.sha(archive)},'source_commit':{'sha':E['source'],'tree':{'sha':E['tree']},'parents':[{'sha':r.P['base']}]},'validation_commit':{'sha':E['validation'],'tree':{'sha':E['validation_tree']},'parents':[{'sha':E['source']}]}}
r.api_binding(api,archive)
for name,field,value in [('wrong source head','head_sha','other'),('rerun attempt','run_attempt',2),('wrong event','event','workflow_dispatch'),('nonterminal run','status','in_progress'),('failed run','conclusion','failure')]:
    bad=copy.deepcopy(api);bad['run'][field]=value
    rejected(name,lambda bad=bad:r.api_binding(bad,archive))
bad=copy.deepcopy(api);bad['jobs']['jobs'][0]['id']+=1
rejected('wrong original job',lambda:r.api_binding(bad,archive))
rejected('changed archive hash',lambda:r.api_binding(api,archive+b'x'))

prefix='assay-mh3/'
original=(r.DATA/'MH3.original.json').read_bytes();candidate=(r.DATA/'MH3.candidate.json').read_bytes();corpus=b'synthetic corpus'
files={prefix+'BINDINGS.json':json.dumps({'original_snapshot':r.sha(original),'candidate_snapshot':r.sha(candidate),'corpus':r.sha(corpus),'distribution':{'lib/example.jar':'not-a-runtime-evidence-hash'},'classification_required':True,'admitted':False}).encode(),prefix+'oracle-cards.jsonl.gz':corpus}
for name,content,count in [('original',original,24),('candidate',candidate,25)]:
    files[prefix+name+'/mtg-sets/src/test/resources/snapshots/cards/MH3.json']=content
    files[prefix+name+'/settings.gradle.kts']=b'identical settings'
    files[prefix+name+'/exit.txt']=b'0\n'
    files[prefix+name+'/stdout.log']=f'Hand-written cards {count}\ngolden would not decode 0\n'.encode()
assert r.assay_binding(files)['classification_required']
for name,key,value in [('changed corpus',prefix+'oracle-cards.jsonl.gz',b'other'),('changed protected original',prefix+'original/mtg-sets/src/test/resources/snapshots/cards/MH3.json',b'other'),('wrong candidate population',prefix+'candidate/stdout.log',b'Hand-written cards 24\ngolden would not decode 0\n'),('undecodable candidate',prefix+'candidate/stdout.log',b'Hand-written cards 25\ngolden would not decode 1\n')]:
    bad=dict(files);bad[key]=value
    rejected(name,lambda bad=bad:r.assay_binding(bad))
result={'state':'SYNTHETIC_RECEIVER_CONTROLS_ONLY','rejected':passed,'count':len(passed),'original_artifact_received':False,'qualification_accepted':False}
Path('disciple-full-receiver-synthetic-result.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result))
