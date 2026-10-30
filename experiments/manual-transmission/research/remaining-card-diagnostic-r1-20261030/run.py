"""Five-card diagnostic only. No registry, production, fixture or snapshot changes."""
from pathlib import Path
import hashlib, io, json, os, re, subprocess, time, traceback, urllib.parse, urllib.request, zipfile
from audit import verify
HERE = Path(__file__).resolve().parent
ROOT = Path.cwd()
P = json.loads((HERE / 'PROTOCOL.json').read_text())
OUT = ROOT / P['evidence']
sha = lambda b: hashlib.sha256(b).hexdigest()
git = lambda *args: subprocess.check_output(['git', *args], text=True).strip()
def save(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)
def js(path, value):
    save(path, (json.dumps(value, indent=2) + '\n').encode())
def bind(label):
    assert P['state'] == 'FROZEN_SOURCE_ONLY_DIAGNOSTIC_NO_ADMISSION'
    assert git('rev-parse', 'HEAD^') == os.environ['SOURCE']
    assert git('rev-parse', os.environ['SOURCE'] + '^') == P['base']
    assert git('rev-parse', P['base'] + '^{tree}') == P['base_tree']
    assert git('rev-parse', os.environ['SOURCE'] + '^{tree}') == os.environ['SOURCE_TREE']
    assert git('diff', '--name-only', os.environ['SOURCE'], 'HEAD') == os.environ['WORKFLOW']
    assert git('diff', '--name-only', P['base'], os.environ['SOURCE']).splitlines() == P['source_delta']
    assert not git('status', '--porcelain', '--untracked-files=no')
    assert git('rev-parse', 'refs/remotes/origin/main') == P['main']
    for path, digest in P['bindings'].items():
        data = (ROOT / path).read_bytes()
        assert sha(data) == digest, path
        save(OUT / label / 'source' / path, data)
    for path, digest in P['git_bindings'].items():
        assert git('hash-object', path) == digest, path
        save(OUT / label / 'source' / path, (ROOT / path).read_bytes())
    trees = {}
    for module in P['unchanged_modules']:
        a = git('rev-parse', P['base'] + ':' + module)
        assert a == git('rev-parse', os.environ['SOURCE'] + ':' + module), module
        trees[module] = a
    js(OUT / label / 'UNCHANGED_TREES.json', trees)
    save(OUT / label / 'TRACKED.diff', subprocess.check_output(['git', 'diff', '--']))
    if label == 'initial':
        for f in HERE.iterdir():
            if f.is_file(): save(OUT / 'protocol' / f.name, f.read_bytes())
        save(OUT / 'WORKFLOW.yml', (ROOT / os.environ['WORKFLOW']).read_bytes())
        js(OUT / 'BINDING.json', dict(source=os.environ['SOURCE'], tree=os.environ['SOURCE_TREE'], validation=git('rev-parse','HEAD'), validation_tree=git('rev-parse','HEAD^{tree}'), workflow=os.environ['WORKFLOW'], workflow_blob=git('hash-object',os.environ['WORKFLOW']), run=os.environ['GITHUB_RUN_ID'], attempt=int(os.environ['GITHUB_RUN_ATTEMPT']), run_number=int(os.environ['GITHUB_RUN_NUMBER'])))
def stage(name, recipe, allowed=(0,)):
    folder = OUT / 'commands' / name
    folder.mkdir(parents=True)
    with (folder/'stdout.log').open('wb') as out, (folder/'stderr.log').open('wb') as err:
        rc = subprocess.run(['just','--working-directory',str(ROOT),'--justfile',str(HERE/'validation.just'),recipe],stdout=out,stderr=err).returncode
    (folder/'exit.txt').write_text(str(rc)+'\n')
    assert rc in allowed, (name,rc)
    return rc
def get(url, name):
    time.sleep(.15)
    request = urllib.request.Request(url,headers={'User-Agent':'ManualTransmissionResearch/1.0','Accept':'application/json'})
    with urllib.request.urlopen(request,timeout=90) as response:
        data=response.read()
        assert response.status==200
        js(OUT/(name+'.http.json'),dict(url=url,status=response.status,headers=dict(response.headers)))
    save(OUT/name,data)
    return json.loads(data)
