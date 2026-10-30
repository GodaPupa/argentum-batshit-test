"""Exact WAR50 source-metadata comparison; no normalization or admission decision."""
import json,hashlib
sha=lambda b:hashlib.sha256(b).hexdigest()
FIELDS=['name','mana_cost','type_line','oracle_text','power','toughness','colors','color_identity','oracle_id','id','rarity','collector_number','artist','flavor_text','released_at','set']
def pages(raw,stem,url):
 result=[];i=0;seen=set()
 while True:
  assert url not in seen;seen.add(url)
  http=json.loads(raw[f'metadata/{stem}-{i}.json.http.json']);assert http['status']==200 and http['url']==url
  d=json.loads(raw[f'metadata/{stem}-{i}.json']);assert d['object']=='list' and isinstance(d['data'],list)
  result+=d['data'];i+=1
  if not d['has_more']:break
  assert d.get('next_page') and i<20;url=d['next_page']
 assert not any(n.startswith('metadata/'+stem+'-') and n.endswith('.json') and '.http.' not in n and int(n.rsplit('-',1)[1][:-5])>=i for n in raw)
 return result,i
def verify(raw,archived,snapshot):
 c=json.loads(raw['metadata/war.json']);old=json.loads(archived)
 assert c['name']=='Fblthp, the Lost' and c['set']=='war' and c['collector_number']=='50'
 for k in FIELDS:assert (k in c)==(k in old) and c.get(k)==old.get(k),(k,c.get(k),old.get(k))
 assert c['image_uris']['normal']==old['image_uris']['normal']
 text=snapshot.decode();assert text.count('// Fblthp, the Lost\n')==1
 g=json.loads(text.split('// Fblthp, the Lost\n')[1].split('\n// ')[0])
 for a,b in [('name','name'),('manaCost','mana_cost'),('typeLine','type_line'),('oracleText','oracle_text')]:assert g[a]==c[b],a
 assert g['creatureStats']=={'power':int(c['power']),'toughness':int(c['toughness'])}
 assert c['colors']==c['color_identity']==['U'] and g['colorIdentityOverride']==['BLUE']
 for a,b in [('collectorNumber','collector_number'),('artist','artist')]:assert g['metadata'][a]==c[b],a
 if 'flavor_text' in c:assert g['metadata']['flavorText']==c['flavor_text']
 else:assert 'flavorText' not in g['metadata']
 assert g['metadata']['rarity']==c['rarity'].upper() and g['metadata']['imageUri']==c['image_uris']['normal']
 rulings,nr=pages(raw,'rulings',c['rulings_uri']);assert [(x['published_at'],x['comment']) for x in rulings]==[(x['date'],x['text']) for x in g['metadata']['rulings']]
 prints,np=pages(raw,'printings',c['prints_search_uri']);assert prints and len({p['id'] for p in prints})==len(prints)
 assert json.loads(raw['metadata/printings-0.json'])['total_cards']==len(prints)
 assert all(p['oracle_id']==c['oracle_id'] and p['name']==c['name'] for p in prints)
 assert c['id'] in {p['id'] for p in prints}
 real=[p for p in prints if p['set_type'] not in ['promo','token','art_series','memorabilia']]
 assert real and c['released_at']==min(p['released_at'] for p in real)
 image=json.loads(raw['metadata/image.json']);assert image['status']==200 and image['url']==c['image_uris']['normal']
 return {'fields':FIELDS,'printings':len(prints),'printing_pages':np,'rulings':len(rulings),'ruling_pages':nr,'image':image,'snapshot_sha256':sha(snapshot),'pagination_complete':True}
