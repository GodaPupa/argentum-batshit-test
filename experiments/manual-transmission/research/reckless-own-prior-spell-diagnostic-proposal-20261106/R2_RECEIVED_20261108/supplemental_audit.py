"""Supplementary read-only audit of the once-received R2 evidence. No build or download."""
from pathlib import Path
from collections import Counter
import hashlib,json,re,xml.etree.ElementTree as ET
R=Path(__file__).resolve().parent
Q=R/'received/unpacked/qualification'
P=json.loads((R/'PROTOCOL.json').read_bytes())
pins=json.loads((R/'RECEIVING_PINS.json').read_bytes())
extra=json.loads((R/'SUPPLEMENTAL_PINS.json').read_bytes())
F=pins['frozen']
sha=lambda b:hashlib.sha256(b).hexdigest()
blob=lambda b:hashlib.sha1(b'blob '+str(len(b)).encode()+b'\0'+b).hexdigest()
def tree(node):
 body=b''
 for name,v in sorted(node.items(),key=lambda e:e[0]+(b'/' if isinstance(e[1],dict) else b'')):
  mode,oid=(b'40000',bytes.fromhex(tree(v))) if isinstance(v,dict) else v
  body+=mode+b' '+name+b'\0'+oid
 return hashlib.sha1(b'tree '+str(len(body)).encode()+b'\0'+body).hexdigest()
def inventory(data):
 root={}; leaves={}
 for x in data.split(b'\0'):
  if not x:continue
  h,p=x.split(b'\t',1);mode,kind,oid=h.split();leaves[p.decode()]=(mode,kind,oid)
  node=root;parts=p.split(b'/')
  for part in parts[:-1]:node=node.setdefault(part,{})
  node[parts[-1]]=(mode,bytes.fromhex(oid.decode()))
 return root,leaves
source,leaves=inventory((Q/'initial/SOURCE-TREE.bin').read_bytes())
base,base_leaves=inventory((Q/'initial/BASE-TREE.bin').read_bytes())
assert tree(source)==F['source_tree'] and tree(base)==P['base_tree']
assert all(leaves.get(k)==v for k,v in base_leaves.items())
assert sorted(set(leaves)-set(base_leaves))==P['source_delta'] and len(P['source_delta'])==13
for k,c in extra['git'].items():
 expected={'source':'source','validation':'validation','event':'event','first_parent':'first_parent'}[k]
 assert c['sha']==F[expected]
 assert c['tree']['sha']==F[{'source':'source_tree','validation':'validation_tree','event':'event_tree','first_parent':'first_tree'}[k]]
assert [x['sha'] for x in extra['git']['source']['parents']]==[F['source_parent']]
assert [x['sha'] for x in extra['git']['validation']['parents']]==[F['source']]
assert [x['sha'] for x in extra['git']['event']['parents']]==[F['first_parent'],F['validation']]
for c in extra['comparisons'].values():
 assert not c['too_large'] and len(c['files'])==1
 assert c['files'][0]['filename']==F['workflow'] and c['files'][0]['status']=='added'
assert blob((Q/'WORKFLOW.yml').read_bytes())==F['workflow_blob']
assert sha((Q/'WORKFLOW.yml').read_bytes())==F['workflow_sha256']
assert blob((Q/'protocol/PROTOCOL.json').read_bytes())==F['protocol_blob']
assert P['epoch']==F['epoch']=='manual-quarantine-20261106-01'
assert P['branch']==F['branch']=='manual-transmission/quarantine-recovery-20261106'
# Restore the additive test out of the inventory, then compare every other root subtree
# outside the historical research/workflow lineage with the admitted runtime root.
node=source
parts=P['test'].encode().split(b'/')
for part in parts[:-1]:node=node[part]
test_entry=node.pop(parts[-1])
assert test_entry[1].hex()==blob((Q/'initial/source'/P['test']).read_bytes())
accepted={e['path']:e for e in extra['accepted_root']['tree']}
assert not extra['accepted_root']['truncated']
assert set(source)=={p.encode() for p in accepted}
protected=[]
for p,e in accepted.items():
 if p in ['experiments','.github']:continue
 value=source[p.encode()]
 digest=tree(value) if isinstance(value,dict) else value[1].hex()
 assert digest==e['sha'],p
 protected.append(p)
for e in extra['accepted_experiments']['tree']:
 if e['path']=='manual-transmission':continue
 value=source[b'experiments'][e['path'].encode()]
 assert (tree(value) if isinstance(value,dict) else value[1].hex())==e['sha'],e['path']
