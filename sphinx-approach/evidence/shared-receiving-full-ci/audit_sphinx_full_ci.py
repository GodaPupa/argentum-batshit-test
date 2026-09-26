import json, hashlib, zipfile, datetime, gzip, re
from pathlib import Path
from collections import Counter
import xml.etree.ElementTree as ET

R=Path('izzet-takeover')
def sha(b):return hashlib.sha256(b).hexdigest()
def blob(b):return hashlib.sha1(b'blob '+str(len(b)).encode()+b'\0'+b).hexdigest()
live={x['kind']:x['data'] for x in json.loads((R/'sphinx-full-ci-independent-live.json').read_text())}
inv=json.loads(Path('sphinx-audit/full-ci-evidence/live-artifact-inventory.json').read_text())
assert live['run']['id']==36277074660 and live['run']['run_attempt']==1 and live['run']['event']=='push'
assert live['run']['head_sha']=='c13b3c84cb30850beca23620b2de53d7a35f7391' and live['run']['conclusion']=='success'
assert len(live['jobs']['jobs'])==9 and all(j['conclusion']=='success' for j in live['jobs']['jobs'])
assert len(live['artifacts']['artifacts'])==8 and len(inv['downloads'])==8
assert live['control_commit']['tree']['sha']=='97785f31fe68efdd50769c3c153c958ba7e3f3c8'
assert live['source_commit']['sha']=='a0c5b995c2829c0b4069562b1f1eede8a820dd97' and live['source_commit']['tree']['sha']=='1ee5590bacf2f6507fff6487be9d3e20df8b5e40'
ct={x['path']:x['sha'] for x in live['control_tree']['tree'] if x['type']=='blob'}
assert not live['control_tree']['truncated'] and len(ct)==3
la={a['id']:a for a in live['artifacts']['artifacts']};seen_groups=set();seen_modules=set();all_names=Counter();skips=[];rows=[];all_xml=0;all_cases=0;reporting_markers=[]
required999=json.loads(Path('sphinx-audit/receiving-gate/receiving-gate.json').read_text())
required={(b['class'],n) for b in required999['banks'] for n in b['case_names']};assert len(required)==999
qualified999=Counter()
for download in inv['downloads']:
    aid=download['artifact']['id'];meta=la[aid];p=Path(download['result']['path']);raw=p.read_bytes();z=zipfile.ZipFile(p)
    assert len(raw)==meta['size_in_bytes'] and 'sha256:'+sha(raw)==meta['digest']
    assert z.testzip() is None and len(z.namelist())==len(set(z.namelist()))
    assert meta['workflow_run']['head_sha']==live['run']['head_sha']
    members={n:{'bytes':len(z.read(n)),'sha256':sha(z.read(n))} for n in z.namelist()}
    a=json.loads(z.read('audit.json'));g=json.loads(z.read('control/full-ci-gate.json'));group=a['group'];assert group not in seen_groups;seen_groups.add(group)
    assert a['run_id']=='36277074660' and a['attempt']=='1' and a['control_head']==live['run']['head_sha']
    assert a['status']=='PASS_PENDING_RAW_REVIEW' and not a['errors']
    assert a['source_before']==a['source_after']=={'head':live['source_commit']['sha'],'tree':live['source_commit']['tree']['sha'],'status':''}
    assert a['official_seeds']==a['official_games']==0 and a['gameplay_authorized'] is False
    for name,expected in ct.items():
        b=z.read('control/'+name);assert blob(b)==expected and sha(b)==a['control_files_sha256'][name]
    assert sha(z.read('control/sphinx-full-ci.py'))=='f1275c8d9348a8455d3ec3eabcefec10b691177aef66997064ab79cec21a40fe'
    assert sha(z.read('control/.github/workflows/sphinx-full-ci.yml'))=='ac91983128ff628572f5268eae80731428119e276c45f030f892e77085b3d34f'
    assert sha(z.read('control/full-ci-gate.json'))=='3798bd2451a4d4ebd4331e0b49c732eba8eca226383599ac89a27ec0ccb8bf2e'
    assert g['ready_for_execution'] is True and g['source_review']['sha256']=='64879f03090bfb32c4c6f4be8b6f0a981dc9574cfa181a65b1e5157f623e2b45'
    rec=g['source_review']['record']; rb=rec.encode() if isinstance(rec,str) else (json.dumps(rec,indent=2)+'\n').encode();assert sha(rb)==g['source_review']['sha256']
    u=dict(g);u['ready_for_execution']=False;u['source_review']=None;assert sha((json.dumps(u,indent=2)+'\n').encode())=='00772eebac1748942be29d25ed8b3e8d7bcbde08cc6acbc85bae3244e44c17c6'
    item={'group':group,'artifact_id':aid,'zip_sha256':sha(raw),'zip_bytes':len(raw),'members':members,'modules':[]}
    if group=='frontend':
        assert len(a['commands'])==3
        for label,cmd,row in zip(['npm-ci','typecheck','build'],[['npm','ci'],['npm','run','typecheck'],['npm','run','build']],a['commands']):
            assert row==json.loads(z.read(label+'.json')) and row['command']==cmd and row['cwd']=='source/web-client' and row['exit_status']==0
            assert row['started_ns']<row['finished_ns'] and 'timeout_seconds' not in row
            assert z.read(label+'.log')
        assert 'vite' in z.read('build.log').decode().lower() and 'built in' in z.read('build.log').decode()
        item['frontend_commands']=a['commands'];rows.append(item);continue
    tasks=g['backend_groups'][group];assert len(a['commands'])==1 and [m['task'] for m in a['modules']]==tasks
    row=a['commands'][0];assert row==json.loads(z.read('backend.json')) and row['exit_status']==0 and 'timeout_seconds' not in row
    assert row['command']==['scripts/gradle-locked']+tasks+['--rerun-tasks','--no-build-cache','--info','--stacktrace','--continue','--max-workers=1','-PkotlinCompileParallelism=1','-Pkotlin.compiler.execution.strategy=in-process','-Dorg.gradle.jvmargs=-Xmx4g']
    assert row['started_ns']<row['finished_ns'] and row['finished_ns']-row['started_ns']<1201e9
    lines=z.read('backend.log').decode(errors='replace').splitlines();assert any('BUILD SUCCESSFUL' in l for l in lines)
    starts=[l for l in lines if 'Gradle Test Executor' in l and 'started executing tests' in l];ends=[l for l in lines if 'Gradle Test Executor' in l and 'finished executing tests' in l]
    assert len(starts)>=len(tasks) and len(ends)>=len(tasks)
    for m in a['modules']:
        task=m['task'];module=m['module'];assert module not in seen_modules;seen_modules.add(module)
        assert task==':'+module.replace('/',':')+':test'
        assert any(l.strip()=='> Task '+task for l in lines)
        assert not any(l.strip()=='> Task '+task+' '+s for l in lines for s in ['FROM-CACHE','UP-TO-DATE','NO-SOURCE','SKIPPED','FAILED'])
        names=sorted(n for n in z.namelist() if n.startswith('xml/'+module+'/') and n.endswith('.xml'))
        assert len(names)==m['xml_count']==len(m['suites']) and names
        by_file={s['file']:s for s in m['suites']};assert set(by_file)==set(names)
        suite_rows=[];module_cases=module_skips=0
        for n in names:
            b=z.read(n);x=ET.fromstring(b);s=by_file[n];tc=x.findall('testcase');assert x.tag=='testsuite'
            assert len(tc)==int(x.attrib['tests'])==s['cases'] and x.attrib['name']==s['class'] and sha(b)==s['sha256']
            assert not x.findall('.//failure') and not x.findall('.//error') and int(x.attrib.get('failures',0))==int(x.attrib.get('errors',0))==0
            skipped=x.findall('.//skipped');assert len(skipped)==int(x.attrib.get('skipped',0))==s['skipped']
            assert [t.attrib.get('name') for t in tc]==s['case_names']
            ts=datetime.datetime.fromisoformat(x.attrib['timestamp']).replace(tzinfo=datetime.timezone.utc).timestamp()*1e9
            assert row['started_ns']-2e9<=ts<=row['finished_ns']+2e9
            for t in tc:
                key=(t.attrib.get('classname'),t.attrib.get('name'));all_names[(module,*key)]+=1
                if key[0].startswith('Gradle Test Run'):
                    assert module=='mtg-sets' and key==('Gradle Test Run :mtg-sets:test','CardImageUriTest') and t.attrib['time']=='0.0' and len(t)==0
                    reporting_markers.append({'module':module,'class':key[0],'name':key[1],'xml':n,'xml_sha256':sha(b),'counts_as_actual_case':False,'counts_as_pass':False,'counts_as_skip':False})
                if t.find('skipped') is not None:skips.append({'module':module,'class':key[0],'name':key[1],'xml':n,'reason':t.find('skipped').attrib,'text':t.find('skipped').text})
                elif key in required:qualified999[key]+=1
            module_cases+=len(tc);module_skips+=len(skipped);all_xml+=1;all_cases+=len(tc)
            suite_rows.append({'class':x.attrib['name'],'cases':len(tc),'skipped':len(skipped),'xml_sha256':sha(b)})
        assert module_cases>0
        item['modules'].append({'task':task,'module':module,'xml_suites':len(names),'case_entries':module_cases,'skipped':module_skips,'fresh_task':True,'suites':suite_rows})
    item['fresh_executor_starts']=len(starts);item['fresh_executor_finishes']=len(ends);rows.append(item)
