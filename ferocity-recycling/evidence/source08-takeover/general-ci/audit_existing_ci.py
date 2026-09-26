"""Read the completed CI's original artifacts and decoded logs; never execute tests."""
from pathlib import Path
import collections
import datetime as dt
import hashlib
import json
import re
import xml.etree.ElementTree as ET
import zipfile

ROOT=Path(__file__).parent
def sha(b): return hashlib.sha256(b).hexdigest()
def instant(s):return dt.datetime.fromisoformat(s.replace('Z','+00:00'))
metadata=json.loads((ROOT/'metadata.json').read_text())
merge=json.loads((ROOT/'merge-commit.json').read_text())
run=metadata['run'];jobs={x['name']:x for x in metadata['jobs']['jobs']}
assert run['id']==36268324392 and run['run_attempt']==1 and run['event']=='pull_request' and run['conclusion']=='success'
assert run['head_sha']=='3a4f99a7653839506e96d19e6639f58d9e8c5ced'
assert merge['sha']=='9cdbee73b8b024d4d1eaf71abd5ac790d6a44913'
assert merge['commit']['tree']['sha']=='56c6b8dd46dc112cdb70db496fd9d0c3e915e8a6'
assert [x['sha'] for x in merge['parents']]==['7052de0b64d5d01c3a31465d19157d95089b7d40',run['head_sha']]
all_counts=collections.Counter();module_rows={};xml_rows=[];skipped=[];cases_by_class={};members={};logs={};markers=[]
for artifact in metadata['artifacts']['artifacts']:
    group=artifact['name'].removeprefix('ci-junit-');job=jobs[f'test ({group})']
    logpath=ROOT/f"decoded-job-{job['id']}.log"; log=logpath.read_text()
    assert f"HEAD is now at 9cdbee73 Merge {run['head_sha']} into 7052de0b64d5d01c3a31465d19157d95089b7d40" in log
    assert 'BUILD SUCCESSFUL in' in log and job['conclusion']=='success'
    logs[str(job['id'])]={'bytes':len(log.encode()),'sha256':sha(log.encode()),'group':group}
    path=ROOT/f"original-artifact-{artifact['id']}.zip";raw=path.read_bytes()
    assert len(raw)==artifact['size_in_bytes'] and 'sha256:'+sha(raw)==artifact['digest']
    with zipfile.ZipFile(path) as z:
        assert z.testzip() is None and len(z.namelist())==len(set(z.namelist()))
        archive_members={}
        for name in z.namelist():
            b=z.read(name);archive_members[name]={'bytes':len(b),'sha256':sha(b)}
            assert name.endswith('.xml')
            suite=ET.fromstring(b);cases=suite.findall('testcase');counts={k:int(suite.attrib.get(k,0)) for k in ['tests','failures','errors','skipped']}
            assert counts['tests']==len(cases)
            assert counts['failures']==sum(c.find('failure') is not None for c in cases)==0
            assert counts['errors']==sum(c.find('error') is not None for c in cases)==0
            assert counts['skipped']==sum(c.find('skipped') is not None for c in cases)
            module=name.split('/build/')[0];task=':'+module.replace('/',':')+':test'
            tasklines=re.findall(r'(?m)^\S+ > Task '+re.escape(task)+r'(?: (.*))?$',log)
            assert tasklines,task
            state='FROM-CACHE' if 'FROM-CACHE' in tasklines else 'FRESH'
            assert all(x in ['', 'FROM-CACHE'] for x in tasklines),(task,tasklines)
            assert (module=='mtgish-tooling')==(state=='FROM-CACHE')
            timestamp=instant(suite.attrib['timestamp'])
            if state=='FRESH': assert instant(job['started_at'])<=timestamp<=instant(job['completed_at']),name
            else: assert timestamp<instant(job['started_at']),name
            row=module_rows.setdefault(module,{'task':task,'job_id':job['id'],'state':state,'counts':collections.Counter(),'xml_files':0})
            row['counts'].update(counts);row['xml_files']+=1;all_counts.update(counts)
            case_names=[c.attrib['name'] for c in cases]
            is_marker=suite.attrib['name']=='Gradle Test Run :mtg-sets:test'
            if is_marker:
                assert name=='mtg-sets/build/test-results/test/TEST-Gradle-Test-Run--mtg-sets-test.xml'
                assert artifact['id']==10915325697 and case_names==['CardImageUriTest']
                assert cases[0].attrib['classname']=='Gradle Test Run :mtg-sets:test'
                markers.append({'artifact_id':artifact['id'],'path':name,'suite':suite.attrib['name'],'reported_case':case_names[0],'xml_sha256':sha(b),'actual_tests':0,'actual_passes':0,'reason':'Gradle reporting marker for default-disabled network spec; neither of its two real test names appears.'})
            assert suite.attrib['name'] not in cases_by_class
            cases_by_class[suite.attrib['name']]={'module':module,'state':state,'cases':case_names,'counts':counts,'xml':name,'artifact_id':artifact['id'],'xml_sha256':sha(b)}
            for c in cases:
                if c.find('skipped') is not None:skipped.append({'class':suite.attrib['name'],'case':c.attrib['name'],'module':module,'state':state})
            xml_rows.append({'artifact_id':artifact['id'],'path':name,'class':suite.attrib['name'],'counts':counts,'timestamp':suite.attrib['timestamp'],'state':state,'record_kind':'NON_TEST_REPORTING_MARKER' if is_marker else 'TEST_CASES','sha256':sha(b)})
        members[str(artifact['id'])]={'bytes':len(raw),'sha256':sha(raw),'members':archive_members}
