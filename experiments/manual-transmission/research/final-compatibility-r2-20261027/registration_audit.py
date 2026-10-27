from pathlib import Path
from collections import Counter
import xml.etree.ElementTree as E
from audit import P,sha

def audit_registration(folder):
 folder=Path(folder)
 for label in ['compile-normal','compile-mutant']:
  p=folder/label;assert int((p/'exit.txt').read_text())==0
  s=(p/'output.log').read_text();assert 'BUILD SUCCESSFUL' in s and 'Compilation error' not in s and '\ne: ' not in s
 normal=(folder/'normal.kt').read_bytes();mutant=(folder/'mutant.kt').read_bytes();assert sha(normal)==P['bindings'][P['registration_path']]
 assert normal.count(P['registration_line'].encode())==1 and mutant==normal.replace(P['registration_line'].encode(),b'')
 for label in ['normal','mutant','restored']:
  p=folder/label;code=int((p/'exit.txt').read_text());assert (code!=0)==(label=='mutant')
  log=(p/'output.log').read_text();assert 'Compilation error' not in log and '\ne: ' not in log and 'Unresolved reference' not in log
  files=list((p/'junit').glob('TEST-*.xml'));assert len(files)==1
  rt=E.fromstring(files[0].read_bytes());rows=rt.findall('testcase');assert len(rows)==int(rt.get('tests'))==8
  assert Counter(c.get('name') for c in rows)==Counter(P['registration_cases'])
  assert int(rt.get('errors'))==int(rt.get('skipped'))==0
  failures=[]
  for c in rows:
   assert c.get('classname')=='com.wingedsheep.engine.hygiene.SerializationPolymorphicRegistrationTest'
   assert c.find('error') is None and c.find('skipped') is None
   f=c.find('failure')
   if f is not None:
    failures.append(c.get('name'));assert f.get('type')=='java.lang.IllegalStateException'
    assert 'Missing polymorphic registrations for AnswerContinuation' in f.text and 'com.wingedsheep.engine.core.FixedRecipientChoiceContinuation' in f.text
  assert failures==([P['registration_witness']] if label=='mutant' else [])
  assert int(rt.get('failures'))==len(failures)
  if label!='mutant':assert 'BUILD SUCCESSFUL' in log
 return dict(compilations=2,normal=8,negative_witnesses=1,negative_controls=7,restored=8,skips=0)
