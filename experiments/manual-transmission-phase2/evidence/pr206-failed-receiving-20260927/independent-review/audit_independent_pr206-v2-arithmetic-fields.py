"""Read-only original artifact/source/log preservation audit; no repository execution."""
import collections, datetime, gzip, hashlib, io, json, pathlib, re, zipfile
import xml.etree.ElementTree as ET
ROOT=pathlib.Path('manual-shared-composition/pr206-discovery')
CORE=ROOT/'manual-audit'
PUB=ROOT/'failure-publication'
OUT=pathlib.Path('sphinx-audit/manual206-review')
def sha(b): return hashlib.sha256(b).hexdigest()
def objhash(kind,b): return hashlib.sha1(kind.encode()+b' '+str(len(b)).encode()+b'\0'+b).hexdigest()
def pin(p):
 b=p.read_bytes();return {'bytes':len(b),'sha256':sha(b)}
def dt(s): return datetime.datetime.fromisoformat(s.replace('Z','+00:00'))
def checked_zip(z):
 names=z.namelist();assert len(names)==len(set(names));assert z.testzip() is None
 return [{'path':n,'bytes':z.getinfo(n).file_size,'sha256':sha(z.read(n))} for n in names]
author=json.loads((CORE/'core-original-audit.json').read_text())
assert pin(CORE/'core-original-audit.json')['sha256']=='a14a25d35ef61ee8a929612bbdfa8fa64f3b09e4d8cfd63cb7c7698817454e14'
live=json.loads((OUT/'independent-live-metadata.json').read_text())
assert live['commits']['source']['tree']['sha']==live['commits']['merge']['tree']['sha']=='c29d399dc4e481179d48ff833af58ea44db1843f'
assert len(live['runs'])==7
for run in live['runs'].values():
 assert (run['head_sha'],run['run_attempt'],run['event'],run['status'],run['conclusion'])==('60a9e20b61f63c4d42772ca8d122aabe68aabe2f',1,'pull_request','completed','failure')
# Reconstruct every cached Git tree object, thereby binding the entire saved map
# to the independently live-fetched immutable root without trusting author rows.
t=json.loads(gzip.decompress((CORE/'source-live-tree.json.gz').read_bytes()))
assert not t['truncated'] and len(t['tree'])==29358
entries={e['path']:e for e in t['tree']};assert len(entries)==len(t['tree'])
children=collections.defaultdict(list)
for e in t['tree']:
 parent,_,name=e['path'].rpartition('/');children[parent].append((name,e))
tree_checks=[]
for directory in sorted(children,key=lambda p:(-p.count('/'),-len(p))):
 rows=sorted(children[directory],key=lambda v:v[0].encode()+(b'/' if v[1]['type']=='tree' else b''))
 body=b''.join(e['mode'].lstrip('0').encode()+b' '+name.encode()+b'\0'+bytes.fromhex(e['sha']) for name,e in rows)
 actual=objhash('tree',body);expected=t['sha'] if not directory else entries[directory]['sha']
 assert actual==expected,(directory,actual,expected)
 tree_checks.append({'path':directory,'sha':actual,'children':len(rows)})
assert t['sha']==live['commits']['source']['tree']['sha']
artifacts=[]
for a in author['artifacts']:
 p=CORE/a['path'];assert pin(p)=={k:a[k] for k in ('bytes','sha256')}
 with zipfile.ZipFile(p) as z:
  members=checked_zip(z);assert members==a['members']
 artifacts.append({'path':a['path'],**pin(p),'members':len(members),'all_member_hashes_match_author':True})
