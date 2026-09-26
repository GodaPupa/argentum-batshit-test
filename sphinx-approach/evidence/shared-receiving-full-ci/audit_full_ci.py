"""Audit only retained full-CI bytes; never imports or starts an engine."""
import collections, datetime, gzip, hashlib, json, pathlib, re, sys, zipfile
import xml.etree.ElementTree as ET
BASE=pathlib.Path(__file__).resolve().parent
ROOT=pathlib.Path('/workspace/scratch/94f5141e9448')
sha=lambda b:hashlib.sha256(b).hexdigest()
inventory=json.loads((BASE/'live-artifact-inventory.json').read_text())
first=inventory['downloads'][0]
first_path=pathlib.Path(first['result']['path'])
if not first_path.exists():first_path=BASE/f"original-artifact-{first['artifact']['id']}.zip"
with zipfile.ZipFile(first_path)as first_zip:gate=json.loads(first_zip.read('control/full-ci-gate.json'))
control_hashes={'.github/workflows/sphinx-full-ci.yml':'ac91983128ff628572f5268eae80731428119e276c45f030f892e77085b3d34f',
 'sphinx-full-ci.py':'f1275c8d9348a8455d3ec3eabcefec10b691177aef66997064ab79cec21a40fe',
 'full-ci-gate.json':'3798bd2451a4d4ebd4331e0b49c732eba8eca226383599ac89a27ec0ccb8bf2e'}
