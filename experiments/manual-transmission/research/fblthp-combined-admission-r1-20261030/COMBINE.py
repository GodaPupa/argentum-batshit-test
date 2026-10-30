from pathlib import Path
import json,hashlib,zipfile,re
H=Path(__file__).resolve().parent;W=H.parent
sha=lambda b:hashlib.sha256(b).hexdigest()
blob=lambda b:hashlib.sha1(b'blob '+str(len(b)).encode()+b'\0'+b).hexdigest()
read=lambda p:json.loads(p.read_text())
r=read(H/'RECEIPT.json');assert not r['gate_errors']
P=read(H/'extracted/protocol/PROTOCOL.json');root=H/'extracted/initial/source'
pins={'generic-receive-r3':('c03e432936a53b49ceaadf136e27313a276dc538','60270ac90a542c20f2d142a6326576327d52b32fee5ac604b0c41484ea23e37b'),'one-test8-receive':('1af2974fbd8c7b7352e2e47de229b90e35759e93',None),'supp9-receive':('e92499415d4eef4465fda6af435342b29f45ca15','9068a3d60f65c5a6d070b2630932cfd741d210681de4d0f59ad72eeb55df4dcc')}
for name,(b,h) in pins.items():
 assert blob((W/name/'RECEIPT.json').read_bytes())==b
 if h:assert sha((W/name/'original.zip').read_bytes())==h
reuse={}
for name in ['one-test8-receive','supp9-receive','ir11-receive']:
 old=read(W/name/'extracted/protocol/PROTOCOL.json')
 for path,h in old['bindings'].items():assert P['bindings'][path]==h and sha((root/path).read_bytes())==h,path
 reuse[name]={'unchanged_sha256_bindings':len(old['bindings'])}
old=W/'generic-receive-r3/extracted/final/source';same=[]
war='mtg-sets/src/test/resources/snapshots/cards/WAR.json'
for f in old.rglob('*'):
 if f.is_file():
  path=str(f.relative_to(old))
  if path.startswith('experiments/') or path==war:continue
  assert f.read_bytes()==(root/path).read_bytes(),path;same.append(path)
assert len(same)==266
with zipfile.ZipFile(W/'generic-receive-r3/original.zip') as z:original=z.read('export/WAR.original.json')
candidate=(root/war).read_bytes();headers=list(re.finditer(rb'(?m)^// ([^\n]+)\n',candidate));i=[h[1] for h in headers].index(b'Fblthp, the Lost');start=headers[i].start();end=headers[i+1].start()
assert candidate[:start]+candidate[end:]==original
assert sha(original)=='10aba1124d5a31c54b130e92044c0a7bdfec4ed45c08800500e157e96fd37709'
assert sha(candidate)=='9564b58258f712f3d36bf11fab8c7c1f72157d97a07f33f93a41df4063c58c49'
b=(W/'generic-receive-r3/BEHAVIORAL_VERIFICATION.json').read_bytes();assert blob(b)=='f102d489d75ef0d91551a1c5656501d859b1d1f0';behavior=json.loads(b)
assert sum(len(v['failures']) for v in behavior.values())==54
ids=r['gates']['ordinary']['identities'];skips=r['gates']['ordinary']['skipped_identities']
required=[k for k in ids if ('CardLintTest' in k and 'every card' in k) or ('CardDefinitionSnapshotTest' in k and 'WAR (War of the Spark)' in k)]
assert len(required)==3 and all(ids[k]==1 and k not in skips for k in required)
trees=r['gates']['initial']['unchanged_trees'];prior=read(W/'supp9-receive/RECEIPT.json')
for k in ['mtg-sdk','mtg-sets','mtgish-tooling','oracle-assay','scripts']:assert trees[k]==prior['gates']['initial']['unchanged_trees'][k]
result={'state':'COMBINED_SOURCE_BINDINGS_VERIFIED','source':r['source'],'dependent_receipt':'899041e3768913832de41ec8f44335aa1303da89','exact_bound_reuse':reuse,'generic_unchanged_source_files':len(same),'generic_entry_origin_negative_witnesses':54,'snapshot':{'archived_original_sha256':sha(original),'candidate_sha256':sha(candidate),'added_entries':1,'all_original_bytes_preserved':True},'lint_snapshot_cases':required,'same_module_trees':trees,'feature_admitted':False,'card_admitted':False,'note':'Source-binding proof for combined admission review; semantic disposition is separate.'}
(H/'COMBINED_BINDINGS.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))
