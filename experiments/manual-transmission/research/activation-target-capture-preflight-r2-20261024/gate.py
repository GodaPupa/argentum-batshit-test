from pathlib import Path
import os,json,hashlib,subprocess,sys,xml.etree.ElementTree as ET,re
ROOT=Path("experiments/manual-transmission/research/activation-target-capture-preflight-r2-20261024");P=json.loads((ROOT/'PROTOCOL.json').read_text());OUT=Path('activation-preflight-r2-evidence');OUT.mkdir(exist_ok=True)
sha=lambda b:hashlib.sha256(b).hexdigest()
git=lambda *a:subprocess.check_output(['git',*a],text=True).strip()
SOURCE=os.environ['SOURCE'];PROD=Path('rules-engine/src/main/kotlin/com/wingedsheep/engine/handlers/continuations/ActivateAbilityXCostContinuationResumer.kt')
mode=sys.argv[1]
if mode=='bind':
 assert git('rev-parse','HEAD^')==SOURCE
 assert git('rev-parse',SOURCE+'^{tree}')==os.environ['SOURCE_TREE']
 assert git('rev-parse','origin/main')==P['protected_main']
 assert git('diff','--name-only',SOURCE,'HEAD')==os.environ['WORKFLOW']
 assert set(git('diff','--name-only',P['base'],SOURCE).splitlines())==set(P['implementation_sha256'])|set(P['preserved_records'])|{str(ROOT/n) for n in ['PROTOCOL.json','gate.py']}
 for p,h in P['implementation_sha256'].items():assert sha(Path(p).read_bytes())==h,p
 bindings=json.loads(Path('experiments/manual-transmission/research/paused-token-entry-r2-20261023/BINDINGS.json').read_text());assert len(bindings)==144
 for p,s in bindings.items():assert sha(Path(p).read_bytes())==s['sha256'],p
 gold=json.loads(Path('experiments/manual-transmission/research/ordinary-build-r1-20261022/GOLDENS.json').read_text());assert len(gold)==23
 for s in gold:assert sha(Path(s['path']).read_bytes())==s['sha256']
 old=json.loads(Path('experiments/manual-transmission/research/modal-spell-capture-r3-20261024/PROTOCOL.json').read_text())
 for p,h in old['legacy_fixture_sha256'].items():assert sha(Path(p).read_bytes())==h
 assert not git('status','--porcelain','--untracked-files=no')
 (OUT/'BINDING.json').write_text(json.dumps(dict(source=SOURCE,tree=os.environ['SOURCE_TREE'],validation=git('rev-parse','HEAD'),protected_bindings=144,goldens=23,legacy=82)))
elif mode=='mutate':
 s=PROD.read_text();anchor='        val replay = continuation.action.copy(targets = chosen, originalTargetSlots = plan)';assert s.count(anchor)==1
 s=s.replace(anchor,'        val faulty = plan.copy(instances = plan.instances.map { group -> group.copy(slots = group.slots.map { it.copy(objectRef = null) }) })\n'+anchor.replace('= plan)','= faulty)'))
 PROD.write_text(s);(OUT/'MUTATION.diff').write_text(git('diff'))
elif mode=='receive':
 stage=sys.argv[2];out=OUT/stage;rc=int((out/'exit.txt').read_text());log=(out/'gradle.log').read_text();rows=[]
 for p in (out/'junit').rglob('TEST-*.xml'):
  suite=ET.parse(p).getroot();ts=list(suite.iter('testcase'));assert len(ts)==int(suite.attrib['tests'])
  for t in ts:
   assert t.find('error') is None and t.find('skipped') is None
   f=t.find('failure');rows.append((t.attrib['name'],None if f is None else f.attrib['type']))
 assert sorted(n for n,_ in rows)==sorted(P['targeted_cases'])
 failed=sorted(n for n,f in rows if f is not None)
 assert failed==(sorted(P['expected_mutation_failures']) if stage=='mutation' else [])
 assert all(f in [None,'org.opentest4j.AssertionFailedError'] for _,f in rows)
 if stage=='mutation':
  for p in (out/'junit').rglob('TEST-*.xml'):
   for t in ET.parse(p).getroot().iter('testcase'):
    f=t.find('failure')
    if f is not None:
     frame=re.search(r'ActivationTargetCaptureTest.kt:(\d+)',f.text or '')
     assert frame and int(frame.group(1))==P['mutation_first_frames'][t.attrib['name'][:4]]
 assert (rc!=0)==(stage=='mutation')
 assert not re.search(r'^e: |Compilation error|Unresolved reference',log,re.M)
 tasks=re.findall(r'^> Task (\S+) FAILED$',log,re.M);assert tasks==([':rules-engine:test'] if stage=='mutation' else [])
 (out/'RECEIPT.json').write_text(json.dumps(dict(stage=stage,cases=8,failed=failed,scope=P['scope'])))
elif mode=='hash':
 (OUT/'SHA256SUMS').write_text(''.join(sha(p.read_bytes())+'  '+str(p.relative_to(OUT))+'\n' for p in sorted(OUT.rglob('*')) if p.is_file() and p.name!='SHA256SUMS'))
