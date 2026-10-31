"""Receive the original diagnostic archive; never grant card admission."""
from pathlib import Path,PurePosixPath
import hashlib,json,zipfile,xml.etree.ElementTree as ET,re,sys
sha=lambda b:hashlib.sha256(b).hexdigest()
def require(condition,message):
 if not condition:raise ValueError(message)
def receive(archive,meta,here):
 p=json.loads((here/'PROTOCOL.json').read_text());expected=json.loads((here/'RECEIVING_PINS.json').read_text())
 require(meta['run_id']==int(expected['run']) and meta['attempt']==1,'run/attempt pin')
 require(meta['head_sha']==expected['validation'] and meta['event']=='push','validation/event pin')
 require(meta['job_id']>0 and meta['artifact_id']>0,'job/artifact pins')
 b=archive.read_bytes();require(len(b)==meta['size_in_bytes'],'ZIP byte count')
 require(sha(b)==meta['digest'].removeprefix('sha256:'),'ZIP digest')
 with zipfile.ZipFile(archive) as z:
  names=z.namelist()
  require(len(names)==len(set(names)),'duplicate ZIP member')
  require(all(not PurePosixPath(n).is_absolute() and '..' not in PurePosixPath(n).parts for n in names),'unsafe member path')
  require(z.testzip() is None,'ZIP CRC')
  files={n:z.read(n) for n in names if not n.endswith('/')}
 binding_names=[n for n in files if n=='BINDING.json' or n.endswith('/BINDING.json')]
 require(len(binding_names)==1,'exact one binding')
 prefix=binding_names[0][:-len('BINDING.json')]
 require(all(n.startswith(prefix) for n in files),'unexpected archive root')
 files={n[len(prefix):]:b for n,b in files.items()}
 get=lambda n:json.loads(files[n])
 manifest=get('MANIFEST.json')
 require(set(manifest)==set(files)-{'MANIFEST.json'},'manifest exact membership')
 require(all(sha(files[n])==v for n,v in manifest.items()),'manifest digest')
 binding=get('BINDING.json')
 for k,v in expected.items():
  if k!='protected':require(binding[k]==v,'binding '+k)
 require(get('protocol/PROTOCOL.json')==p,'frozen protocol bytes/values')
 require(files['protocol/PROTOCOL.json']==(here/'PROTOCOL.json').read_bytes(),'protocol exact bytes')
 require(files['scenario.kt']==(here/'scenario.kt').read_bytes(),'test exact bytes')
 require(files['WORKFLOW.yml']==(here/'WORKFLOW.yml').read_bytes(),'workflow exact bytes')
 for name in ['run.py','validation.just','SOURCE_REVIEW.json']:
  require(files['protocol/'+name]==(here/name).read_bytes(),'frozen runner '+name)
 for name,digest in p['form_hashes'].items():
  require(sha(files['protocol/forms/'+name+'.kt'])==digest,'form '+name)
 for label in ['initial','final']:
  require(get(label+'/PROTECTED.json')==expected['protected'],'protected binding '+label)
 require('FINAL_BINDING_FAILURE.txt' not in files,'final binding failed')
 compiled=[];compile_failures=[]
 for form in p['forms']:
  folder='compile-'+form+'/'
  if folder+'exit.txt' not in files:continue
  rc=int(files[folder+'exit.txt']);log=files[folder+'output.log'].decode(errors='replace')
  require(sha(files[folder+'CARD.kt'])==p['form_hashes'][form],'compiled form bytes')
  ok=rc==0 and 'BUILD SUCCESSFUL' in log and ':mtg-sets:2024:tests:compileTestKotlin' in log
  require(not any(n.startswith(folder+'junit/') for n in files),'compile produced behavioral reports')
  require(get(folder+'SUMMARY.json')['compile_passed']==ok,'compile summary agreement')
  (compiled if ok else compile_failures).append(form)
 all_compiled=compiled==p['forms'] and not compile_failures
 behavioral_paths=[n for n in files if n.startswith('observe-')]
 if not all_compiled:
  require(not behavioral_paths,'behavior ran before all compilations')
  require(compile_failures and 'FAILURE.txt' in files,'unexplained incomplete compile')
  return dict(state='ORIGINAL_COMPILATION_FAILURE_VERIFIED_NOT_BEHAVIORAL',pins=meta,archive_sha256=sha(b),manifest_entries=len(manifest),compiled=compiled,compile_failures=compile_failures,negative_witnesses=[],card_admitted=False)
 require(get('ALL_FORMS_COMPILED.json')==dict(forms=p['forms'],all_compiled_before_behavior=True),'compile-first marker')
 observed={}
 for form in p['forms']:
  folder='observe-'+form+'/'
  require(sha(files[folder+'CARD.kt'])==p['form_hashes'][form],'observed form')
  rc=int(files[folder+'exit.txt']);rows=[]
  for n,data in files.items():
   if n.startswith(folder+'junit/') and n.endswith('.xml'):
    for c in ET.fromstring(data).iter('testcase'):
     rows.append(dict(classname=c.get('classname'),name=c.get('name'),failures=[dict(type=f.get('type'),message=f.get('message'),text=f.text) for f in c.findall('failure')],errors=[dict(type=f.get('type'),message=f.get('message'),text=f.text) for f in c.findall('error')],skipped=len(c.findall('skipped'))))
  require(sorted(r['name'] for r in rows)==sorted(p['case_names']),'raw names/multiplicities '+form)
  require(all(r['classname']==p['class'] and r['skipped']==0 for r in rows),'class/skip '+form)
  failed=[r for r in rows if r['failures'] or r['errors']]
  require(rc in (0,1) and (rc!=0)==bool(failed),'exit/report agreement '+form)
  summary=get(folder+'SUMMARY.json')
  require(summary['total']==len(rows) and summary['failed']==len(failed) and summary['skipped']==0,'summary counts '+form)
  raw=[{k:v for k,v in r.items() if k!='report'} for r in summary['rows']]
  require(sorted(raw,key=lambda r:r['name'])==sorted(rows,key=lambda r:r['name']),'summary raw rows '+form)
  observed[form]={r['name']:r for r in rows}
 witnesses=[];not_witnesses=[]
 for form,c in p['controls'].items():
  if c.get('kind')=='localization_only':continue
  normal=observed['normal'][c['case']];mutant=observed[form][c['case']]
  text='\n'.join(str(f.get('message',''))+'\n'+str(f.get('text','')) for f in mutant['failures'])
  line='DiscipleOfFreyaliseScenarioTest.kt:'+str(c['assertion_line'])
  expected_text=str(c['expected_normal']).lower();actual_text=str(c['expected_mutant']).lower()
  exact_values=bool(re.search(r'expected[^\n]*?'+re.escape(expected_text)+r'[^\n]*?(?:but was|actual|got)[^\n]*?'+re.escape(actual_text),text,re.I))
  valid=not normal['failures'] and not normal['errors'] and mutant['failures'] and not mutant['errors'] and line in text and exact_values
  (witnesses if valid else not_witnesses).append(dict(form=form,case=c['case'],assertion_line=c['assertion_line'],raw_failure=mutant['failures']))
 require(get('restored/PROTECTED.json')==expected['protected'],'restored protected')
 return dict(state='ORIGINAL_DIAGNOSTIC_VERIFIED_NOT_ADMISSION',pins=meta,archive_sha256=sha(b),manifest_entries=len(manifest),all_forms_compiled=True,counts={form:dict(total=len(rows),failed=sum(bool(r['failures'] or r['errors']) for r in rows.values())) for form,rows in observed.items()},normal_failures=[r for r in observed['normal'].values() if r['failures'] or r['errors']],negative_witnesses=witnesses,not_negative_witnesses=not_witnesses,localization_only=[k for k,v in p['controls'].items() if v.get('kind')=='localization_only'],card_admitted=False,feature_admitted=False,full_qualification=False)
if __name__=='__main__':
 result=receive(Path(sys.argv[1]),json.loads(Path(sys.argv[2]).read_text()),Path(__file__).resolve().parent)
 print(json.dumps(result,indent=2))
