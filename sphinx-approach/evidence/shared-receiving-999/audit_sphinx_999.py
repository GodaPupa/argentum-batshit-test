import json, hashlib, zipfile, gzip, datetime
from pathlib import Path
from collections import Counter
import xml.etree.ElementTree as ET

R = Path('izzet-takeover')
P = Path('attachments/1497c0e2-c3ab-4d6e-971f-9d6db541d101/sphinx-receiving-999-original-10916917146.zip')
def sha(b): return hashlib.sha256(b).hexdigest()
def gitblob(b): return hashlib.sha1(b'blob '+str(len(b)).encode()+b'\0'+b).hexdigest()
live = {x['kind']:x['data'] for x in json.loads((R/'sphinx-999-independent-live-metadata.json').read_text())}
raw=P.read_bytes(); z=zipfile.ZipFile(P)
assert len(raw)==995625 and sha(raw)=='1894feb1549e9f9a5ede3ff710635772e5f392b7c4a93b8bcdf9a1019ab6da1c'
assert len(z.namelist())==len(set(z.namelist()))==281 and z.testzip() is None
members={n:{'bytes':len(z.read(n)),'sha256':sha(z.read(n))} for n in z.namelist()}
assert live['run']['id']==36273602269 and live['run']['run_attempt']==1 and live['run']['event']=='push'
assert live['run']['head_sha']=='5b92b8f28b4023420bd510ec354ceef6d763d99d'
assert live['run']['status']=='completed' and live['run']['conclusion']=='success'
assert len(live['jobs']['jobs'])==1 and live['jobs']['jobs'][0]['id']==108492016984
assert live['jobs']['jobs'][0]['conclusion']=='success'
assert len(live['artifacts']['artifacts'])==1
artifact=live['artifacts']['artifacts'][0]
assert artifact['id']==10916917146 and artifact['digest']=='sha256:'+sha(raw) and artifact['size_in_bytes']==len(raw)
assert live['source_commit']['sha']=='a0c5b995c2829c0b4069562b1f1eede8a820dd97'
assert live['source_commit']['tree']['sha']=='1ee5590bacf2f6507fff6487be9d3e20df8b5e40'
assert live['control_commit']['tree']['sha']=='83e376ca1911dca2388a52aa9fe11ef781fca4ea'
ct={x['path']:x['sha'] for x in live['control_tree']['tree'] if x['type']=='blob'}
assert not live['control_tree']['truncated'] and len(ct)==3
audit=json.loads(z.read('audit.json')); gate=json.loads(z.read('control/receiving-gate.json'))
source=json.loads(z.read('source-before.json'))
assert source==json.loads(z.read('source-after.json'))==audit['source_before']==audit['source_after']
assert source['head']==audit['source_head']==live['source_commit']['sha'] and source['tree']==live['source_commit']['tree']['sha'] and not source['status']
assert audit['status']=='PASS_REQUIRES_INDEPENDENT_ARTIFACT_REVIEW' and not audit['errors']
assert audit['control_head']==live['run']['head_sha'] and audit['run_id']=='36273602269' and audit['attempt']=='1'
controls={k:z.read('control/'+k) for k in ['receiving-gate.json','sphinx-receiving-qualification.py']}
workflow_path='.github/workflows/sphinx-shared-receiving.yml'
assert 'control/'+workflow_path not in z.namelist()
controls[workflow_path]=live['workflow']['content'].encode()
for p,b in controls.items():
    assert gitblob(b)==ct[p]
    assert sha(b)==audit['control_files_sha256'][p]