source={'head':'a0c5b995c2829c0b4069562b1f1eede8a820dd97','tree':'1ee5590bacf2f6507fff6487be9d3e20df8b5e40','status':''}
all_cases=set();skips=[];all_suites=[];groups=[];discovery_markers=[]
for d in inventory['downloads']:
    meta=d['artifact'];p=pathlib.Path(d['result']['path'])
    if not p.exists():p=BASE/f"original-artifact-{meta['id']}.zip"
    raw=p.read_bytes();assert len(raw)==meta['size_in_bytes'] and 'sha256:'+sha(raw)==meta['digest']
    assert meta['workflow_run']['id']==36277074660 and meta['workflow_run']['head_sha']==inventory['control_head']
    with zipfile.ZipFile(p) as z:
        assert z.testzip() is None and len(z.namelist())==len(set(z.namelist()))
        a=json.loads(z.read('audit.json'));group=a['group'];assert a['errors']==[] and a['status']=='PASS_PENDING_RAW_REVIEW'
        assert a['source_before']==a['source_after']==source and a['control_head']==inventory['control_head']
        assert a['run_id']=='36277074660' and a['attempt']=='1'
        assert a['official_seeds']==a['official_games']==0 and not a['gameplay_authorized']
        assert a['control_files_sha256']==control_hashes
        for name,h in control_hashes.items():assert sha(z.read('control/'+name))==h
        assert json.loads(z.read('control/full-ci-gate.json'))==gate
        row={'group':group,'artifact_id':meta['id'],'artifact_bytes':len(raw),'artifact_sha256':sha(raw),'zip_members':len(z.namelist()),'crc':'PASS','commands':[],'modules':[]}
        for cmd in a['commands']:
            assert cmd['exit_status']==0 and 'timeout_seconds' not in cmd
            assert cmd['started_ns']<cmd['finished_ns']
            label='backend' if group!='frontend' else ('npm-ci' if cmd['command']==['npm','ci'] else cmd['command'][-1])
            assert json.loads(z.read(label+'.json'))==cmd
            row['commands'].append({**cmd,'log_sha256':sha(z.read(label+'.log'))})
        if group=='frontend':
            assert [x['command']for x in a['commands']]==[['npm','ci'],['npm','run','typecheck'],['npm','run','build']]
            assert not any(n.endswith('.xml')for n in z.namelist())
            assert 'tsc --noEmit' in z.read('typecheck.log').decode()
            assert 'built in' in z.read('build.log').decode()
        else:
            assert len(a['commands'])==1
            command=a['commands'][0];tasks=gate['backend_groups'][group]
            assert command['command']==['scripts/gradle-locked']+tasks+['--rerun-tasks','--no-build-cache','--info','--stacktrace','--continue','--max-workers=1','-PkotlinCompileParallelism=1','-Pkotlin.compiler.execution.strategy=in-process','-Dorg.gradle.jvmargs=-Xmx4g']
            log=z.read('backend.log').decode();lines=log.splitlines()
            starts=re.findall(r'^Gradle Test Executor (\d+) started executing tests\.$',log,re.M)
            finishes=re.findall(r'^Gradle Test Executor (\d+) finished executing tests\.$',log,re.M)
            assert collections.Counter(starts)==collections.Counter(finishes) and len(starts)==len(tasks)
            assert 'BUILD SUCCESSFUL' in log and 'Using 1 worker leases.' in log
            row['fresh_test_workers']=len(starts)
            row['lock_fallback_observed']="'shlock' not found; running ./gradlew unlocked" in log
            assert [m['task']for m in a['modules']]==tasks
            found=set()
            for module in a['modules']:
                task=module['task'];tasklines=[l for l in lines if re.match('^> Task '+re.escape(task)+r'(?:\s|$)',l)]
                assert tasklines and set(tasklines)=={'> Task '+task}
                assert module['xml_count']==len(module['suites'])>0
                counts=collections.Counter()
                for s in module['suites']:
                    n=s['file'];found.add(n);b=z.read(n);assert sha(b)==s['sha256']
                    x=ET.fromstring(b);cases=x.findall('testcase')
                    assert x.attrib['name']==s['class'] and len(cases)==int(x.attrib['tests'])==s['cases']
                    assert [c.attrib['name']for c in cases]==s['case_names']
                    for tag,key in [('failure','failures'),('error','errors'),('skipped','skipped')]:
                        assert len(x.findall('.//'+tag))==int(x.attrib.get(key,0))==s[key]
                    assert s['failures']==s['errors']==0
                    stamp=datetime.datetime.fromisoformat(x.attrib['timestamp'].replace('Z','+00:00')).replace(tzinfo=datetime.timezone.utc).timestamp()
                    assert command['started_ns']/1e9-2 <= stamp <= command['finished_ns']/1e9+2, (n,stamp,command)
                    counts['suites']+=1;counts['testcase_records']+=len(cases);counts['skipped']+=s['skipped']
                    all_suites.append({'group':group,'module':module['module'],**s,'timestamp':x.attrib['timestamp']})
                    for c in cases:
                        ident=(s['class'],c.attrib['name'])
                        all_cases.add(ident)
                        if ident[0].startswith('Gradle Test Run '):
                            discovery_markers.append({'module':module['module'],'class':ident[0],'name':ident[1],'xml':n,'xml_sha256':sha(b),'time':c.attrib.get('time')})
                        if c.find('skipped')is not None:skips.append({'module':module['module'],'class':ident[0],'case':ident[1]})
                row['modules'].append({'task':task,**counts})
            assert found=={n for n in z.namelist()if n.endswith('.xml')}
        groups.append(row)
assert {r['group']for r in groups}==set(gate['backend_groups'])|{'frontend'}
# The independently admitted 999-case bank must also be represented in the full fresh CI.
original999=ROOT/'attachments/1497c0e2-c3ab-4d6e-971f-9d6db541d101/sphinx-receiving-999-original-10916917146.zip'
comparisons=json.loads((BASE/'prior-identity-comparison-inputs.json').read_text())
qualified={tuple(x)for x in comparisons['qualified999_cases']}
if original999.exists():
    decoded=set()
    with zipfile.ZipFile(original999)as z:
        for n in z.namelist():
            if n.endswith('.xml'):
                x=ET.fromstring(z.read(n));decoded.update((x.attrib['name'],c.attrib['name'])for c in x.findall('testcase'))
    assert qualified==decoded
