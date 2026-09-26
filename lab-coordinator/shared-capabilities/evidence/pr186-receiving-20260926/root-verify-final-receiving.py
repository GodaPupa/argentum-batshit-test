from pathlib import Path
import json,hashlib,zipfile,gzip,collections,xml.etree.ElementTree as E
p=Path(__file__).parent
def sh(b):return hashlib.sha256(b).hexdigest()
def xmls(z):
 out={}
 for n in z.namelist():
  if not n.endswith('.xml'):continue
  x=E.fromstring(z.read(n));cases=list(x.iter('testcase'))
  assert len(cases)==int(x.get('tests','0'))
  assert all(int(x.get(k,0))==0 for k in ['failures','errors','skipped'])
  assert all(c.find(k) is None for c in cases for k in ['failure','error','skipped'])
  ident=tuple(sorted((c.get('classname'),c.get('name')) for c in cases))
  out[n]=(x.get('name'),ident,sh(z.read(n)))
 return out
old=Path('shared-audit/publish/lab-coordinator/shared-capabilities/evidence/combined-994383-takeover')
pri=json.loads((p/'priority-artifact-audit.json').read_text());pri_file=p/pri['artifact']['filename']
assert pri_file.stat().st_size==pri['artifact']['bytes'] and sh(pri_file.read_bytes())==pri['artifact']['sha256']
with zipfile.ZipFile(pri_file) as z,zipfile.ZipFile(old/'priority-qualification.zip') as oldz,zipfile.ZipFile(p/'original-artifact-10917771412.zip') as pz:
 assert z.testzip() is None
 cur=xmls(z);before=xmls(oldz)
 assert collections.Counter((x[0],x[1]) for x in cur.values())==collections.Counter((x[0],x[1]) for x in before.values())
 assert len(cur)==27 and sum(len(x[1]) for x in cur.values())==220
 bind=json.loads(z.read('binding-attempt.json'));manifest=json.loads(z.read('source-manifest.json'));provenance=json.loads(z.read('source-provenance.json'))
 qualified=json.loads(pz.read('build/reports/shared-postblock-source/source-binding.json'))
 assert bind['candidate_head']==bind['expected_head']=='b6fc446543d3ce566bb12c60d1ab6b84e1c96d8c'
 assert bind['observed_source_files_sha256']==bind['expected_source_files_sha256']==manifest['source_files_sha256']==provenance['source_files_sha256']==qualified['combined_source_files_sha256']
 assert len(bind['observed_source_files_sha256'])==250
 for n in z.namelist():
  if n.endswith('exit-status.txt'):assert z.read(n).strip()==b'0'
 assert z.read('effective-rules.txt')==oldz.read('effective-rules.txt')
log=gzip.decompress((p/pri['decoded_job_log']['filename']).read_bytes())
assert sh(log)==pri['decoded_job_log']['decoded_sha256'] and len(log)==pri['decoded_job_log']['decoded_bytes']
assert log.count(b'BUILD SUCCESSFUL')==27 and log.count(b'started executing tests')>=27
post=json.loads((p/'postblock-pr-author-artifact-audit.json').read_text());postfile=p/'original-artifact-10917980789.zip'
assert sh(postfile.read_bytes())==post['artifact_sha256'] and postfile.stat().st_size==post['artifact_bytes']
with zipfile.ZipFile(postfile) as z,zipfile.ZipFile(p/'original-artifact-10917771412.zip') as oldz:
 assert z.testzip() is None
 cur=xmls(z);before=xmls(oldz)
 assert collections.Counter((x[0],x[1]) for x in cur.values())==collections.Counter((x[0],x[1]) for x in before.values())
 assert len(cur)==17 and sum(len(x[1]) for x in cur.values())==127
 bind=json.loads(z.read('build/reports/shared-postblock-source/source-binding.json'))
 oldbind=json.loads(oldz.read('build/reports/shared-postblock-source/source-binding.json'))
 for k in ['source_files_sha256','combined_source_files_sha256','receiving_control_files_sha256','expected_cases','extraction_manifest_sha256','fixture_receiving_manifest_sha256','receiving_manifest_sha256']:
  assert bind[k]==oldbind[k],k
 assert bind['actual_head']=='b6fc446543d3ce566bb12c60d1ab6b84e1c96d8c' and bind['tree']=='3d6a178f3b8168444d7f09b7dd212d3e4d567036'
 for n in ['build/reports/shared-postblock-source/engine.txt','build/reports/shared-postblock-source/server.txt']:
  log=z.read(n);assert b'BUILD SUCCESSFUL' in log and b'started executing tests' in log