api_artifacts=live['artifacts']['36286644783']['artifacts']
assert len(api_artifacts)==2
assert sorted((a['size_in_bytes'],a['digest']) for a in api_artifacts)==sorted((a['bytes'],'sha256:'+a['sha256']) for a in artifacts)
for r in ('36286644786','36286644804'): assert live['artifacts'][r]['total_count']==0 and live['artifacts'][r]['artifacts']==[]
with zipfile.ZipFile(CORE/'source-original.zip') as z:
 nested=z.read('source-bundle.zip');assert sha(nested)=='167acad037e2a089d37fd8598481d88774cdefecd38b231dd9dc05b0f28a5888'
 with zipfile.ZipFile(io.BytesIO(nested)) as n:
  rows=checked_zip(n);assert len(rows)==24799
  matched=[];phase=[]
  for row in rows:
   name=row['path'];data=n.read(name)
   if name.startswith('source/'):
    path=name[len('source/'):];assert entries[path]['type']=='blob';assert objhash('blob',data)==entries[path]['sha'],path
    matched.append(path)
   else: assert name.startswith('phase1/');phase.append(name)
  assert len(matched)==24719 and len(phase)==80
  assert 'rules-engine/src/testFixtures/kotlin/com/wingedsheep/engine/research/ferocity/FerocityOfTheHuntPrerelease.kt' not in entries
  assert b'import com.wingedsheep.engine.research.ferocity.FerocityOfTheHuntPrerelease as ferocityResearchFixture' in n.read('source/rules-engine/src/test/kotlin/com/wingedsheep/engine/scenarios/ActivationPriorityScenarioTest.kt')
  # Workflows are separately source-recovered files, not nested source-bundle members.
  workflows={name:(CORE/'actual-workflows'/name).read_bytes() for name in ('manual-transmission-phase2-capability.yml','ci.yml','engine-priority-after-resolution.yml')}
  for name,data in workflows.items():
   assert objhash('blob',data)==entries['.github/workflows/'+name]['sha']
   assert data==(CORE/name).read_bytes()
source_proof={'nested_bytes':len(nested),'nested_sha256':sha(nested),'nested_members':len(rows),'current_source_Git_blobs_verified':len(matched),'phase1_preserved_without_new_Git_rebind':len(phase),'complete_tree_objects_recomputed':len(tree_checks),'root_tree':t['sha']}
jobs={j['id']:j for group in live['jobs'].values() for j in group['jobs']}
assert len(jobs)==13 and all(group['total_count']==len(group['jobs']) for group in live['jobs'].values())
logs={};logrows=[];printed=[]
for j in author['jobs']:
 meta=jobs[j['job']];assert meta['conclusion']==j['conclusion'] and meta['name']==j['name']
 if 'path' not in j['log']:
  assert j['name']=='coverage' and j['conclusion']=='skipped';continue
 compressed=(CORE/j['log']['path']).read_bytes();data=gzip.decompress(compressed)
 assert sha(compressed)==j['log']['sha256'] and len(compressed)==j['log']['bytes']
 assert sha(data)==j['log']['decoded_sha256'] and len(data)==j['log']['decoded_bytes']
 lines=data.decode().splitlines();assert len(lines)==j['log']['decoded_lines'];logs[j['job']]=lines
 clean=[re.sub(r'^\d{4}-\d\d-\d\dT\S+Z ?','',line) for line in lines]
 status=[]
 for line_no,line in enumerate(clean,1):
  m=re.fullmatch(r'([^>].* > .*) (PASSED|FAILED|SKIPPED)',line)
  if m:
   row={'job':j['job'],'line':line_no,'printed_identity':m[1],'status':m[2]};status.append(row)
   if j['run']==36286644786:printed.append(row)
 counts=dict(collections.Counter(r['status'] for r in status));assert counts==j.get('stdout_case_status_counts',{})
 if j['name']!='backend':
  assert any('HEAD is now at 60a9e20b' in l or 'HEAD is now at 06094adf' in l for l in clean)
 for expected in j.get('compile_failures',[]):assert expected in clean
 for expected in j.get('process_exits',[]):assert expected in clean
 logrows.append({'job':j['job'],'name':j['name'],'compressed_sha256':sha(compressed),'decoded_sha256':sha(data),'decoded_bytes':len(data),'case_status_counts':counts,'recorded_first_line':lines[0],'recorded_last_line':lines[-1]})
