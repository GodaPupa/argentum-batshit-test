"""Inventory integrity only; never qualify mechanics, pilots or gameplay."""
import json,unicodedata,hashlib,xml.etree.ElementTree as ET
sha=lambda b:hashlib.sha256(b).hexdigest()
CASE='inventory exact Phase 2 candidate decks without treating registry presence as rules qualification'
CLASS='com.wingedsheep.gym.ManualTransmissionPhaseTwoRegistryAuditTest'
def audit(request,raw):
 report=json.loads(raw['registry/registry-report.json'])
 assert report['schema']=='mt-phase2-live-registry-audit-v1'
 assert report['status']=='INVENTORY_ONLY_NOT_RULES_OR_PILOT_QUALIFICATION' and report['execution_allowed'] is False
 assert report['actual_deck_games_initialized']==report['official_outcomes']==0
 assert len(request['decks'])==len(report['decks'])==8
 xml=[v for k,v in raw.items() if k.startswith('registry/junit/') and k.endswith('.xml')]
 assert len(xml)==1
 suite=ET.fromstring(xml[0]);assert suite.tag=='testsuite' and suite.attrib['name']==CLASS
 assert all(int(suite.attrib[k])==v for k,v in [('tests',1),('skipped',0),('failures',0),('errors',0)])
 cases=suite.findall('testcase');assert len(cases)==1 and cases[0].attrib['classname']==CLASS and cases[0].attrib['name']==CASE and not list(cases[0])
 summaries=[];exports=set();all_ids=[]
 for expected,actual in zip(request['decks'],report['decks']):
  id=expected['id'];all_ids.append(id);names=expected['commanders']+expected['mainboard'];assert len(names)==100
  assert actual['deck_id']==id and actual['physical_cards']==100 and actual['commander_count']==len(expected['commanders']) and len(actual['cards'])==100
  missing=[];found=0
  for i,(name,row) in enumerate(zip(names,actual['cards'])):
   normalized=unicodedata.normalize('NFC',name).replace('\u2019',"'")
   assert row['index']==i and row['requested_name']==name and row['normalized_name']==normalized and row['rules_qualified'] is False
   assert type(row['registry_resolved']) is bool
   key=f'registry/output/{id}-{i}.json'
   if row['registry_resolved']:
    found+=1;exports.add(key);card=json.loads(raw[key]);assert card['name']==row['resolved_name'] and card['name'] in [normalized,normalized.split(' // ')[0]]
    if ' // ' in normalized:assert card.get('backFace',{}).get('name')==normalized.split(' // ')[-1] or normalized.split(' // ')[-1] in [x.get('name') for x in card.get('cardFaces',[])]
   else:
    assert row['resolved_name'] is None and key not in raw;missing.append({'index':i,'name':name,'blocker':row.get('blocker','NOT_REGISTERED')})
  summaries.append(dict(deck_id=id,resolved=found,total=100,missing=missing))
 assert len(set(all_ids))==8 and {k for k in raw if k.startswith('registry/output/')}==exports
 return dict(state='RECEIVED_REGISTRY_INVENTORY_ONLY_NOT_RULES_ADMISSION',decks=summaries,resolved=sum(x['resolved'] for x in summaries),physical_cards=800,official_games=0,official_seeds=0,multiplayer_admission=False)
