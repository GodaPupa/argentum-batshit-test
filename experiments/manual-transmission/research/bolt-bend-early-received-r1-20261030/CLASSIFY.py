from pathlib import Path
import sys,json,copy,xml.etree.ElementTree as ET
H=Path(__file__).parent;h=H/'extracted';sys.path.insert(0,str(h/'protocol'));from audit import verify,P
r=json.loads((H/'RECEIPT.json').read_text());out={'state':'PARTIAL_EARLY_ORIGINAL_VERIFIED_COLLECTOR_FORMAT_FAILURE','run':41035962802,'compiled_forms':9,'normal_cases':16,'skips':0,'negative_gates':{},'original_failure_preserved':True,'feature_admitted':False,'card_admitted':False}
assert r['gates']['normal']['rows']==16
for name in P['witnesses']:
 if name=='base-type':continue
 if name=='drop-red':
  for w in P['witnesses'][name].values():
   if w['message'].endswith('was:<>'):w['message']=w['message'].replace('was:<>','was:<<empty string>>')
 got=verify(h/('negative-'+name),name)
 if name!='drop-red':
  old=json.loads((h/('negative-'+name)/'SUMMARY.json').read_text())
  assert {k:v for k,v in got.items() if k!='failures'}=={k:v for k,v in old.items() if k!='failures'}
  assert sorted(got['failures'],key=lambda x:x['id'])==sorted(old['failures'],key=lambda x:x['id'])
 out['negative_gates'][name]=got
out['negative_witnesses']=sum(len(x['failures']) for x in out['negative_gates'].values());out['negative_passing_controls']=sum(x['passing_controls'] for x in out['negative_gates'].values())
out['cause']='Collector predicted empty-string formatting was:<>; Kotest renders was:<<empty string>>. All nine colored-mana mutant assertion identities/types/frames/values match after exact literal correction. Receiver SUMMARY comparison also assumed filesystem traversal order for failures across two XML files; strict per-case comparison proves identical content and multiplicity. Original failure receipt preserved unchanged.'
out['remaining']=['base-type mutant16cases/2exactnegativewitnesses14controls','explicit restored compilation and16restored cases','full required dependent/card/client/Assay and combined Fblthp admission']
out['next']='Research-only bounded missing-gate successor: reuse verified normal/all8mutant compilation and seven original mutant reports bound to unchanged source; correct exact empty-string report literal and compare generated summary failures by unique case identity. Execute only missing base-type behavior and explicit restored compilation/behavior, with source/form/protected binding and original receipt gate. No production or test changes, no repeat of received gates.'
(H/'CLASSIFICATION.json').write_text(json.dumps(out,indent=2)+'\n');print(json.dumps({k:v for k,v in out.items() if k!='negative_gates'}))