assert sha(controls['sphinx-receiving-qualification.py'])=='6c775a9e62f7e49223ac2a3d4a41209ea2fdcbf3f07301d6cf64f84379c18aab'
assert sha(controls[workflow_path])=='4e7faa85714e5ca9d0ef6dfbcaeb6f390279b528bdd60ca207a46d5097add06e'
assert sha(controls['receiving-gate.json'])=='ffb1f5380c9b9b2c2f3e8bc0caf53997badc6e5b58fa74dfeb7da9ad410e699a'
review=gate['source_review']['original_review_json'].encode()
assert sha(review)==gate['source_review']['original_review_sha256']=='68449f09443a15107c70e7c3b9d3fc152539f592b1d69225df00cdec2dd1b7c0'
unready=dict(gate);unready['ready_for_execution']=False;unready['source_review']=None
assert sha((json.dumps(unready,indent=2)+'\n').encode())=='7180b10e609c4c6a267edf421de9b26c5059990104834bf372493d97e77450b2'
tree=json.loads(gzip.decompress((R/'sphinx-gate-live-source-tree.json.gz').read_bytes()))
assert not tree['truncated']
source_blobs={x['path']:x['sha'] for x in tree['tree'] if x['type']=='blob'}
assert len(source['test_source_git_blobs'])==69
assert source['test_source_git_blobs']=={b['test_source']:source_blobs[b['test_source']] for b in gate['banks']}
assert source['authority_sha256']==gate['preserved_authority_sha256'] and len(source['authority_sha256'])==11
assert len(audit['stages'])==len(gate['banks'])==69
cases=set(); rows=[]; totals=Counter(); previous_finish=0
for i,(bank,row) in enumerate(zip(gate['banks'],audit['stages']),1):
    prefix=f"{i:02d}-{bank['stage']}/"
    assert row==json.loads(z.read(prefix+'command.json'))
    assert row['class']==bank['class'] and row['module']==bank['module'] and row['expected_cases']==bank['expected_cases']
    assert row['exit_status']==0 and z.read(prefix+'exit-status.txt')==b'0\n'
    assert row['status']=='PASS_PRESERVED_CASE_IDENTITIES'
    assert previous_finish<=row['started_ns']<row['finished_ns'];previous_finish=row['finished_ns']
    assert row['command']==['just','test-class',bank['class'].rsplit('.',1)[1],'--rerun','--no-build-cache','--info','--stacktrace','--max-workers=1','-PkotlinCompileParallelism=1','-Pkotlin.compiler.execution.strategy=in-process','-Dorg.gradle.jvmargs=-Xmx4g']
    xml_path=prefix+'TEST-'+bank['class']+'.xml';xb=z.read(xml_path);x=ET.fromstring(xb)
    assert [n for n in z.namelist() if n.startswith(prefix) and n.endswith('.xml')]==[xml_path]
    assert x.tag=='testsuite' and x.attrib['name']==bank['class']
    names=[t.attrib['name'] for t in x.findall('testcase')]
    assert len(names)==len(set(names))==int(x.attrib['tests'])==bank['expected_cases']
    assert sorted(names)==sorted(bank['case_names'])==sorted(row['actual']['case_names'])
    assert all(t.attrib['classname']==bank['class'] for t in x.findall('testcase'))
    assert not any(int(x.attrib.get(k,0)) for k in ['failures','errors','skipped'])
    assert not any(x.findall('.//'+k) for k in ['failure','error','skipped'])
    assert sha(xb)==row['actual']['xml_sha256']
    ts=datetime.datetime.fromisoformat(x.attrib['timestamp']).replace(tzinfo=datetime.timezone.utc).timestamp()*1e9
    assert row['started_ns']-2e9<=ts<=row['finished_ns']+2e9
    lines=z.read(prefix+'command.log').decode().splitlines(); task=':'+bank['module'].replace('/',':')+':test'
    assert any(l.strip()=='> Task '+task for l in lines)
    assert not any(l.strip()=='> Task '+task+' '+s for l in lines for s in ['FROM-CACHE','UP-TO-DATE','NO-SOURCE','SKIPPED'])
    assert any('Gradle Test Executor' in l and 'started executing tests' in l for l in lines)
    assert any('Gradle Test Executor' in l and 'finished executing tests' in l for l in lines)
    assert any('BUILD SUCCESSFUL' in l for l in lines)
    for n in names:
        key=(bank['class'],n);assert key not in cases;cases.add(key)
    totals[bank['scope']]+=len(names)
    rows.append({'stage':bank['stage'],'class':bank['class'],'cases':len(names),'xml_sha256':sha(xb),'log_sha256':sha(z.read(prefix+'command.log')),'exit':0,'fresh_task':True,'fresh_executor':True})
assert len(cases)==999
assert all(audit[k]==0 for k in ['new_pilot_cases','official_games','official_seeds'])
assert audit['gameplay_authorized'] is False and audit['full_runtime_accepted'] is False
(R/'sphinx-999-source-recovered-workflow.yml').write_bytes(controls[workflow_path])
result={'schema':'sphinx999-independent-raw-audit-v1','zip_sha256':sha(raw),'zip_bytes':len(raw),'members':members,'stages':rows,'classes':len(rows),'cases':len(cases),'by_scope':dict(totals),'failures':0,'errors':0,'skips':0,'source_head':source['head'],'source_tree':source['tree'],'control_head':audit['control_head'],'control_tree':live['control_commit']['tree']['sha'],'source_before_equals_after':True,'run_id':36273602269,'attempt':1,'artifact_id':10916917146,'artifact_workflow_omission':'OriginalZIP omits hidden control/.github workflow. Exact workflow separately recovered from immutable control Git commit; not claimed to be an original ZIP member.','source_recovered_workflow_sha256':sha(controls[workflow_path]),'limits':['No full CI or full runtime/pilot/gameplay admission.','Original case inventories and11 authority source bindings were independently checked in prior immutable-source review68449f09; this audit checks actual receiving raw results and unchanged source bindings.']}
(R/'sphinx-999-independent-raw-audit.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps({'classes':len(rows),'cases':len(cases),'by_scope':dict(totals),'zip_members':len(members),'raw_audit_sha256':sha((R/'sphinx-999-independent-raw-audit.json').read_bytes())}))
