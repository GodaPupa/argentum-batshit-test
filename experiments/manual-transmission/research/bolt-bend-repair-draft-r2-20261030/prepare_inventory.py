from pathlib import Path
import json,re,hashlib,sys
files=[Path(x) for x in sys.argv[1:]]
assert len(files)==2, 'Pass original five-case test and additive projection test paths'
rows={}
for p in files:
    current=None
    cls=re.search(r'class (\w+) :',p.read_text()).group(1)
    for line,text in enumerate(p.read_text().splitlines(),1):
        m=re.search(r'test\("([^"]+)"\)',text)
        if m:
            current=m.group(1).split()[0];rows[current]={'name':m.group(1),'class':'com.wingedsheep.engine.mana.'+cls,'file':p.name,'cost_assertions':[]}
        if current and 'cost(d' in text and 'shouldBe ManaCost.parse(' in text:
            cost=re.search(r'ManaCost.parse\("([^"]+)"\)',text).group(1)
            rows[current]['cost_assertions'].append({'line':line,'expected':cost})
failures={
'drop-power':['BB03','BB04','BP01','BP03','BP06','BP07','BP08'],
'exclusive-threshold':['BB03','BP01','BP03','BP06'],
'base-power':['BP03','BP04','BP06'],
'owner-battlefield':['BP01','BP02'],
'ignore-controller':['BB05','BP02','BP09'],
'wrong-amount':['BB03','BB04','BP01','BP03','BP06','BP07','BP08','BP11'],
'drop-red':['BB03','BB04','BP01','BP03','BP06','BP07','BP08','BP10','BP11'],
'base-type':['BP05','BP06']}
assert len(rows)==16 and len(failures)==8
assert all(rows[x]['cost_assertions'] for x in rows)
for form,ids in failures.items():
    assert len(ids)==len(set(ids)) and set(ids)<=set(rows)
result={'state':'PREDICTED_NOT_EXECUTED','cases':rows,'expected_failure_ids':failures,'skips':0,'compile_first':'Normal plus every mutant before behavioral tests; restoration normal compilation before restored positives.','remaining':'Pin exact actual cost messages and failing frame per form, then execute compiler/behavior/dependent gates and receive originals. This static inventory is not qualification evidence.'}
Path(__file__).with_name('INVENTORY_DRAFT.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps({'cases':len(rows),'forms':len(failures),'assertions':sum(len(x['cost_assertions']) for x in rows.values()),'failure_counts':{k:len(v) for k,v in failures.items()}}))
