"""Read-only Scryfall intake for later card work; never registers cards or claims semantics."""
from pathlib import Path
from datetime import datetime, timezone
import hashlib, json, time, traceback, urllib.parse, urllib.request
out=Path('entry-snapshot-evidence/source-intake');out.mkdir(parents=True,exist_ok=True)
cards=[('Phyrexian Metamorph','nph'),('Fblthp, the Lost','war'),('Reckless Bushwhacker','ogw'),
       ('Amphin Mutineer','cmr'),('Spellskite','nph'),('Disciple of Freyalise','mh3'),
       ('Veil of Summer','m20'),('Opal Palace','c13')]
report={'retrieved_at':datetime.now(timezone.utc).isoformat(),'purpose':'Read-only preparation; no card or competitive admission','cards':[]}
def fetch(url,path):
    time.sleep(0.15) # Respect the source service's request pacing; no build or game is dispatched.
    request=urllib.request.Request(url,headers={'User-Agent':'Argentum-Manual-Transmission-Source-Audit/1.0','Accept':'application/json'})
    with urllib.request.urlopen(request,timeout=40) as response:
        data=response.read();status=response.status
    path.write_bytes(data)
    return json.loads(data),{'url':url,'file':str(path.relative_to(out)),'bytes':len(data),'sha256':hashlib.sha256(data).hexdigest(),'http_status':status}
for name,requested_set in cards:
    item={'name':name,'requested_set':requested_set,'status':'NOT_RECEIVED','files':[]};report['cards'].append(item)
    slug=requested_set+'-'+name.lower().replace(',','').replace(' ','-')
    try:
        card,meta=fetch('https://api.scryfall.com/cards/named?'+urllib.parse.urlencode({'exact':name,'set':requested_set}),out/(slug+'.json'));item['files'].append(meta)
        assert card['object']=='card' and card['set']==requested_set
        assert card['name']==name or any(f.get('name')==name for f in card.get('card_faces',[]))
        item.update(id=card['id'],oracle_id=card.get('oracle_id'),collector_number=card['collector_number'])
        rulings,meta=fetch(card['rulings_uri'],out/(slug+'-rulings.json'));item['files'].append(meta)
        assert rulings['object']=='list' and not rulings.get('has_more')
        printings=[];url=card['prints_search_uri'];page=0
        while url:
            assert url.startswith('https://api.scryfall.com/')
            data,meta=fetch(url,out/(slug+'-printings-'+str(page)+'.json'));item['files'].append(meta)
            assert data['object']=='list';printings.extend(data['data']);page+=1
            url=data.get('next_page') if data.get('has_more') else None
            assert page<=20, 'Unexpected pagination; stop without guessing canonical placement'
        real=[p for p in printings if p.get('set_type') not in {'promo','token','art_series','memorabilia'}]
        assert real
        first=min(real,key=lambda p:(p['released_at'],p['set'],p['collector_number']))
        item['earliest_nonexcluded_printing']={k:first.get(k) for k in ['name','id','set','set_name','set_type','released_at','collector_number']}
        image=card.get('image_uris',{}).get('normal') or card.get('card_faces',[{}])[0].get('image_uris',{}).get('normal')
        assert image and image.startswith('https://cards.scryfall.io/')
        with urllib.request.urlopen(urllib.request.Request(image,method='HEAD',headers={'User-Agent':'Argentum-Source-Audit/1.0'}),timeout=40) as response:
            item['image_head']={'url':image,'status':response.status}
        item['status']='PRIMARY_JSON_RECEIVED_REQUIRES_HUMAN_MODEL_REVIEW'
    except Exception as exc:
        item['status']='INTAKE_INCOMPLETE';item['error']=repr(exc)
        (out/(slug+'-error.txt')).write_text(traceback.format_exc())
    (out/'INTAKE.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps({'source_intake':[{'name':i['name'],'status':i['status']} for i in report['cards']],
                  'qualification_effect':'None; this is separate preparation, never a test waiver or card admission'},indent=2))
