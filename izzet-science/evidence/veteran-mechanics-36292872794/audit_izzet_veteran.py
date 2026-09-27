import hashlib, json, sys, xml.etree.ElementTree as ET, zipfile

path,out=sys.argv[1:3]
digest=lambda b: hashlib.sha256(b).hexdigest()
errors=[]
def check(ok,why):
    if not ok:errors.append(why)

with zipfile.ZipFile(path) as z:
    check(z.testzip() is None,'ZIP CRC')
    names=set(z.namelist());a=json.loads(z.read('audit.json'));g=json.loads(z.read('control/gate.json'))
    before=json.loads(z.read('source-before.json'));after=json.loads(z.read('source-after.json'))
    stage=a['stages'][0];bank=g['banks'][0]
    root='01-VeteranBeastriderScenarioTest/'
    xmlpath=root+'TEST-'+bank['class']+'.xml'
    expected={root+n for n in ('command.json','command.log','exit-status.txt')} | {xmlpath,'audit.json','source-before.json','source-after.json'} | {'control/'+n for n in a['control_files_sha256']}
    check(names==expected and len(names)==10,'exact 10 members')
    check((a['run_id'],a['attempt'],a['event'],a['source_head'],a['source_tree'],a['control_head'])==
      ('36292872794','1','push','82b5e4660c28bfb399b09cbaaf92d4e59181bf82','aecacb5c0e520721b8ae263e9e1f3822bb46f783','c841edaa2f836e33a5f0ac881b1bf9c87e5d4df5'),'source/control/run binding')
    check(a['status']=='PASS_REQUIRES_INDEPENDENT_ARTIFACT_REVIEW' and a['errors']==[],'collector status')
    check(before==after==a['source_before']==a['source_after'] and before['head']==a['source_head'] and before['tree']==a['source_tree'],'source snapshots')
    check(before['authority_sha256']==g['preserved_authority_sha256'] and len(before['authority_sha256'])==10,'authority hashes')
    check(bank['test_source_git_blob']==before['test_source_git_blobs'][bank['test_source']],'test blob')
    check(set(a['dependency_files_sha256'])==set(g['dependency_files']) and len(g['dependency_files'])==8,'dependencies')
    for name,h in a['control_files_sha256'].items():check(digest(z.read('control/'+name))==h,'control hash '+name)
    check(json.loads(z.read(root+'command.json'))==stage and z.read(root+'exit-status.txt')==b'0\n','command sidecar/exit')
    check(stage['status']=='PASS_PRESERVED_CASE_IDENTITIES' and stage['exit_status']==0 and stage['output_complete'] and stage['discarded_observed_pipe_bytes']==0,'complete result')
    data=z.read(xmlpath); x=ET.fromstring(data)
    cases=[v.attrib['name'] for v in x.iter('testcase')]
    check(digest(data)==stage['actual']['xml_sha256']==stage['xml_retention'][0]['sha256'],'XML hash')
    check(x.attrib['name']==bank['class'] and sorted(cases)==sorted(bank['case_names'])==sorted(stage['actual']['case_names']) and len(cases)==2,'exact two identities')
    check(all(int(x.attrib.get(k,'-1'))==v for k,v in [('tests',2),('failures',0),('errors',0),('skipped',0)]) and not list(x.iter('failure')) and not list(x.iter('error')) and not list(x.iter('skipped')),'XML zero error/failure/skip')
    log=z.read(root+'command.log').decode(errors='replace')
    marker='> Task :mtg-sets:2025:tests:test';check(marker in log.splitlines() and 'BUILD SUCCESSFUL' in log,'fresh target task and build')
    check(a['official_games']==a['official_seeds']==a['new_pilot_cases']==0 and not a['gameplay_authorized'] and not a['full_runtime_accepted'],'no gameplay')

review={'schema':'izzet-veteran-mechanics-implementer-raw-audit-v1','role':'implementer, independent audit recorded separately by conductor',
  'artifact':{'id':10922269951,'sha256':digest(open(path,'rb').read()),'zip_members':len(names),'crc':'PASS'},
  'run':{'id':36292872794,'attempt':1,'event':'push','head':'c841edaa2f836e33a5f0ac881b1bf9c87e5d4df5','branch':'lab/izzet-veteran-mechanics-qualification-20260927','conclusion':'success'},
  'source':{'head':a['source_head'],'tree':a['source_tree'],'before_after_equal':before==after},
  'result':{'status':'PASS_BOUNDED_TWO_CASE_RAW_DIAGNOSTIC' if not errors else 'FAIL_RAW_AUDIT','cases':cases,'errors':errors,'official_games':0,'official_seeds':0},
  'branch_creation_provenance':{'observed_initial_create_branch_response':'HTTP 422 Reference already exists','subsequent_live_ref':'refs/heads/lab/izzet-veteran-mechanics-qualification-20260927 at c841edaa2f836e33a5f0ac881b1bf9c87e5d4df5','observed_actions_run':'36292872794 push attempt 1 on exact head, created 2026-09-27T03:56:44Z, completed success 2026-09-27T04:03:07Z','interpretation':'422 did not authorize retry; creation actor cannot be inferred from the response; exact branch and sole observed run were audited; no second dispatch'},
  'limitations':['These two deterministic cases establish only the observed untap/team-pump software behavior. They do not establish complete Veteran Beastrider compatibility, commander lifecycle/damage, pilots, replay, or Position-1 admission.']}
with open(out,'w') as f:json.dump(review,f,indent=2,sort_keys=True);f.write('\n')
print(json.dumps(review,indent=2))
