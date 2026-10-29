"""Collect missing CLB binding and remaining card admission evidence; never admit a card."""
from pathlib import Path
import os,json,subprocess,hashlib,urllib.request,time,traceback
HERE=Path(__file__).resolve().parent;ROOT=Path.cwd();P=json.loads((HERE/'PROTOCOL.json').read_text());OUT=ROOT/P['evidence']
sha=lambda b:hashlib.sha256(b).hexdigest()
git=lambda *a:subprocess.check_output(['git',*a],text=True).strip()
def save(p,b):p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(b)
def js(p,v):save(p,(json.dumps(v,indent=2)+'\n').encode())
def bind(label):
 assert git('rev-parse',os.environ['SOURCE']+'^{tree}')==os.environ['SOURCE_TREE']
 assert git('rev-parse','HEAD^')==os.environ['SOURCE'] and git('rev-parse',os.environ['SOURCE']+'^')==P['base']
 assert git('diff','--name-only',os.environ['SOURCE'],'HEAD')==os.environ['WORKFLOW']
 assert git('diff','--name-only',P['base'],os.environ['SOURCE']).splitlines()==sorted(P['source_delta'])
 assert not git('diff','--name-only',P['dependent_source'],os.environ['SOURCE'],'--',':(exclude)experiments/manual-transmission/research')
 assert git('rev-parse','refs/remotes/origin/main')==P['main']
 assert not git('status','--porcelain','--untracked-files=no')
 if label=='initial':
  for f in HERE.iterdir():
   if f.is_file():save(OUT/'protocol'/f.name,f.read_bytes())
  save(OUT/'WORKFLOW.yml',(ROOT/os.environ['WORKFLOW']).read_bytes())
  js(OUT/'BINDING.json',dict(source=os.environ['SOURCE'],tree=os.environ['SOURCE_TREE'],validation=git('rev-parse','HEAD'),validation_tree=git('rev-parse','HEAD^{tree}'),workflow=os.environ['WORKFLOW'],workflow_blob=git('hash-object',os.environ['WORKFLOW']),run=os.environ['GITHUB_RUN_ID'],attempt=int(os.environ['GITHUB_RUN_ATTEMPT']),run_number=int(os.environ['GITHUB_RUN_NUMBER'])))
 for path,h in P['bindings'].items():
  b=(ROOT/path).read_bytes();assert sha(b)==h,path;save(OUT/label/'source'/path,b)
 save(OUT/label/'TRACKED.diff',subprocess.check_output(['git','diff','--']))
 clb=(ROOT/'mtg-sets/src/test/resources/snapshots/cards/CLB.json').read_bytes()
 assert clb==(ROOT/P['early_clb']).read_bytes()
 save(OUT/'EARLY_CLB.json',(ROOT/P['early_clb']).read_bytes())
 receipt=(ROOT/P['dependent_receipt']).read_bytes()
 assert git('hash-object',P['dependent_receipt'])==P['dependent_receipt_blob'];save(OUT/'DEPENDENT_RECEIPT.json',receipt)
 reuse={}
 for module in ['mtg-sdk','rules-engine/src/main','game-server/src/main','web-client','oracle-assay','mtgish-tooling','ai/src/main','gym/src/main']:
  a=git('rev-parse',P['code_source']+':'+module);b=git('rev-parse',os.environ['SOURCE']+':'+module);assert a==b,module;reuse[module]=a
 js(OUT/label/'UNCHANGED_TREES.json',reuse)

def stage(name,recipe,allowed=(0,)):
 folder=OUT/name;folder.mkdir(parents=True)
 with (folder/'stdout.log').open('wb') as out,(folder/'stderr.log').open('wb') as err:
  rc=subprocess.run(['just','--working-directory',str(ROOT),'--justfile',str(HERE/'validation.just'),recipe],stdout=out,stderr=err).returncode
 (folder/'exit.txt').write_text(str(rc)+'\n');assert rc in allowed,(name,rc)
def get(url,name):
 time.sleep(.15);req=urllib.request.Request(url,headers={'User-Agent':'ManualTransmissionResearch/1.0','Accept':'application/json'})
 with urllib.request.urlopen(req,timeout=60) as r:b=r.read();status=r.status
 save(OUT/'metadata'/name,b);return json.loads(b),status