assert len(logs)==12
expected_printed=json.loads(gzip.decompress((CORE/'ci-printed-case-statuses.json.gz').read_bytes()))
key=lambda x:(x['job'],x['line'],x['printed_identity'],x['status'])
assert sorted(map(key,printed))==sorted(map(key,expected_printed))
counts=dict(collections.Counter(x['status'] for x in printed));assert counts=={'PASSED':12316,'FAILED':165,'SKIPPED':53}
assert 'AssertionError: mtg-sets/2026/tests/src/test/kotlin/com/wingedsheep/engine/scenarios/PrismariTheInspirationScenarioTest.kt' in '\n'.join(logs[108528555744])
assert not any('> Task :' in l for l in logs[108528555744])
assert any('No files were found' in l for l in logs[108528555744])
for job in (108528555693,108528555695):
 assert any('> Task :rules-engine:compileTestKotlin FAILED' in l for l in logs[job])
 assert not any(re.search(r'> Task :rules-engine:test(?: |$)',l) for l in logs[job])
assert not any('> Task :mtg-sets:2017-2022:tests:test' in l for l in logs[108528555742])
assert any('> Task :mtg-search:test FROM-CACHE' in l for l in logs[108528555696])
engine_meta=jobs[108528555693];xmlrows=[];card=0;snapshot=0
author_xml={r['member']:r for r in json.loads((CORE/'engine-actual-xml-cases.json').read_text())}
with zipfile.ZipFile(CORE/'engine-original.zip') as z:
 for name in z.namelist():
  if not name.endswith('.xml'):continue
  data=z.read(name);e=ET.fromstring(data);cases=e.findall('testcase');assert len(cases)==int(e.attrib['tests'])
  assert all(not c.findall('failure') and not c.findall('error') and not c.findall('skipped') for c in cases)
  assert all(int(e.attrib.get(k,0))==0 for k in ('failures','errors','skipped'))
  stamp=dt(e.attrib['timestamp']);assert dt(engine_meta['started_at'])<=stamp<=dt(engine_meta['completed_at'])
  # Match actual source-bound per-step execution windows rather than job duration alone.
  is_snapshot='CardDefinitionSnapshotTest' in name
  needle='snapshot' if is_snapshot else 'card'
  possible=[s for s in engine_meta['steps'] if needle in s['name'].lower() and s['conclusion']=='success' and s.get('started_at') and s.get('completed_at')]
  within=[s for s in possible if dt(s['started_at'])<=stamp<=dt(s['completed_at'])]
  assert within,(name,stamp,possible)
  actual=[{'class':c.attrib['classname'],'name':c.attrib['name'],'status':'pass'} for c in cases]
  assert actual==author_xml[name]['cases'] and sha(data)==author_xml[name]['sha256']
  row={'path':name,'sha256':sha(data),'timestamp':e.attrib['timestamp'],'cases':len(cases),'step':within[0]['name']};xmlrows.append(row)
  if is_snapshot:snapshot+=len(cases)
  else:card+=len(cases)