node[parts[-1]]=test_entry
assert tree(source)==F['source_tree']
assert (Q/'initial/BASE-TREE.bin').read_bytes()==(Q/'final/BASE-TREE.bin').read_bytes()
assert (Q/'initial/SOURCE-TREE.bin').read_bytes()==(Q/'final/SOURCE-TREE.bin').read_bytes()
assert (Q/'initial/TRACKED.diff').read_bytes()==(Q/'final/TRACKED.diff').read_bytes()==b''
stages=[]
for name in ['normal',*P['mutants']]:
 d=Q/('compile-'+name);log=(d/'output.log').read_text()
 assert (d/'exit.txt').read_text().strip()=='0'
 assert log.count('BUILD SUCCESSFUL')==1 and 'BUILD FAILED' not in log
 assert '> Task :rules-engine:compileKotlin\n' in log
 assert '> Task :rules-engine:compileTestKotlin' in log
 assert not list(d.rglob('TEST-*.xml')) and not re.search(r'> Task :[^\n]*:test(?: |\n)',log)
 stages.append(dict(stage=d.name,exit=0,compile_kotlin_executed=True,test_task_executed=False,
                    compile_test_kotlin_up_to_date='> Task :rules-engine:compileTestKotlin UP-TO-DATE' in log,
                    stdout_sha256=sha(log.encode())))
 expected_changed=[] if name=='normal' else [P['mutants'][name]['path']]
 changes=[]
 for path in P['normal_forms']:
  data=(Q/'forms'/name/path).read_bytes()
  if sha(data)!=P['bindings'][path]:changes.append(path)
 assert changes==expected_changed,(name,changes)
 if name=='normal':assert (Q/'forms'/name/'TRACKED.diff').read_bytes()==b''
 else:
  diff=(Q/'forms'/name/'TRACKED.diff').read_text()
  assert len(re.findall(r'^diff --git ',diff,re.M))==1
  assert diff.startswith('diff --git a/'+expected_changed[0]+' b/'+expected_changed[0]+'\n')
records=[]
for name in ['normal',*['negative-'+m for m in P['mutants']],'restored-normal']:
 d=Q/name;xmls=list(d.rglob('TEST-*.xml'));assert len(xmls)==1
 root=ET.parse(xmls[0]).getroot();tests=root.findall('testcase')
 assert Counter(t.attrib['name'] for t in tests)==Counter(P['cases'])
 assert root.attrib['tests']=='24' and root.attrib['errors']==root.attrib['skipped']=='0'
 negative=name.startswith('negative-');expected=len(P['mutants'][name[9:]]['failed_identities']) if negative else 0
 assert int(root.attrib['failures'])==expected
 log=(d/'output.log').read_text();code=int((d/'exit.txt').read_text())
 assert code==(1 if negative else 0)
 stdout_failures=re.findall(r'^[^\n]* > (PS [^\n]+) FAILED$',log,re.M)
 assert Counter(stdout_failures)==Counter(P['mutants'][name[9:]]['failed_identities'] if negative else [])
 assert ('BUILD FAILED' in log)==negative and ('BUILD SUCCESSFUL' in log)==(not negative)
 records.append(dict(stage=name,rows=24,failures=expected,errors=0,skips=0,exit=code,
                     timestamp=root.attrib['timestamp'],stdout_sha256=sha(log.encode()),junit_sha256=sha(xmls[0].read_bytes())))
assert [r['timestamp'] for r in records]==sorted(r['timestamp'] for r in records)
assert sum(r['failures'] for r in records)==18
runner=(Q/'protocol/run.py').read_text()
assert runner.index("assert stage('compile-' + name, 'compile') == 0") < runner.index("js(O / 'COMPILE-FIRST.json'") < runner.index("behavior('normal')")
assert "for path, data in normal.items():\n        (R / path).write_bytes(data)" in runner
assert json.loads((Q/'RESTORATION.json').read_text())==dict(normal_restored=True,tracked_diff='',status='')
for path in P['bindings']:
 assert (Q/'initial/source'/path).read_bytes()==(Q/'final/source'/path).read_bytes()
result=dict(state='SUPPLEMENTAL_AUDIT_PASSED',source=F['source'],source_tree=F['source_tree'],
 source_parent=F['source_parent'],epoch=F['epoch'],run=pins['expected_run'],job=pins['expected_job'],attempt=1,artifact=pins['expected_artifact'],
 immutable_git_pins_verified=True,validation_and_dispatch_each_add_only_exact_workflow=True,
 existing_base_files_unchanged=len(base_leaves),additive_source_files=13,protected_runtime_root_entries=protected,
 admitted_engine_existing_tests_sdk_fixtures_unchanged=True,other_experiments_unchanged=True,
 compile_stages=stages,behavior_stages=records,all_forms_compiled_before_behavior=True,
 restoration_between_forms='Frozen runner restores all three normal source buffers before each isolated form; archived full forms and one-file diffs prove no accumulated mutation. No separate intermediate normal-stage artifact is claimed.',
 final_normal_bytes_and_complete_tree_inventories_rebound=True,
 observed_build_notes=['The inner gradle-locked script reports shlock missing and invokes Gradle unlocked. The frozen workflow holds the outer flock for the entire runner and has noncancelling Manual Actions concurrency. This original warning is preserved.',
 'Gradle configuration-task SKIPPED/NO-SOURCE markers are preserved; they are not skipped JUnit cases. JUnit skips and errors are zero.',
 'All five shipping source forms execute compileKotlin. The unchanged test compilation is UP-TO-DATE in the four mutant compile stages.'],
 evidence_scope='Test-only two-player synthetic own-prior-spell predicate; no production, Surge, card, multiplayer or gameplay admission.')
(R/'SUPPLEMENTAL_AUDIT.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps({k:result[k] for k in ['state','existing_base_files_unchanged','all_forms_compiled_before_behavior','final_normal_bytes_and_complete_tree_inventories_rebound']}))
