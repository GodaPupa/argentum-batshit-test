"""Isolated remaining Fblthp evidence collector; no card or feature admission."""
from pathlib import Path
import os,json,subprocess,hashlib,urllib.request,time,traceback,gzip
HERE=Path(__file__).resolve().parent;ROOT=Path.cwd();P=json.loads((HERE/'PROTOCOL.json').read_text());OUT=ROOT/P['evidence']
sha=lambda b:hashlib.sha256(b).hexdigest()
git=lambda *a:subprocess.check_output(['git',*a],text=True).strip()
def save(p,b):p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(b)
def js(p,v):save(p,(json.dumps(v,indent=2)+'\n').encode())
def bind(label):
 assert P['state']=='FROZEN_AFTER_VERIFIED_ONE_TEST_DEPENDENT_RECEIPT'
 assert git('rev-parse',os.environ['SOURCE']+'^{tree}')==os.environ['SOURCE_TREE']
 assert git('rev-parse','HEAD^')==os.environ['SOURCE'] and git('rev-parse',os.environ['SOURCE']+'^')==P['base']
 assert git('diff','--name-only',os.environ['SOURCE'],'HEAD')==os.environ['WORKFLOW']
 assert git('diff','--name-only',P['base'],os.environ['SOURCE']).splitlines()==sorted(P['source_delta'])
 assert not git('diff','--name-only',P['dependent_source'],os.environ['SOURCE'],'--',':(exclude)experiments/manual-transmission/research')
 assert git('rev-parse','refs/remotes/origin/main')==P['main'] and not git('status','--porcelain','--untracked-files=no')
 for path,h in P['bindings'].items():
  b=(ROOT/path).read_bytes();assert sha(b)==h,path;save(OUT/label/'source'/path,b)
 for path,h in P['git_bindings'].items():
  b=(ROOT/path).read_bytes();assert git('hash-object',path)==h,path;save(OUT/label/'source'/path,b)
 save(OUT/label/'TRACKED.diff',subprocess.check_output(['git','diff','--']))
 trees={}
 for module in P['unchanged_modules']:
  a=git('rev-parse',P['dependent_source']+':'+module);b=git('rev-parse',os.environ['SOURCE']+':'+module);assert a==b,module;trees[module]=a
 js(OUT/label/'UNCHANGED_TREES.json',trees)
 receipt=(ROOT/P['dependent_receipt']).read_bytes();assert git('hash-object',P['dependent_receipt'])==P['dependent_receipt_blob']
 d=json.loads(receipt);assert d['state']=='ONE_TEST_DEPENDENT_CLIENT_ORIGINAL_VERIFIED_NOT_CARD_ADMISSION' and not d['gate_errors']
 assert d['source']==P['dependent_source'] and d['run']==40977504099 and d['validation']=='2219d5783d67fd3b674cdd7850502cc1b1976668'
 save(OUT/'DEPENDENT_RECEIPT.json',receipt)
 if label=='initial':
  for f in HERE.iterdir():
   if f.is_file():save(OUT/'protocol'/f.name,f.read_bytes())
  save(OUT/'WORKFLOW.yml',(ROOT/os.environ['WORKFLOW']).read_bytes())
  js(OUT/'BINDING.json',dict(source=os.environ['SOURCE'],tree=os.environ['SOURCE_TREE'],validation=git('rev-parse','HEAD'),validation_tree=git('rev-parse','HEAD^{tree}'),workflow=os.environ['WORKFLOW'],workflow_blob=git('hash-object',os.environ['WORKFLOW']),run=os.environ['GITHUB_RUN_ID'],attempt=int(os.environ['GITHUB_RUN_ATTEMPT']),run_number=int(os.environ['GITHUB_RUN_NUMBER'])))
def stage(name,recipe,allowed=(0,)):
 folder=OUT/name;folder.mkdir(parents=True)
 with (folder/'stdout.log').open('wb') as out,(folder/'stderr.log').open('wb') as err:
  rc=subprocess.run(['just','--working-directory',str(ROOT),'--justfile',str(HERE/'validation.just'),recipe],stdout=out,stderr=err).returncode
 (folder/'exit.txt').write_text(str(rc)+'\n');assert rc in allowed,(name,rc)
 if name=='compile-normal':
  import re
  log=(folder/'stdout.log').read_text()+(folder/'stderr.log').read_text();assert 'BUILD SUCCESSFUL' in log and not re.search(r'^e: |Compilation error|Unresolved reference|^> Task .* FAILED$',log,re.M)
