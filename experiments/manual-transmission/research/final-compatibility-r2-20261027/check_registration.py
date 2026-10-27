from pathlib import Path
import tempfile,shutil,xml.etree.ElementTree as E
from registration_audit import audit_registration
from audit import P

def setup(p):
 source=Path(__file__).with_name('Serialization.kt');source=source if source.exists() else Path(P['registration_path']);s=source.read_bytes();(p/'normal.kt').write_bytes(s);(p/'mutant.kt').write_bytes(s.replace(P['registration_line'].encode(),b''))
 for label in ['compile-normal','compile-mutant','normal','mutant','restored']:
  d=p/label;d.mkdir();(d/'exit.txt').write_text('1' if label=='mutant' else '0');(d/'output.log').write_text('BUILD FAILED' if label=='mutant' else 'BUILD SUCCESSFUL')
  if label.startswith('compile'):continue
  j=d/'junit';j.mkdir();r=E.Element('testsuite',tests='8',failures='1' if label=='mutant' else '0',errors='0',skipped='0')
  for n in P['registration_cases']:
   c=E.SubElement(r,'testcase',name=n,classname='com.wingedsheep.engine.hygiene.SerializationPolymorphicRegistrationTest')
   if label=='mutant' and n==P['registration_witness']:
    E.SubElement(c,'failure',type='java.lang.IllegalStateException').text='Missing polymorphic registrations for AnswerContinuation\ncom.wingedsheep.engine.core.FixedRecipientChoiceContinuation'
  (j/'TEST-suite.xml').write_bytes(E.tostring(r))
with tempfile.TemporaryDirectory() as td:
 base=Path(td)/'base';base.mkdir();setup(base);audit_registration(base)
 for case in ['missing','compile','wrong_witness','wrong_type','skip','mutant_survived','wrong_source']:
  p=Path(td)/case;shutil.copytree(base,p)
  f=p/'mutant/junit/TEST-suite.xml'
  if case=='missing':f.unlink()
  elif case=='compile':(p/'compile-mutant/exit.txt').write_text('1')
  elif case=='wrong_source':(p/'mutant.kt').write_text('wrong')
  elif case=='mutant_survived':(p/'mutant/exit.txt').write_text('0')
  else:
   s=f.read_text()
   if case=='wrong_witness':s=s.replace('com.wingedsheep.engine.core.FixedRecipientChoiceContinuation','Other')
   elif case=='wrong_type':s=s.replace('java.lang.IllegalStateException','CompilerError')
   else:s=s.replace('skipped="0"','skipped="1"')
   f.write_text(s)
  try:audit_registration(p)
  except (AssertionError,FileNotFoundError):pass
  else:raise AssertionError(case+' accepted')
print('registration auditor:1valid7rejections')
