from pathlib import Path
import hashlib,json,os,shutil,subprocess,sys,xml.etree.ElementTree as ET
R=Path(os.environ['RECORD']);O=Path(os.environ['EVIDENCE']);O.mkdir(exist_ok=True)
P=json.loads((R/'PROTOCOL.json').read_text());sha=lambda b:hashlib.sha256(b).hexdigest()
git=lambda *a:subprocess.check_output(['git',*a],text=True).strip()
if sys.argv[1]=='bind':
 assert git('rev-parse','HEAD^')==os.environ['SOURCE']
 assert git('rev-parse',os.environ['SOURCE']+'^{tree}')==os.environ['SOURCE_TREE']
 assert git('diff','--name-only',os.environ['SOURCE'],'HEAD')==os.environ['WORKFLOW']
 delta=git('diff','--name-only',P['base'],os.environ['SOURCE']).splitlines()
 assert set(delta)==set(P['sha256'])|{str(R/name) for name in ['PROTOCOL.json','gate.py','validation.just']}
 for p,h in P['sha256'].items():assert sha(Path(p).read_bytes())==h,p
 bindings=json.loads(Path('experiments/manual-transmission/research/paused-token-entry-r2-20261023/BINDINGS.json').read_text())
 assert len(bindings)==144
 for p,v in bindings.items():assert sha(Path(p).read_bytes())==v['sha256'],p
 for p in Path('rules-engine/build/test-results').glob('test/*.xml'):p.unlink()
 (O/'BINDING.json').write_text(json.dumps(dict(source=os.environ['SOURCE'],tree=os.environ['SOURCE_TREE'],validation=git('rev-parse','HEAD'),run=os.environ['GITHUB_RUN_ID'],delta=delta,protected_bindings=144)))
elif sys.argv[1]=='receive':
 rows=[]
 for p in Path('rules-engine/build/test-results/test').glob('*.xml'):
  dest=O/'xml'/p.name;dest.parent.mkdir(exist_ok=True);shutil.copy2(p,dest)
  for t in ET.parse(p).getroot().iter('testcase'):
   rows.append((t.attrib['classname']+'::'+t.attrib['name'],len(t.findall('failure')),len(t.findall('error')),len(t.findall('skipped'))))
 (O/'RAW_ROWS.json').write_text(json.dumps(rows,indent=2))
 assert int((O/'targeted-exit.txt').read_text())==0
 assert sorted(r[0] for r in rows)==sorted(P['tests']) and len(rows)==8
 assert all(r[1:]==(0,0,0) for r in rows)
 (O/'PREFLIGHT.json').write_text(json.dumps(dict(status='TARGETED_SOURCE_PREFLIGHT_ONLY',tests=8,component_accepted=False,remaining=P['remaining']),indent=2))
elif sys.argv[1]=='archive':
 for p in Path('rules-engine/build/test-results/test').glob('*.xml'):
  dest=O/'xml'/p.name;dest.parent.mkdir(exist_ok=True);shutil.copy2(p,dest)
 shutil.copytree(R,O/'protocol',dirs_exist_ok=True)
 shutil.copy2(os.environ['WORKFLOW'],O/'WORKFLOW.yml')
 (O/'TRACKED_CHANGES.txt').write_text(git('diff','--name-only')+'\n')
 (O/'SHA256SUMS').write_text(''.join(sha(p.read_bytes())+'  '+str(p.relative_to(O))+'\n' for p in sorted(O.rglob('*')) if p.is_file() and p.name!='SHA256SUMS'))