assert len(qualified)==999 and qualified<=all_cases
# Verify skip identities against prior retained shared source artifacts; no skip is recast as pass.
old_skips=set();old_archives=[]
for p in (ROOT/'shared-audit/publish/lab-coordinator/shared-capabilities/evidence/combined-994383-takeover').glob('ci-*.zip'):
    with zipfile.ZipFile(p) as z:
        for n in z.namelist():
            if n.endswith('.xml'):
                x=ET.fromstring(z.read(n));old_skips.update((x.attrib['name'],c.attrib['name'])for c in x.findall('testcase')if c.find('skipped')is not None)
    old_archives.append({'name':p.name,'sha256':sha(p.read_bytes())})
if old_archives:
    assert len(old_archives)==7 and old_skips=={tuple(s)for s in comparisons['prior_shared_skips']}
else:
    old_archives=comparisons['prior_shared_original_archives']
    old_skips={tuple(s)for s in comparisons['prior_shared_skips']}
assert old_skips=={(s['class'],s['case'])for s in skips} and len(skips)==52
assert len(discovery_markers)==1 and discovery_markers[0]['class']=='Gradle Test Run :mtg-sets:test' and discovery_markers[0]['name']=='CardImageUriTest'
report={'schema':'sphinx-full-ci-author-raw-audit-v1','auditor':'/root/sphinx','auditor_is_control_author':True,'status':'PASS_FRESH_STANDARD_CI_PENDING_INDEPENDENT_RAW_REVIEW',
 'source':source,'control_head':inventory['control_head'],'control_tree':inventory['control_tree'],'run':36277074660,'attempt':1,
 'groups':groups,'total_modules':sum(len(r['modules'])for r in groups),'total_suites':len(all_suites),'testcase_records':sum(s['cases']for s in all_suites),'actual_case_records_excluding_discovery_markers':sum(s['cases']for s in all_suites)-len(discovery_markers),'discovery_markers':discovery_markers,'skipped_testcase_records':len(skips),'passed_testcase_records':sum(s['cases']for s in all_suites)-len(skips)-len(discovery_markers),'failures':0,'errors':0,
 'qualified999_identities_also_observed':len(qualified),'skips':skips,'prior_shared_skip_comparison':{'same52identities':True,'originals':old_archives},
 'limits':['This is author audit, not independent artifact acceptance or GitHub approval.','All original standard PR CI groups executed freshly;52 unchanged preexisting disabled/opt-in cases remained skipped and are not passing tests.','Engine fixtures, including999 bank, are deterministic qualification rather than official games. No experimental seed or outcome claim.','Full CI and999 receiving bank do not establish whole pilot competence, a prospective StageE allocation freeze, replay/runner/durable admission or gameplay permit.','Canonical reconstruction integration remains separately governed.','Canonical gradle-locked route reports absent shlock fallback on isolated one-command runners; no operating shared semaphore is claimed.'],
 'audit_revision':{'prior_author_audit_sha256':'04a94e3c8c8e4c3a84d43c659614c15a010becbf726e1d0e5e1fd86a3acb02eb','prior_record':'author-raw-audit-v1-preserved.json','correction':'Independent review identified one Gradle discovery marker among unflagged XML records. It remains in raw17547 but is excluded from actual-case and pass credit; corrected actual cases17546, passed17494, skips52. Original artifacts and execution are unchanged.'},
 'official_seeds':0,'official_games':0,'gameplay_authorized':False}
packed=gzip.compress((json.dumps(all_suites,separators=(',',':'))+'\n').encode(),mtime=0)
(BASE/'all-suite-observations.json.gz').write_bytes(packed)
report['complete_suite_observations']={'path':'all-suite-observations.json.gz','sha256':sha(packed)}
(BASE/'author-raw-audit.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps({k:report[k]for k in ['total_modules','total_suites','testcase_records','skipped_testcase_records','passed_testcase_records','failures','errors','qualified999_identities_also_observed']}))
print('audit_sha256',sha((BASE/'author-raw-audit.json').read_bytes()))