def metadata(card):
    stem='metadata/'+card['slug']+'/'
    url='https://api.scryfall.com/cards/named?exact='+urllib.parse.quote(card['name'])+'&set='+card['set']
    c=get(url,stem+'card.json')
    for kind, url in [('printings',c['prints_search_uri']),('rulings',c['rulings_uri'])]:
        page=0;seen=set()
        while url:
            assert url not in seen and page<30
            seen.add(url)
            data=get(url,stem+kind+'-'+str(page)+'.json')
            page+=1
            url=data.get('next_page') if data['has_more'] else None
            assert not data['has_more'] or url
    images=c.get('image_uris')
    urls=[images['normal']] if images else [face['image_uris']['normal'] for face in c['card_faces']]
    for i,url in enumerate(urls):
        with urllib.request.urlopen(urllib.request.Request(url,method='HEAD',headers={'User-Agent':'ManualTransmissionResearch/1.0'}),timeout=90) as response:
            assert response.status==200
            js(OUT/(stem+'image-'+str(i)+'.json'),dict(url=url,status=response.status,headers=dict(response.headers)))
def source_inventory():
    files=git('ls-files','mtg-sdk','rules-engine','mtg-sets','game-server','web-client').splitlines()
    files=[p for p in files if p.endswith(('.kt','.ts','.tsx'))]
    terms={term:re.compile(term,re.I) for term in P['terms']}
    inventory=[];hits={term:[] for term in terms};names={c['name']:[] for c in P['cards']}
    for path in files:
        raw=(ROOT/path).read_bytes();text=raw.decode();lines=text.splitlines()
        inventory.append(dict(path=path,sha256=sha(raw),git_blob=hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()))
        matched=False
        for term,rx in terms.items():
            rows=[dict(line=i+1,text=line) for i,line in enumerate(lines) if rx.search(line)]
            if rows:hits[term].append(dict(path=path,rows=rows));matched=True
        for name in names:
            rows=[dict(line=i+1,text=line) for i,line in enumerate(lines) if name in line]
            if rows:names[name].append(dict(path=path,rows=rows));matched=True
        if matched:save(OUT/'source-matches'/path,raw)
    js(OUT/'SOURCE_INVENTORY.json',dict(files=inventory,terms=hits,names=names,interpretation='Text/source inventory only; no behavioral or absence-of-composition proof.'))
def restore_ir():
    record=ROOT/P['reuse']['ir_archive_record']
    index=json.loads((record/'ARCHIVE_PARTS.json').read_text())
    raw=b''.join((record/item['name']).read_bytes() for item in index['parts'])
    assert sha(raw)==P['reuse']['ir_archive_sha256']
    with zipfile.ZipFile(io.BytesIO(raw)) as archive:
        assert archive.testzip() is None
        data=archive.read('current-ir/mtgish.lines.json')
    assert sha(data)==P['reuse']['ir_sha256']
    save(OUT/'input/mtgish.lines.json',data)
    save(ROOT/'mtgish-tooling/data/mtgish.lines.json',data)
    rows=[json.loads(line) for line in data.decode().splitlines() if line.strip()]
    for card in P['cards']:
        matched=[r for r in rows if r.get('Name','').split(' // ')[0]==card['name']]
        js(OUT/'input'/('ir-'+card['slug']+'.json'),dict(name=card['name'],matches=matched,count=len(matched),absence_is_not_engine_gap=True))
def main():
    OUT.mkdir();bind('initial')
    save(OUT/'CHECK_AUDIT.json',subprocess.check_output(['python3',str(HERE/'check_audit.py')]))
    source_inventory();stage('compile-normal','compile')
    restore_ir()
    for card in P['cards']:
        metadata(card)
        for action in ['parse','explain','assay-compile','emit']:
            stage(card['slug']+'/'+action,card['slug']+'-'+action,(0,1) if action in ['assay-compile','emit'] else (0,))
    cache=Path.home()/'.cache/scryfall'
    if cache.exists():
        for path in sorted(cache.rglob('*')):
            if path.is_file():save(OUT/'scryfall-cache'/path.relative_to(cache),path.read_bytes())
    assert sha((ROOT/'mtgish-tooling/data/mtgish.lines.json').read_bytes())==P['reuse']['ir_sha256']
    bind('final')
    js(OUT/'AUDIT.json',verify({str(f.relative_to(OUT)):f.read_bytes() for f in OUT.rglob('*') if f.is_file()},P))
    js(OUT/'RESULT.json',dict(state='DIAGNOSTIC_COLLECTED_ORIGINAL_RECEIVING_REQUIRED',feature_admitted=False,card_admitted=False))
if __name__=='__main__':
    try:main()
    except Exception:
        OUT.mkdir(exist_ok=True);(OUT/'FAILURE.txt').write_text(traceback.format_exc());raise
    finally:
        try:bind('final')
        finally:
            OUT.mkdir(exist_ok=True)
            js(OUT/'MANIFEST.json',{str(f.relative_to(OUT)):sha(f.read_bytes()) for f in sorted(OUT.rglob('*')) if f.is_file() and f.name!='MANIFEST.json'})
