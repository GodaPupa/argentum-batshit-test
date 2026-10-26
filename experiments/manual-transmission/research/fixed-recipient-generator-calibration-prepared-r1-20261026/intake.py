"""Read-only calibration input freeze, mirroring pinned Kotlin Scryfall schema8; no qualification."""
import datetime,gzip,hashlib,json,re,subprocess,time,urllib.parse
from pathlib import Path
D=Path(__file__).resolve().parent;RAW=D/'raw';RAW.mkdir(exist_ok=True);(D/'inputs').mkdir(exist_ok=True)
sha=lambda b:hashlib.sha256(b).hexdigest()
manifest={}
def fetch(url,name):
    assert urllib.parse.urlparse(url).hostname=='api.scryfall.com'
    p=RAW/name
    if not p.exists():
        time.sleep(.15)
        subprocess.run(['curl','-fLsS','--max-time','30','-A','argentum-engine-card-status/1.0','-H','Accept: application/json',url,'-o',str(p)],check=True)
    b=p.read_bytes();j=json.loads(b);assert j.get('object')!='error'
    manifest[name]=dict(url=url,bytes=len(b),sha256=sha(b))
    return j
def front(n):return n.split(' // ',1)[0].strip()
def rank(p):
    n=p.get('collector_number') or '';m=re.match(r'^\d+',n)
    return (0 if p.get('booster') else 1,int(m.group()) if m else 2147483647,n)
def metadata(p):
    image=p.get('image_uris')
    if image is None:image=(p.get('card_faces') or [{}])[0].get('image_uris')
    uri=(image or {}).get('normal');uri=uri.split('?',1)[0] if uri else uri
    oracle=p.get('oracle_text')
    if oracle is None:oracle='\n//\n'.join(f.get('oracle_text') for f in p.get('card_faces',[]) if f.get('oracle_text')) or None
    return dict(rarity=p.get('rarity'),collector_number=p.get('collector_number'),artist=p.get('artist'),image_uri=uri,flavor_text=p.get('flavor_text'),color_identity=p.get('color_identity') or [],oracle_text=oracle)
summary={}
for code in ['por','isd']:
    setmeta=fetch('https://api.scryfall.com/sets/'+code,code+'-set.json');groups={};page=0
    url='https://api.scryfall.com/cards/search?q='+urllib.parse.quote('set:'+code+' -is:rebalanced',safe='')+'&unique=prints&order=name'
    while url:
        page+=1;j=fetch(url,f'{code}-page{page}.json')
        for card in j['data']:groups.setdefault(card['name'],[]).append(card)
        url=j.get('next_page') if j['has_more'] else None
    draft=[];extra=[];products={};cards={};standard=0
    for name,group in groups.items():
        if any(p.get('booster') for p in group):draft.append(name)
        else:extra.append(name);products[name]=sorted({t for p in group for t in p.get('promo_types',[])}) or ['other']
        representative=min(group,key=rank);standard+=representative.get('legalities',{}).get('standard')=='legal'
        cards.setdefault(front(name),metadata(representative))
    cache=dict(_v=8,released_at=setmeta['released_at'],set_type=setmeta['set_type'],draft_names=draft,extra_names=extra,extra_products=products,standard_legal_count=standard,cards=cards)
    body=(json.dumps(cache,ensure_ascii=False,indent=2)+'\n').encode();(D/'inputs'/f'{code}.json').write_bytes(body)
    summary[code]=dict(raw_printings=sum(map(len,groups.values())),names=len(groups),front_names=len({front(n) for n in groups}),sha256=sha(body),bytes=len(body),pages=page)
rawpack=(json.dumps({name:(RAW/name).read_text() for name in manifest},ensure_ascii=False,separators=(',',':'))+'\n').encode()
compressed=gzip.compress(rawpack,mtime=0);(D/'RAW_RESPONSES.json.gz').write_bytes(compressed)
record=dict(state='READ_ONLY_INPUT_FREEZE_NOT_QUALIFICATION',created_utc=datetime.datetime.now(datetime.timezone.utc).isoformat(),source='f54094584f283ace86035492b1566c16effd5be2',transform_source='mtgish-tooling/src/main/kotlin/com/wingedsheep/tooling/coverage/Scryfall.kt',transform_blob='a3381f8ee220ae62585724351f16416ab9eef12c',schema=8,comparison='Kotlin source schema8, including canonical grouping/printing order/metadata. Python scripts/card-status is schema9 and is not substituted.',raw=manifest,raw_pack_sha256=sha(compressed),raw_pack_bytes=len(compressed),sets=summary)
(D/'INPUTS.json').write_text(json.dumps(record,indent=2)+'\n');print(json.dumps(summary))