# Extract the frozen135 observer identities from already-existing current broadCI originals.
ci={}
ci_report=json.loads((p/'ci-artifact-audit.json').read_text())
ci_filenames={a['filename'] for a in ci_report['artifacts']}
assert len(ci_filenames)==7
for f in p.glob('original-artifact-*.zip'):
 with zipfile.ZipFile(f) as z:
  for n in z.namelist():
   if not n.endswith('.xml') or '/build/test-results/test/' not in n:continue
   if '/tests/' in n and not n.startswith('mtg-sets/'):continue
   x=E.fromstring(z.read(n));name=x.get('name')
   ci.setdefault(name,[]).append((f.name,n,x))
observers=[]
with zipfile.ZipFile(old/'observer-qualification.zip') as z:
 for n,(cl,expected,h) in xmls(z).items():
  candidates=[]
  for fn,xn,x in ci.get(cl,[]):
   if fn not in ci_filenames:continue
   actual=tuple(sorted((c.get('classname'),c.get('name')) for c in x.findall('testcase')))
   if actual==expected and all(int(x.get(k,0))==0 for k in ['failures','errors','skipped']):candidates.append((fn,xn,x))
  assert candidates,cl
  fn,xn,x=candidates[0];observers.append({'class':cl,'cases':len(expected),'current_ci_original':fn,'xml':xn,'timestamp':x.get('timestamp')})
assert len(observers)==18 and sum(x['cases'] for x in observers)==135
# Recheck all uploaded Pest XML and their current-CI identity matches without summing overlapping banks.
pest=json.loads((p/'pest-eight-receiving-audit.json').read_text());pestchecks=[]
for c in pest['components']:
 suites=[]
 for a in c['artifacts']:
  f=p/a['filename'];assert sh(f.read_bytes())==a['sha256'] and f.stat().st_size==a['bytes']
  with zipfile.ZipFile(f) as z:
   assert z.testzip() is None
   for n,(cl,ident,hash_) in xmls(z).items():
    record=next(v for v in c['suites'] if v['xml_member']==n)
    assert record['cases']==len(ident) and record['sha256']==hash_
    cf=p/f"original-artifact-{record['same_case_identities_in_current_full_ci_artifact']}.zip"
    with zipfile.ZipFile(cf) as cz:
     matching=[E.fromstring(cz.read(cn)) for cn in cz.namelist() if cn.endswith('/TEST-'+cl+'.xml')]
     assert len(matching)==1
     assert tuple(sorted((t.get('classname'),t.get('name')) for t in matching[0].findall('testcase')))==ident
    suites.append((cl,len(ident)))
 assert len(suites)==c['actual_uploaded_suites'] and sum(x[1] for x in suites)==c['actual_uploaded_cases']
 for support in c['separate_same_source_current_ci_support']:
  with zipfile.ZipFile(p/f"original-artifact-{support['ci_artifact']}.zip") as z:
   raw=z.read(support['ci_xml']);assert sh(raw)==support['ci_xml_sha256']
   x=E.fromstring(raw);assert len(x.findall('testcase'))==support['actual_ci_cases'] and all(int(x.get(k,0))==0 for k in ['failures','errors','skipped'])
 pestchecks.append({'run':c['run'],'uploaded_suites':len(suites),'uploaded_cases':sum(x[1] for x in suites),'separate_ci_classes':len(c['separate_same_source_current_ci_support'])})
out={'source':'b6fc446543d3ce566bb12c60d1ab6b84e1c96d8c','tree':'3d6a178f3b8168444d7f09b7dd212d3e4d567036','actual_main_merge':'fc5c443fc9d674d85dc1942a553319fee8b69d87','actual_main_tree':'88967f3a9ba0479661f02150f82ddcc9a2066e42','priority_original_cases':220,'priority_original_zip':pri['artifact'],'postblock_pr_original_cases':127,'postblock_pr_zip_sha256':post['artifact_sha256'],'source_bindings':'250exact map matches independently Sphinx-rehashed source; postblock54+250/control maps unchanged from independently accepted push artifact','observer_current_ci_extraction':observers,'pest_originals':pestchecks,'status':'PASS_BOUNDED_RECEIVING_RAW_PROOFS','limits':['Observer135 is a declared extraction from original fullCI, not a new run or added total','Postblock127 overlaps observer; two runs are preserved separately without duplicate uniqueness claims','Pest counts overlap and legacy log-only runs retain separate CI evidence, not invented original XML','No game,claim,permit,pilot or historicalPestquarantine disposition']}
(p/'root-final-receiving-checks.json').write_text(json.dumps(out,indent=2)+'\n')
print(json.dumps({'status':out['status'],'priority':220,'postblock_pr':127,'observer':135,'pest_runs':len(pestchecks),'sha256':sh((p/'root-final-receiving-checks.json').read_bytes())}))
