import json,copy
from pathlib import Path
from audit import audit,CASE,CLASS
req=json.loads(Path(__file__).with_name('REQUEST.json').read_text())
report=dict(schema='mt-phase2-live-registry-audit-v1',status='INVENTORY_ONLY_NOT_RULES_OR_PILOT_QUALIFICATION',execution_allowed=False,actual_deck_games_initialized=0,official_outcomes=0,decks=[])
import unicodedata
for d in req['decks']:
 report['decks'].append(dict(deck_id=d['id'],physical_cards=100,commander_count=len(d['commanders']),cards=[dict(index=i,requested_name=n,normalized_name=unicodedata.normalize('NFC',n).replace('\u2019',"'"),resolved_name=None,registry_resolved=False,rules_qualified=False) for i,n in enumerate(d['commanders']+d['mainboard'])]))
raw={'registry/registry-report.json':json.dumps(report).encode(),'registry/junit/result.xml':f'<testsuite name="{CLASS}" tests="1" skipped="0" failures="0" errors="0"><testcase name="{CASE}" classname="{CLASS}"/></testsuite>'.encode()}
assert audit(req,raw)['resolved']==0
mutants=[]
def rejected(name,f):
 r=copy.deepcopy(raw);f(r)
 try:audit(req,r)
 except (AssertionError,KeyError,ValueError):mutants.append(name)
 else:raise AssertionError('escaped '+name)
def report_mut(fn):
 def f(r):d=json.loads(r['registry/registry-report.json']);fn(d);r['registry/registry-report.json']=json.dumps(d).encode()
 return f
for name,fn in [('wrong-count',lambda d:d.update(actual_deck_games_initialized=1)),('enabled',lambda d:d.update(execution_allowed=True)),('outcome',lambda d:d.update(official_outcomes=1)),('missing-deck',lambda d:d['decks'].pop()),('wrong-deck',lambda d:d['decks'][0].update(deck_id='wrong')),('missing-row',lambda d:d['decks'][0]['cards'].pop()),('wrong-name',lambda d:d['decks'][0]['cards'][0].update(requested_name='wrong')),('wrong-index',lambda d:d['decks'][0]['cards'][0].update(index=1)),('false-qualification',lambda d:d['decks'][0]['cards'][0].update(rules_qualified=True)),('missing-export',lambda d:d['decks'][0]['cards'][0].update(registry_resolved=True,resolved_name='Animar, Soul of Elements'))]:rejected(name,report_mut(fn))
rejected('unexpected-export',lambda r:r.update({'registry/output/x.json':b'{}'}))
rejected('skipped',lambda r:r.update({'registry/junit/result.xml':r['registry/junit/result.xml'].replace(b'skipped="0"',b'skipped="1"')}))
rejected('missing-junit',lambda r:r.pop('registry/junit/result.xml'))
rejected('duplicate-junit',lambda r:r.update({'registry/junit/duplicate.xml':r['registry/junit/result.xml']}))
# Exercise the resolved/export branch with synthetic data as an auditor control only.
r=copy.deepcopy(raw);d=json.loads(r['registry/registry-report.json']);name=req['decks'][0]['commanders'][0]
d['decks'][0]['cards'][0].update(registry_resolved=True,resolved_name=name)
r['registry/registry-report.json']=json.dumps(d).encode();key='registry/output/manual-transmission-v07-0.json';r[key]=json.dumps({'name':name}).encode()
assert audit(req,r)['resolved']==1
r[key]=json.dumps({'name':'Wrong card'}).encode()
try:audit(req,r)
except AssertionError:pass
else:raise AssertionError('wrong exported identity escaped')

print(json.dumps({'synthetic_controls_only':True,'negative_controls':mutants+['wrong-export-identity'],'passing_synthetic_controls':['all-missing','one-resolved']}))