assert len(xmlrows)==27 and card==143 and snapshot==340
# Verify the complete outer packet and unchanged previously reviewed inherited packet.
packet=PUB/'seven-failed-attempts.zip';assert pin(packet)=={'bytes':33916511,'sha256':'a24c895c150f03c8f271183caa8280e996705d5184d22c1f3bcf116d034df029'}
with zipfile.ZipFile(packet) as z:
 packed=checked_zip(z);assert len(packed)==49
 manifest=json.loads(z.read('preservation-manifest.json'));assert z.read('preservation-manifest.json')==(PUB/'preservation-manifest.json').read_bytes()
 assert set(z.namelist())=={r['path'] for r in manifest['files']}|{'preservation-manifest.json'}
 for r in manifest['files']:
  data=z.read(r['path']);assert len(data)==r['bytes'] and sha(data)==r['sha256']
  if r['path'].startswith('core/'):assert data==(CORE/r['path'][5:]).read_bytes()
 ih=z.read('inherited/four-inherited-evidence.zip');assert sha(ih)=='9d6ac3563c3d949e5c9f76abb54aa5f9db7af1782fc7e53c74f7f151d6645dfd'
 prior=z.read('inherited/manual-independent-preservation-review.json');assert sha(prior)=='ec3d6809b30032b1589236f19dfa237e404539e0aab333dfe5fbe20640e5d1be'
 inherited_review=json.loads(prior)
 with zipfile.ZipFile(io.BytesIO(ih)) as n:
  irows=checked_zip(n);assert len(irows)==21
  im=json.loads(n.read('preservation-manifest.json'))
  for r in im['members']:
   data=n.read(r['path']);assert len(data)==r['bytes'] and sha(data)==r['sha256']
  for name in n.namelist():
   if name.endswith('.zip'):
    with zipfile.ZipFile(io.BytesIO(n.read(name))) as inner:checked_zip(inner)
   elif name.endswith('.log.gz'):
    data=gzip.decompress(n.read(name));assert sha(data) in {r['decoded_sha256'] for r in inherited_review['logs']}
 correction=json.loads(z.read('core/author-audit-recount-correction.json'))
 assert correction['earlier_informal_claim']['total']==496 and correction['actual_independent_xml_recount']['total']==483
 assert sha(z.read('core/audit-core-before-arithmetic-correction.py'))==correction['preserved_initial_script_sha256']
 assert sha(z.read('core/audit_core_attempts.py'))==correction['corrected_script_sha256']
 old=z.read('core/audit-core-before-arithmetic-correction.py');new=z.read('core/audit_core_attempts.py')
 assert old.replace(b'==496',b'==483').replace(b'== 496',b'== 483')==new
report={'schema':'manual206-independent-original-byte-audit-v1','scope':'Original failed-attempt preservation; no new repository tests, JVM, event or gameplay.',
 'author_report':pin(CORE/'core-original-audit.json'),'selected_live_metadata':pin(OUT/'independent-live-metadata.json'),
 'artifacts':artifacts,'source_binding':source_proof,'logs':logrows,'xml':xmlrows,
 'actual_capability_xml':{'card':card,'snapshot':snapshot,'total':card+snapshot,'xml_files':len(xmlrows),'failures':0,'errors':0,'skips':0},
 'CI_printed_status_lines':counts,'CI_raw_XML_available':False,'priority_fixtures_executed':0,
 'packet':{**pin(packet),'members':len(packed),'manifest':pin(PUB/'preservation-manifest.json'),'all_members_rehashed':True},
 'inherited':{'archive_sha256':sha(ih),'members':21,'original_ZIPs_preserved_and_CRC_checked':2,'decoded_logs_fully_decompressed':4,'reused_independent_preservation_review_sha256':sha(prior)},
 'corrections':{'author_arithmetic_496_to_483_preserved':True,'preliminary_parser_outputs_preserved':True,'separate_prior_static_closure_correction':pin(pathlib.Path('sphinx-audit/manual71-review/static-dependency-review-correction-pr206.json'))},
 'limitations':['CI status lines are not complete raw XML or distinct-case certification.','80 Phase1 source files are preserved without fresh full Git rebinding.','Source bundle is the preserved24719-file subset, not a claim all repository files or loaded runtime were observed.','No source, policy, fixture, golden, admission or allocation changed.']}
(OUT/'independent-original-byte-audit.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps({'report':pin(OUT/'independent-original-byte-audit.json'),'source':source_proof,'xml':report['actual_capability_xml'],'CI':counts,'packet':report['packet']},indent=2))