def get(url,name):
 time.sleep(.15)
 with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'ManualTransmissionResearch/1.0','Accept':'application/json'}),timeout=60) as r:
  b=r.read();assert r.status==200;js(OUT/(name+'.http.json'),{'url':url,'status':r.status,'headers':dict(r.headers)})
 save(OUT/name,b);return b
def metadata():
 c=json.loads(get(P['card_url'],'metadata/war.json'))
 for stem,url in [('printings',c['prints_search_uri']),('rulings',c['rulings_uri'])]:
  i=0;seen=set()
  while url:
   assert url not in seen;seen.add(url);d=json.loads(get(url,'metadata/'+stem+'-'+str(i)+'.json'));i+=1;assert i<20
   if d['has_more']:assert d.get('next_page')
   url=d['next_page'] if d['has_more'] else None
 with urllib.request.urlopen(urllib.request.Request(c['image_uris']['normal'],method='HEAD',headers={'User-Agent':'ManualTransmissionResearch/1.0'}),timeout=60) as r:
  assert r.status==200;js(OUT/'metadata/image.json',{'url':c['image_uris']['normal'],'status':r.status,'headers':dict(r.headers)})
 raw={str(f.relative_to(OUT)):f.read_bytes() for f in (OUT/'metadata').glob('*')}
 js(OUT/'metadata/VERIFICATION.json',verify(raw,(HERE/'ARCHIVED_WAR_METADATA.json').read_bytes(),(ROOT/P['snapshot']).read_bytes()))
def main():
 import zipfile,io
 OUT.mkdir();bind('initial');stage('compile-normal','compile')
 prior=ROOT/P['prior_record'];raw=(prior/'RECEIPT.json').read_bytes()
 assert git('hash-object',str(prior/'RECEIPT.json'))==P['prior_receipt_blob']
 d=json.loads(raw);assert d['run']==40982111046 and d['state']=='ORIGINAL_FAILURE_RECEIVED_NO_ADMISSION'
 assert set(e['gate'] for e in d['gate_errors'])=={'current-ir','collector_result','collector','workflow'}
 index=json.loads((prior/'ARCHIVE_PARTS.json').read_text());zraw=b''.join((prior/x['name']).read_bytes() for x in index['parts'])
 assert len(zraw)==P['original_zip_bytes'] and sha(zraw)==P['original_zip_sha256']
 with zipfile.ZipFile(io.BytesIO(zraw)) as z:
  assert z.testzip() is None
  manifest=json.loads(z.read('MANIFEST.json'))
  for name,digest in manifest.items():assert sha(z.read(name))==digest,name
  for name in ['current-ir/mtgish.lines.json','current-ir/mtgish.lines.json.http.json','current-ir/FBLTHP.json']:save(OUT/name,z.read(name))
 save(OUT/'PRIOR_RECEIPT.json',raw)
 ir=(OUT/'current-ir/mtgish.lines.json').read_bytes();assert sha(ir)==P['ir_sha256'];save(ROOT/'mtgish-tooling/data/mtgish.lines.json',ir)
 save(OUT/'check_stage.json',subprocess.check_output(['python3',str(HERE/'check_stage.py')]))
 stage('emit','current-ir');assert (ROOT/'mtgish-tooling/data/mtgish.lines.json').read_bytes()==ir
 bind('final');js(OUT/'RESULT.json',{'state':'MISSING_IR_COMMAND_COLLECTED_CLASSIFICATION_PENDING','feature_admitted':False,'card_admitted':False})
if __name__=='__main__':
 try:main()
 except Exception:OUT.mkdir(exist_ok=True);(OUT/'FAILURE.txt').write_text(traceback.format_exc());raise
 finally:
  try:bind('final')
  finally:
   OUT.mkdir(exist_ok=True);js(OUT/'MANIFEST.json',{str(f.relative_to(OUT)):sha(f.read_bytes()) for f in sorted(OUT.rglob('*')) if f.is_file() and f.name!='MANIFEST.json'})