assert len(seen_groups)==8 and len(seen_modules)==19
assert set(qualified999)==required and all(n==1 for n in qualified999.values())
(R/'sphinx-full-ci-duplicate-identity-observation.json').write_text(json.dumps({'raw_case_entries':all_cases,'unique_display_identities':len(all_names),'duplicate_display_identities':[{'module':k[0],'class':k[1],'name':k[2],'occurrences':v} for k,v in all_names.items() if v!=1]},indent=2)+'\n')
expected_duplicate_display_names = {('rules-engine', 'com.wingedsheep.engine.mechanics.layers.ConditionalControllerGrantsTest', 'OpponentsCantMakeYouSacrifice'), ('rules-engine', 'com.wingedsheep.engine.mechanics.layers.ConditionalControllerGrantsTest', 'GrantCantLoseGame'), ('rules-engine', 'com.wingedsheep.engine.mechanics.layers.ConditionalControllerGrantsTest', 'GrantCantLoseGameFromLife'), ('rules-engine', 'com.wingedsheep.engine.handlers.CostHandlerPayLifeTest', 'life < cost is NOT payable'), ('rules-engine', 'com.wingedsheep.engine.mechanics.layers.ConditionalControllerGrantsTest', 'GrantHexproofToController'), ('rules-engine', 'com.wingedsheep.engine.mechanics.layers.ConditionalControllerGrantsTest', 'GrantOpponentsCantWinGame'), ('rules-engine', 'com.wingedsheep.engine.handlers.CostHandlerPayLifeTest', 'life > cost is payable'), ('rules-engine', 'com.wingedsheep.engine.mechanics.layers.ConditionalControllerGrantsTest', 'CantBeTargetedByOpponentAbilities'), ('rules-engine', 'com.wingedsheep.engine.mechanics.layers.ConditionalControllerGrantsTest', 'StationUsingToughness'), ('rules-engine', 'com.wingedsheep.engine.mechanics.layers.ConditionalControllerGrantsTest', 'GrantShroudToController'), ('rules-engine', 'com.wingedsheep.engine.handlers.CostHandlerPayLifeTest', 'life == cost is payable (the boundary case)')}
assert {k for k,v in all_names.items() if v!=1}==expected_duplicate_display_names
assert all(v==2 for k,v in all_names.items() if k in expected_duplicate_display_names)

