from RECEIVE import bound,sha
import copy,json
from pathlib import Path
clb='mtg-sets/src/test/resources/snapshots/cards/CLB.json';nph='mtg-sets/src/test/resources/snapshots/cards/NPH.json'
bindings={clb:sha(b'original CLB'),nph:sha(b'original NPH plus one entry')}
raw={'EARLY_CLB.json':b'original CLB'}
for label in ['initial','final']:
 raw[label+'/TRACKED.diff']=b'';raw[label+'/UNCHANGED_TREES.json']=b'{}'
 raw[label+'/source/'+clb]=b'original CLB';raw[label+'/source/'+nph]=b'original NPH plus one entry'
bound(raw,bindings);checks=[]
for key in list(raw):
 x=copy.deepcopy(raw);del x[key]
 try:bound(x,bindings)
 except (AssertionError,KeyError):checks.append('missing:'+key)
 else:raise AssertionError(key)
for key in list(raw):
 x=copy.deepcopy(raw);x[key]+=b'changed'
 try:bound(x,bindings)
 except (AssertionError,KeyError):checks.append('changed:'+key)
 else:raise AssertionError(key)
Path(__file__).with_name('SYNTHETIC_CHECKS.json').write_text(json.dumps({'positive':1,'rejected':checks,'not_behavioral_evidence':True},indent=2)+'\n');print(len(checks),'collection rejection controls')