source=json.loads((ROOT.parent/'priority08/exact-git-source-inputs.json').read_text())
token=json.loads(source['lab-coordinator/shared-capabilities/attacking-token-defender-budget.json']['content'])
entry=json.loads(source['lab-coordinator/shared-capabilities/entry-choice-event-provenance-budget.json']['content'])
banks=[]
for bank in token['banks']+[entry['bank']]:
    actual=cases_by_class[bank['class']]
    assert set(actual['cases'])==set(bank['cases']) and len(actual['cases'])==len(bank['cases'])
    assert actual['counts']['failures']==actual['counts']['errors']==actual['counts']['skipped']==0 and actual['state']=='FRESH'
    assert sha(source[bank['path']]['content'].encode())==bank['sha256']
    banks.append({**actual,'class':bank['class'],'source_path':bank['path'],'source_sha256':bank['sha256']})
assert sum(len(b['cases']) for b in banks)==20
assert len(xml_rows)==4599 and all_counts=={'tests':17710,'failures':0,'errors':0,'skipped':52}
assert len(module_rows)==19
assert len(markers)==1
marker_source=json.loads((ROOT/'non-test-marker-source.json').read_text())
marker_path='mtg-sets/src/test/kotlin/com/wingedsheep/mtg/sets/CardImageUriTest.kt'
marker_bytes=marker_source[marker_path]['content'].encode()
assert hashlib.sha1(f'blob {len(marker_bytes)}\0'.encode()+marker_bytes).hexdigest()==marker_source[marker_path]['sha']
assert '@EnabledIf(VerifyImageUrisCondition::class)' in marker_bytes.decode()
assert 'System.getProperty("verifyImageUris") == "true"' in marker_bytes.decode()
assert 'verifyImageUris=true' not in (ROOT/'decoded-job-108477324940.log').read_text()
frontend=jobs['frontend'];flog=(ROOT/f"decoded-job-{frontend['id']}.log").read_text()
assert frontend['conclusion']=='success' and all(s['conclusion']=='success' for s in frontend['steps'])
assert 'HEAD is now at 9cdbee73 Merge '+run['head_sha'] in flog
assert 'npm run typecheck' in flog and 'npm run build' in flog
report={'schema':'ferocity-source08-existing-general-ci-raw-audit-v1','status':'RAW_EVIDENCE_AUDITED_PENDING_INDEPENDENT_DISPOSITION','run_id':run['id'],'attempt':1,'event':run['event'],'source_head':run['head_sha'],'tested_merge_commit':merge['sha'],'tested_tree':merge['commit']['tree']['sha'],'merge_tree_equals_source08_tree':True,'total_xml_files':len(xml_rows),'total_case_counts':dict(all_counts),'fresh_modules':18,'cached_modules':['mtgish-tooling'],'raw_fresh_reported_records':17563,'actual_test_case_records':17709,'non_test_reporting_markers':markers,'non_test_marker_source':{'path':marker_path,'sha256':sha(marker_bytes),'git_blob_sha1':marker_source[marker_path]['sha']},'fresh_cases_including_skips':17562,'fresh_passing_cases':17513,'fresh_skipped_cases':49,'cached_cases_including_skips':147,'cached_reported_passes':144,'cached_skipped_cases':3,'modules':module_rows,'xml':xml_rows,'skipped_cases':skipped,'original_artifacts':members,'decoded_job_logs':logs,'required_receiving_token_banks':banks,'token_original16_cases':16,'additional_entry_cases':4,'no_double_counting':'The receiving20 are a subset of general CI. Original16 and separate4 remain distinct; neither is added to the dedicated508 count. Donor results are not receiving results.','frontend_job_id':frontend['id'],'coverage':'Skipped by explicit main-only non-gating workflow condition on this PR event.','limitations':['mtgish-tooling147 cases are cached results, not fresh execution.','52 skipped cases are retained and never counted as passes.','Full CI tests exact source08 tree via synthetic PR merge; this does not establish binary equivalence with the dedicated gym runtime.','General CI does not emit independent post-run source maps; source/tree binding is checkout+immutable Git tree and separately retained pins.','No complete runtime, resource, export, pilot or gameplay admission is supplied by this author audit.'],'new_test_executions':0,'new_jvms':0,'new_resource_commands':0,'new_games':0,'new_entropy':0}
(ROOT/'raw-ci-audit.json').write_text(json.dumps(report,indent=2,sort_keys=True)+'\n')
print(json.dumps({'xml':len(xml_rows),'counts':dict(all_counts),'fresh_passes':17513,'actual_test_case_records':17709,'non_test_markers':1,'cached_reported_passes':144,'receiving_token_original16':16,'receiving_entry4':4}))
