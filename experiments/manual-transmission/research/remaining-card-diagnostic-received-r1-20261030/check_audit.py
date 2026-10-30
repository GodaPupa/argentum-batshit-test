"""Executable synthetic receiver controls; not engine/card behavioral evidence."""
from pathlib import Path
import ast,copy,hashlib,json
from audit import verify
H=Path(__file__).resolve().parent
for name in ['run.py','audit.py','RECEIVE.py']:ast.parse((H/name).read_text())
p=json.loads((H/'PROTOCOL.json').read_text());p=copy.deepcopy(p)
p['bindings']={};p['git_bindings']={};p['reuse']['ir_sha256']=hashlib.sha256(b'ir').hexdigest()
raw={'input/mtgish.lines.json':b'ir','commands/compile-normal/exit.txt':b'0\n','commands/compile-normal/stdout.log':b'BUILD SUCCESSFUL\n','commands/compile-normal/stderr.log':b''}
def js(name,value):raw[name]=json.dumps(value).encode()
js('SOURCE_INVENTORY.json',dict(files=[dict(path='fixture.kt',sha256=hashlib.sha256(b'x').hexdigest())],terms={x:[] for x in p['terms']},names={c['name']:[] for c in p['cards']}))
for phase in ['initial','final']:
    raw[phase+'/TRACKED.diff']=b''
    js(phase+'/UNCHANGED_TREES.json',{m:'a'*40 for m in p['unchanged_modules']})
for i,card in enumerate(p['cards']):
    prefix='metadata/'+card['slug']+'/'
    c=dict(object='card',id='card'+str(i),name=card['name'],set=card['set'],oracle_id='oracle'+str(i),collector_number='1',released_at='2000-01-01',set_type='expansion',digital=False,prints_search_uri='https://prints/'+str(i),rulings_uri='https://rulings/'+str(i),image_uris={'normal':'https://image/'+str(i)})
    js(prefix+'card.json',c);js(prefix+'card.json.http.json',dict(status=200))
    for kind in ['printings','rulings']:
        js(prefix+kind+'-0.json',dict(object='list',data=[c] if kind=='printings' else [],has_more=False))
        js(prefix+kind+'-0.json.http.json',dict(status=200,url=c['prints_search_uri'] if kind=='printings' else c['rulings_uri']))
    js(prefix+'image-0.json',dict(status=200,url=c['image_uris']['normal']))
    js('input/ir-'+card['slug']+'.json',dict(name=card['name'],count=0,matches=[]))
    for action in ['parse','explain','assay-compile','emit']:
        stem='commands/'+card['slug']+'/'+action+'/'
        raw[stem+'exit.txt']=b'0';raw[stem+'stdout.log']=b'collected diagnostic';raw[stem+'stderr.log']=b''
assert len(verify(raw,p)['cards'])==5
rejections=[]
def reject(name,mutate):
    bad=copy.deepcopy(raw);mutate(bad)
    try:verify(bad,p)
    except (AssertionError,KeyError,ValueError):rejections.append(name)
    else:raise AssertionError('Mutation accepted: '+name)
prefix='metadata/'+p['cards'][0]['slug']+'/'
reject('missing compile',lambda d:d.pop('commands/compile-normal/stdout.log'))
reject('compiler failure',lambda d:d.__setitem__('commands/compile-normal/stdout.log',b'BUILD SUCCESSFUL\nUnresolved reference'))
reject('changed IR',lambda d:d.__setitem__('input/mtgish.lines.json',b'changed'))
reject('changed source',lambda d:d.__setitem__('final/TRACKED.diff',b'changed'))
reject('unreceived page',lambda d:d.__setitem__(prefix+'printings-0.json',json.dumps(dict(object='list',data=[],has_more=True,next_page='next')).encode()))
reject('wrong printing',lambda d:d.__setitem__(prefix+'card.json',json.dumps(dict(json.loads(d[prefix+'card.json']),set='wrong')).encode()))
reject('wrong image',lambda d:d.__setitem__(prefix+'image-0.json',json.dumps(dict(status=404,url='wrong')).encode()))
reject('missing command',lambda d:d.pop('commands/'+p['cards'][0]['slug']+'/emit/stdout.log'))
print(json.dumps(dict(synthetic_only=True,normal_cards=5,rejected=rejections,engine_or_card_acceptance=False)))
