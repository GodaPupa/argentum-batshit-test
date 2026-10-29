from pathlib import Path
import json,re,hashlib,importlib.util
H=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('bootstrap',H/'bootstrap_audit.py');mod=importlib.util.module_from_spec(spec);spec.loader.exec_module(mod)
sha=lambda b:hashlib.sha256(b).hexdigest()
deck=(H/'inputs'/mod.DECK).read_text();cmd=[];main=[];section=''
for line in deck.splitlines():
 if line in ('Commander','Creatures','Noncreatures','Lands'):section=line
 m=re.fullmatch(r'(\d+) (.+)',line)
 if m:(cmd if section=='Commander' else main).extend([m[2]]*int(m[1]))
assert len(cmd)==1 and len(main)==99
ad=json.loads((H/'inputs/admission.json').read_text());ad={x['id']:x for x in ad['decks']}
decks=[dict(id='manual-transmission-v07',commanders=cmd,mainboard=main)];sources={}
for axis in mod.AXES:
 p=H/'inputs'/f'experiments/manual-transmission/opponents/{axis}/source-freeze.json';o=json.loads(p.read_text());c,m,d=mod.source_cards(o,axis)
 assert c==ad[axis]['commanders'] and ad[axis]['complete_100_sha256'] in d.values() and ad[axis]['disposition']=='ADMITTED_EXACT_IDENTITY'
 decks.append(dict(id=axis,commanders=c,mainboard=m));sources[axis]=dict(sha256=sha(p.read_bytes()),digests=d)
req=dict(schema='mt-phase2-registry-requests-v1',phase1_commit=mod.PHASE1,decks=decks)
(H/'REQUEST.json').write_text(json.dumps(req,sort_keys=True,indent=2,ensure_ascii=False)+'\n')
(H/'INPUT_PROVENANCE.json').write_text(json.dumps(dict(phase1=mod.PHASE1,source_derivation='Unchanged bootstrap_audit.source_cards plus exact existing deck section parser. No substitutions or random values.',source_blobs={str(p.relative_to(H/'inputs')):hashlib.sha1(b'blob '+str(len(p.read_bytes())).encode()+b'\0'+p.read_bytes()).hexdigest() for p in (H/'inputs').rglob('*') if p.is_file()},sources=sources,request_sha256=sha((H/'REQUEST.json').read_bytes()),physical_cards=800,official_games=0,official_seeds=0),indent=2)+'\n')
print('Exact frozen8decks800cards request derived')