frontlog=json.loads((R/'sphinx-full-ci-frontend-decoded-log.json').read_text())['content'];assert 'node: v22.23.2' in frontlog and 'npm: 10.9.8' in frontlog
assert len(reporting_markers)==1
marker_source=json.loads((R/'sphinx-full-ci-reporting-marker-source.json').read_text());mb=marker_source['content'].encode();assert blob(mb)==marker_source['sha']=='6ea078f9fb1eb8004795a685acdcb7e520105e69'
assert 'System.getProperty("verifyImageUris") == "true"' in marker_source['content'] and '@EnabledIf(VerifyImageUrisCondition::class)' in marker_source['content']
assert not any('verifyImageUris=true' in str(g) for g in rows)
result={'schema':'sphinx-full-ci-independent-raw-audit-v2','run':36277074660,'attempt':1,'control_head':live['run']['head_sha'],'control_tree':live['control_commit']['tree']['sha'],'source_head':live['source_commit']['sha'],'source_tree':live['source_commit']['tree']['sha'],'groups':rows,'backend_modules':19,'xml_suites':all_xml,'case_entries':all_cases,'raw_case_records':all_cases,'actual_case_records':all_cases-len(reporting_markers),'actual_passed_records':all_cases-len(reporting_markers)-len(skips),'reporting_markers':reporting_markers,'unique_module_class_name_display_identities':len(all_names),'duplicate_display_name_explanation':'11 source-backed labels each occur under two distinct existing contexts; no additional workflow execution. Do not call raw entry total distinct display identities.','failed':0,'errored':0,'skipped':len(skips),'skip_inventory':skips,'all_required999_executed_once_without_skips':True,'frontend_node_version_observed':'22.23.2','frontend_npm_version_observed':'10.9.8','frontend_decoded_job_log_sha256':sha(frontlog.encode()),'official_games':0}
(R/'sphinx-full-ci-independent-raw-audit.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps({k:v for k,v in result.items() if k not in ['groups','skip_inventory']}))
