"""Receive the original read-only intake; this cannot admit code or a card."""
import hashlib, io, json, pathlib, sys, zipfile

def git_blob(b):
    return hashlib.sha1(b'blob '+str(len(b)).encode()+b'\0'+b).hexdigest()

def receive(archive):
    raw = pathlib.Path(archive).read_bytes()
    assert len(raw) == 257050
    assert hashlib.sha256(raw).hexdigest() == '591a339572ed073afa9056b27b1dda0ac1b2cb91fcec0a53037dbbf2e86ca5e8'
    with zipfile.ZipFile(io.BytesIO(raw)) as z:
        names = z.namelist()
        assert len(names) == len(set(names)) == 16 and z.testzip() is None
        assert all(not n.startswith('/') and '..' not in pathlib.PurePosixPath(n).parts for n in names)
        files = {n:z.read(n) for n in names}
    j = lambda n: json.loads(files[n])
    manifest = j('MANIFEST.json')
    assert set(manifest) == set(files)-{'MANIFEST.json'}
    assert all(hashlib.sha256(files[n]).hexdigest() == h for n,h in manifest.items())
    assert j('BINDING.json') == {
        'source':'b6adc80687793c0d75f87cde7cba0e1e1812b62b',
        'tree':'ea591657f8d64176680d84fc5ed547b0cb588c44',
        'validation':'9f8de39e84ea7da67332c1474af478be5d85944f',
        'validation_tree':'78eebf6590f1cb848bf49b19d92c249708462a7b',
        'workflow':'.github/workflows/manual-fblthp-metadata-r1-20261029.yml',
        'workflow_blob':'1a7000d326e8960233980671992d09d985e7f28f',
        'run':'40897068991','attempt':1,'run_number':1}
    for n,h in {'WORKFLOW.yml':'1a7000d326e8960233980671992d09d985e7f28f',
                'protocol/PROTOCOL.json':'34291af8db74a7631de1a33be00e6b225381f2b0',
                'protocol/run.py':'3fa5a730f84975d02505d96599a90c0ed4dfbf49'}.items():
        assert git_blob(files[n]) == h
    assert files['FINAL_TRACKED.diff'] == b''
    c=j('metadata/war.json');p=j('protocol/PROTOCOL.json')
    expected={'id':'52558748-6893-4c72-a9e2-e87d31796b59','oracle_id':'ba5a7ac0-b625-42e9-af05-2680a01a95ed',
        'name':'Fblthp, the Lost','set':'war','collector_number':'50','mana_cost':'{1}{U}',
        'type_line':'Legendary Creature — Homunculus','power':'1','toughness':'1','color_identity':['U'],
        'rarity':'rare','artist':'Jesper Ejsing','released_at':'2019-05-03'}
    assert all(c[k]==v for k,v in expected.items())
    assert c['oracle_text']=="When Fblthp enters, draw a card. If it entered from your library or was cast from your library, draw two cards instead.\nWhen Fblthp becomes the target of a spell, shuffle Fblthp into its owner's library."
    for n,u in {'metadata/war.json':p['card_url'],'metadata/printings-0.json':c['prints_search_uri'],
                'metadata/rulings-0.json':c['rulings_uri'],'rules.txt':p['rules_url']}.items():
        h=j(n+'.http.json');assert h['url']==u and h['status']==200
    prints=j('metadata/printings-0.json');rulings=j('metadata/rulings-0.json')
    assert prints['has_more'] is False and rulings['has_more'] is False
    assert len(prints['data'])==8 and len(rulings['data'])==5
    assert all(x['oracle_id']==c['oracle_id'] for x in prints['data']+rulings['data'])
    assert sum(x['id']==c['id'] for x in prints['data'])==1
    real=[x for x in prints['data'] if x['set_type'] not in ['promo','token','art_series','memorabilia']]
    assert min(x['released_at'] for x in real)==c['released_at']
    assert j('metadata/image.json')['url']==c['image_uris']['normal']
    assert j('metadata/image.json')['status']==200
    assert b'September 25, 2026' in files['rules.txt']
    assert j('RESULT.json')=={'state':'COLLECTED_NOT_RECEIVED','printings':8,'printing_pages':1,'rulings':5,'ruling_pages':1,'earliest_release':'2019-05-03','games':0,'seeds':0}
    return {'state':'RECEIVED_METADATA_ONLY_NO_FEATURE_OR_CARD_ADMISSION','run':40897068991,'job':123529005622,
        'attempt':1,'artifact':12906973695,'artifact_bytes':len(raw),'artifact_sha256':hashlib.sha256(raw).hexdigest(),
        'binding':j('BINDING.json'),'manifest_entries':len(manifest),'canonical':expected,'printings':8,'rulings':5,
        'original_http_and_exact_image_verified':True,'source_protocol_workflow_blobs_verified':True,
        'original_tracked_bytes_unchanged':True,'rules_effective':'2026-09-25',
        'behavioral_tests_executed':False,'card_admitted':False,'feature_admitted':False,'official_games':0,'official_seeds':0,'precision_reserved':2}

if __name__=='__main__':
    print(json.dumps(receive(sys.argv[1]),indent=2))