def metadata():
 common=['name','mana_cost','type_line','oracle_text','power','toughness','colors','color_identity','oracle_id','id','rarity','collector_number','artist','flavor_text','released_at']
 images={};cards={}
 for setcode in ['nph','clb']:
  c,status=get('https://api.scryfall.com/cards/named?exact=Spellskite&set='+setcode,setcode+'.json');assert status==200
  old=json.loads((ROOT/P['metadata_base']/(setcode+'.json')).read_text())
  for k in common:assert c.get(k)==old.get(k),(setcode,k,c.get(k),old.get(k))
  assert c['image_uris']['normal']==old['image_uris']['normal']
  with urllib.request.urlopen(urllib.request.Request(c['image_uris']['normal'],method='HEAD',headers={'User-Agent':'ManualTransmissionResearch/1.0'}),timeout=60) as r:assert r.status==200;images[setcode]={'url':c['image_uris']['normal'],'status':r.status,'headers':dict(r.headers)}
  cards[setcode]=c
 r,status=get(cards['nph']['rulings_uri'],'rulings.json');assert not r.get('has_more');assert status==200
 golden=(ROOT/'mtg-sets/src/test/resources/snapshots/cards/NPH.json').read_text().split('// Spellskite\n')[1].split('\n// ')[0];g=json.loads(golden)
 assert [(x['published_at'],x['comment']) for x in r['data']]==[(x['date'],x['text']) for x in g['metadata']['rulings']]
 c=cards['nph']
 for a,b in [('name','name'),('manaCost','mana_cost'),('typeLine','type_line'),('oracleText','oracle_text')]:assert g[a]==c[b]
 assert g['creatureStats']=={'power':int(c['power']),'toughness':int(c['toughness'])}
 for a,b in [('collectorNumber','collector_number'),('artist','artist'),('flavorText','flavor_text')]:assert g['metadata'][a]==c[b]
 assert g['metadata']['rarity']==c['rarity'].upper() and g['metadata']['imageUri']==c['image_uris']['normal']
 url=c['prints_search_uri'];pages=[];i=0
 while url:
  d,status=get(url,'printings-'+str(i)+'.json');assert status==200;pages+=d['data'];i+=1;assert i<20;url=d.get('next_page') if d.get('has_more') else None
 assert {cards[s]['id'] for s in cards} <= {x['id'] for x in pages}
 cache=Path.home()/'.cache/scryfall/printings/spellskite.json'
 if cache.exists():cache.unlink() # transient cache only; force unchanged printing gate to fetch current data
 js(OUT/'metadata'/'VERIFICATION.json',{'fields':common,'printings':len(pages),'pages':i,'rulings':len(r['data']),'images':images,'pagination_complete':True})
def main():
 OUT.mkdir();bind('initial');stage('compile-normal','compile');metadata();stage('printing','printing')
 cache=Path.home()/'.cache/scryfall/printings/spellskite.json';save(OUT/'metadata'/'printing-check-cache.json',cache.read_bytes())
 stage('corpus','corpus')
 bulk=Path.home()/'.cache/scryfall/_bulk-oracle-cards.jsonl.gz';save(OUT/'corpus'/'oracle-cards.jsonl.gz',bulk.read_bytes())
 for name in ['parse','explain','assay-compile','assay-nph','assay-clb']:stage(name,name,(0,1) if name=='assay-compile' else (0,))
 assert sha(bulk.read_bytes())==sha((OUT/'corpus'/'oracle-cards.jsonl.gz').read_bytes())
 bind('final')
if __name__=='__main__':
 try:main()
 except Exception:OUT.mkdir(exist_ok=True);(OUT/'FAILURE.txt').write_text(traceback.format_exc());raise
 finally:
  try:bind('final')
  finally:
   OUT.mkdir(exist_ok=True);js(OUT/'MANIFEST.json',{str(f.relative_to(OUT)):sha(f.read_bytes()) for f in sorted(OUT.rglob('*')) if f.is_file() and f.name!='MANIFEST.json'})
