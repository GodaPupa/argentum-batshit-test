from pathlib import Path
import os,json,hashlib,subprocess,urllib.request,time,traceback
HERE=Path(__file__).resolve().parent;P=json.loads((HERE/'PROTOCOL.json').read_text());OUT=Path(P['evidence'])
sha=lambda b:hashlib.sha256(b).hexdigest()
git=lambda *a:subprocess.check_output(['git',*a],text=True).strip()
def save(n,b):
 p=OUT/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(b)
def js(n,d):save(n,(json.dumps(d,indent=2)+'\n').encode())
def get(u,n):
 time.sleep(.15)
 with urllib.request.urlopen(urllib.request.Request(u,headers={'User-Agent':'ManualTransmissionResearch/1.0','Accept':'application/json'}),timeout=60) as r:
  b=r.read();assert r.status==200;js(n+'.http.json',{'url':u,'status':r.status,'headers':dict(r.headers)})
 save(n,b);return b
def main():
 OUT.mkdir()
 assert git('rev-parse','HEAD^')==os.environ['SOURCE']
 assert git('rev-parse',os.environ['SOURCE']+'^{tree}')==os.environ['SOURCE_TREE']
 assert git('rev-parse',os.environ['SOURCE']+'^')==P['base']
 assert git('diff','--name-only',os.environ['SOURCE'],'HEAD')==os.environ['WORKFLOW']
 assert not git('status','--porcelain','--untracked-files=no')
 assert git('rev-parse','origin/main')==P['main']
 for f in HERE.iterdir():
  if f.is_file():save('protocol/'+f.name,f.read_bytes())
 save('WORKFLOW.yml',Path(os.environ['WORKFLOW']).read_bytes())
 js('BINDING.json',{'source':os.environ['SOURCE'],'tree':os.environ['SOURCE_TREE'],'validation':git('rev-parse','HEAD'),'validation_tree':git('rev-parse','HEAD^{tree}'),'workflow':os.environ['WORKFLOW'],'workflow_blob':git('hash-object',os.environ['WORKFLOW']),'run':os.environ['GITHUB_RUN_ID'],'attempt':int(os.environ['GITHUB_RUN_ATTEMPT']),'run_number':int(os.environ['GITHUB_RUN_NUMBER'])})
 c=json.loads(get(P['card_url'],'metadata/war.json'))
 assert c['name']=='Fblthp, the Lost' and c['set']=='war' and c['collector_number']=='50'
 assert c['mana_cost']=='{1}{U}' and c['power']=='1' and c['toughness']=='1'
 def pages(u,stem):
  rows=[];i=0
  while u:
   d=json.loads(get(u,'metadata/'+stem+'-'+str(i)+'.json'));rows+=d['data'];i+=1
   assert i<20
   u=d.get('next_page') if d.get('has_more') else None
  return rows,i
 prints,np=pages(c['prints_search_uri'],'printings');rulings,nr=pages(c['rulings_uri'],'rulings')
 real=[x for x in prints if x['set_type'] not in ['promo','token','art_series','memorabilia']]
 first=min(x['released_at'] for x in real);assert c['released_at']==first
 assert any(x['id']==c['id'] for x in prints)
 with urllib.request.urlopen(urllib.request.Request(c['image_uris']['normal'],method='HEAD',headers={'User-Agent':'ManualTransmissionResearch/1.0'}),timeout=60) as r:
  assert r.status==200;js('metadata/image.json',{'url':c['image_uris']['normal'],'status':r.status,'headers':dict(r.headers)})
 rules=get(P['rules_url'],'rules.txt');assert b'September 25, 2026' in rules
 save('FINAL_TRACKED.diff',subprocess.check_output(['git','diff','--']));assert not git('status','--porcelain','--untracked-files=no')
 js('RESULT.json',{'state':'COLLECTED_NOT_RECEIVED','printings':len(prints),'printing_pages':np,'rulings':len(rulings),'ruling_pages':nr,'earliest_release':first,'games':0,'seeds':0})
if __name__=='__main__':
 try:main()
 except Exception:
  OUT.mkdir(exist_ok=True);save('FAILURE.txt',traceback.format_exc().encode());raise
 finally:
  OUT.mkdir(exist_ok=True);js('MANIFEST.json',{str(f.relative_to(OUT)):sha(f.read_bytes()) for f in sorted(OUT.rglob('*')) if f.is_file() and f.name!='MANIFEST.json'})
