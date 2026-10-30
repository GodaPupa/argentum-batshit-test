"""Validate raw diagnostic collection; never admits card behavior."""
import hashlib,json,re
def verify(raw,p):
    def read(name):
        assert name in raw,name
        return raw[name]
    def js(name):return json.loads(read(name))
    log=read('commands/compile-normal/stdout.log').decode()+read('commands/compile-normal/stderr.log').decode()
    assert read('commands/compile-normal/exit.txt').strip()==b'0'
    assert 'BUILD SUCCESSFUL' in log
    assert not re.search(r'^e: |Compilation error|Unresolved reference|^> Task .* FAILED$',log,re.M)
    assert hashlib.sha256(read('input/mtgish.lines.json')).hexdigest()==p['reuse']['ir_sha256']
    inventory=js('SOURCE_INVENTORY.json')
    assert inventory['files'] and len({x['path'] for x in inventory['files']})==len(inventory['files'])
    assert set(inventory['terms'])==set(p['terms'])
    assert set(inventory['names'])=={c['name'] for c in p['cards']}
    for entry in inventory['files']:
        key='source-matches/'+entry['path']
        if key in raw:assert hashlib.sha256(raw[key]).hexdigest()==entry['sha256']
    for phase in ['initial','final']:
        assert read(phase+'/TRACKED.diff')==b''
        assert set(js(phase+'/UNCHANGED_TREES.json'))==set(p['unchanged_modules'])
        for path,digest in p['bindings'].items():assert hashlib.sha256(read(phase+'/source/'+path)).hexdigest()==digest,path
        for path,digest in p['git_bindings'].items():
            b=read(phase+'/source/'+path)
            assert hashlib.sha1(b'blob '+str(len(b)).encode()+b'\0'+b).hexdigest()==digest,path
    assert js('initial/UNCHANGED_TREES.json')==js('final/UNCHANGED_TREES.json')
    summaries=[]
    for card in p['cards']:
        prefix='metadata/'+card['slug']+'/'
        c=js(prefix+'card.json')
        assert c['object']=='card' and c['set']==card['set']
        assert c['name'].split(' // ')[0]==card['name']
        assert c.get('oracle_id') and c.get('collector_number') and c.get('released_at')
        assert js(prefix+'card.json.http.json')['status']==200
        arrays={}
        for kind in ['printings','rulings']:
            data=[];i=0;expected=c['prints_search_uri'] if kind=='printings' else c['rulings_uri']
            while True:
                key=prefix+kind+'-'+str(i)+'.json'
                page=js(key);http=js(key+'.http.json')
                assert http['status']==200 and http['url']==expected
                assert page['object']=='list' and isinstance(page['data'],list)
                data+=page['data'];i+=1
                assert i<31
                if not page['has_more']:break
                expected=page['next_page']
            actual={key for key in raw if re.fullmatch(re.escape(prefix+kind)+r'-\d+\.json',key)}
            assert actual=={prefix+kind+'-'+str(n)+'.json' for n in range(i)}
            arrays[kind]=data
        prints=arrays['printings'];assert prints
        assert len({x['id'] for x in prints})==len(prints)
        assert all(x['oracle_id']==c['oracle_id'] for x in prints)
        assert c['id'] in {x['id'] for x in prints}
        real=[x for x in prints if x.get('set_type') not in ['promo','token','art_series','memorabilia'] and not x.get('digital',False)]
        assert real
        earliest=min(x['released_at'] for x in real)
        assert c['released_at']==earliest,(card['name'],earliest,c['released_at'])
        assert all(x.get('oracle_id')==c['oracle_id'] for x in arrays['rulings'])
        urls=[c['image_uris']['normal']] if c.get('image_uris') else [f['image_uris']['normal'] for f in c['card_faces']]
        for i,url in enumerate(urls):
            image=js(prefix+'image-'+str(i)+'.json');assert image['status']==200 and image['url']==url
        codes={}
        for action in ['parse','explain','assay-compile','emit']:
            stem='commands/'+card['slug']+'/'+action+'/'
            rc=int(read(stem+'exit.txt'));codes[action]=rc
            allowed=(0,1) if action in ['assay-compile','emit'] else (0,)
            assert rc in allowed
            output=read(stem+'stdout.log')+read(stem+'stderr.log')
            assert output.strip(),stem
            assert not re.search(rb'Unresolved reference|Compilation error|Exception in thread|Traceback \(most recent call last\)',output)
        ir=js('input/ir-'+card['slug']+'.json');assert ir['name']==card['name'] and ir['count']==len(ir['matches'])
        summaries.append(dict(name=card['name'],set=c['set'],collector_number=c['collector_number'],oracle_id=c['oracle_id'],earliest=earliest,printings=len(prints),rulings=len(arrays['rulings']),image_faces=len(urls),ir_rows=ir['count'],command_exit_codes=codes))
    return dict(state='RAW_DIAGNOSTIC_STRUCTURE_VERIFIED_NO_BEHAVIORAL_ACCEPTANCE',cards=summaries,source_files_scanned=len(inventory['files']),sha_bindings=len(p['bindings']),git_bindings=len(p['git_bindings']),feature_admitted=False,card_admitted=False)
