"""Repair only the four string-unpacking inventory keys; preserve original evidence."""
from pathlib import Path
from collections import Counter
import copy,json,hashlib
from audit import inventory,junit
HERE=Path(__file__).resolve().parent
CLASS='com.wingedsheep.gameserver.session.FixedRecipientChoiceTransportTest'
CASE_NAMES=[
 'routing and JSON retain distinct occurrence labels for the same preview card',
 'nested routing changes only identity and enrichment preserves occurrence payload',
 'live occurrence transport preserves owner tokens replay and nested index 0',
 'live occurrence transport preserves owner tokens replay and nested index 1',
]
def repair(original,receipt):
 assert receipt['source']=='ec4914f8347c0456fc20eb4c78ea1078c1147a75'
 assert receipt['artifact_sha256']=='d942003dd36554f8bebe43fb22101d3ffe7e9fb61eddd38e159989ad93136948'
 assert receipt['gate_errors']==[]
 assert receipt['gates']['server-normal']['cases']==receipt['gates']['server-restored']['cases']==CASE_NAMES
 assert all(isinstance(x,str) and '\t' not in x for x in CASE_NAMES)
 result=copy.deepcopy(original);ids=result['ordinary']['identities'];mapping={}
 for name in CASE_NAMES:
  old='\t'.join(['game-server',*name])
  new='\t'.join(['game-server',CLASS,name])
  assert ids[old]==1 and new not in ids
  del ids[old];ids[new]=1;mapping[old]=new
 assert sum(ids.values())==18918 and sum(result['ordinary']['skips'].values())==43
 assert original['ordinary']['skips']==result['ordinary']['skips']
 assert len(mapping)==4 and len(ids)==len(original['ordinary']['identities'])
 for stage in original:
  assert all(len(k.split('\t'))==3 and v>0 for k,v in result[stage]['identities'].items())
  if stage!='ordinary':assert result[stage]==original[stage]
 return result,mapping
def main():
 original=inventory(HERE/'original');receipt=json.loads((HERE/'TRANSPORT_R7_RECEIPT.json').read_text())
 corrected,mapping=repair(original,receipt)
 (HERE/'INVENTORY_CORRECTION.json').write_text(json.dumps(dict(cause="derive.py unpacked each string case with *v instead of constructing [module,class,name]",mapping=mapping,unchanged_rows=18918,unchanged_passes=18875,unchanged_skips=43,transport_receipt='69f2dce2835a55ad364ee9b1a9c75f7b4b1e5ac9',original_inventory_sha256='65ed2e9903c0019b353990402eafb48d7e064539690759cc603c5ae077ed98da'),indent=2)+'\n')
 result=junit(HERE/'original/ordinary',corrected['ordinary'])
 (HERE/'ORDINARY_SUPPLEMENT.json').write_text(json.dumps(dict(state='ORIGINAL_ORDINARY_REPORTS_VERIFIED_AFTER_EXACT_INVENTORY_KEY_REPAIR',run=40572838230,artifact=12768604575,artifact_sha256='bf026b88a87f30845599211d1dc52c05e0e17614b6cdea3b545384169d1045cd',gate=result,remaining=['Assay'],original_failure_preserved=True),indent=2)+'\n')
 rejected=[]
 for mode in ['missing','duplicate','wrong_class','wrong_name','unexpected_skip','original_character_keys']:
  bad=copy.deepcopy(corrected['ordinary']);key='\t'.join(['game-server',CLASS,CASE_NAMES[0]])
  if mode=='missing':del bad['identities'][key]
  elif mode=='duplicate':bad['identities'][key]+=1
  elif mode in ['wrong_class','wrong_name']:
   del bad['identities'][key];parts=key.split('\t');parts[1 if mode=='wrong_class' else 2]='wrong';bad['identities']['\t'.join(parts)]=1
  elif mode=='unexpected_skip':bad['skips'][key]=1
  else:bad=original['ordinary']
  try:junit(HERE/'original/ordinary',bad)
  except AssertionError:rejected.append(mode)
  else:raise AssertionError('surviving negative control: '+mode)
 for mode in ['wrong_receipt','wrong_case_shape']:
  bad=copy.deepcopy(receipt)
  if mode=='wrong_receipt':bad['artifact_sha256']='wrong'
  else:bad['gates']['server-normal']['cases']=[[CLASS,n] for n in CASE_NAMES]
  try:repair(original,bad)
  except AssertionError:rejected.append(mode)
  else:raise AssertionError('surviving input mutation: '+mode)
 summary=dict(ordinary={k:v for k,v in result.items() if 'identit' not in k},negative_controls=rejected,other_inventory_stages_unchanged=True,skips_unchanged=True)
 (HERE/'CORRECTION_CHECKS.json').write_text(json.dumps(summary,indent=2)+'\n');print(json.dumps(summary))
if __name__=='__main__':main()
