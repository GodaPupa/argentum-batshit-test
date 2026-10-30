"""Executable synthetic original-report auditor rejection controls, not runtime evidence."""
from pathlib import Path
from collections import Counter
import tempfile,json,xml.etree.ElementTree as ET,sys
from audit import junit,client
checks=[]
def reject(label,fn):
 try:fn()
 except (AssertionError,ValueError,KeyError,FileNotFoundError):checks.append(label);return
 raise AssertionError('accepted '+label)
with tempfile.TemporaryDirectory() as t:
 f=Path(t);(f/'junit/mod/build/test-results/test').mkdir(parents=True);(f/'exit.txt').write_text('0');(f/'output.log').write_text('BUILD SUCCESSFUL')
 path=f/'junit/mod/build/test-results/test/TEST-one.xml';expected={'identities':{'mod\tC\ta':2,'mod\tC\tb':1},'skips':{'mod\tC\tb':1}}
 def xml():
  r=ET.Element('testsuite',tests='3',errors='0',failures='0',skipped='1');ET.SubElement(r,'testcase',classname='C',name='a');ET.SubElement(r,'testcase',classname='C',name='a');b=ET.SubElement(r,'testcase',classname='C',name='b');ET.SubElement(b,'skipped');return r
 def write(r):path.write_bytes(ET.tostring(r))
 write(xml());junit(f,expected)
 for variant in ['missing','duplicate','skip','error','failure','wrongcount','wrongidentity']:
  r=xml()
  if variant=='missing':r.remove(r[0]);r.set('tests','2')
  elif variant=='duplicate':ET.SubElement(r,'testcase',classname='C',name='a');r.set('tests','4')
  elif variant=='skip':ET.SubElement(r[0],'skipped');r.set('skipped','2')
  elif variant in ['error','failure']:ET.SubElement(r[0],variant);r.set('errors' if variant=='error' else 'failures','1')
  elif variant=='wrongcount':r.set('tests','4')
  else:r[0].set('name','wrong')
  write(r);reject(variant,lambda:junit(f,expected))
 write(xml());(f/'exit.txt').write_text('1');reject('nonzeroexit',lambda:junit(f,expected));(f/'exit.txt').write_text('0')
 (f/'output.log').write_text('BUILD SUCCESSFUL\ne: bad compiler');reject('compilercontamination',lambda:junit(f,expected));(f/'output.log').write_text('BUILD SUCCESSFUL')
 path.unlink();reject('missingreport',lambda:junit(f,expected))
 c={'client_cases':{'web-client/test.ts':[str(i) for i in range(64)]}}
 for n in ['npm-ci','typecheck','test']:(f/(n+'-exit.txt')).write_text('0')
 r=dict(success=True,numTotalTests=64,numPassedTests=64,numFailedTests=0,numPendingTests=0,testResults=[dict(name='/root/web-client/test.ts',status='passed',assertionResults=[dict(title=str(i),status='passed',failureMessages=[]) for i in range(64)])]);(f/'vitest.json').write_text(json.dumps(r));client(f,c)
 for v in ['identity','status','pending']:
  x=json.loads(json.dumps(r))
  if v=='identity':x['testResults'][0]['assertionResults'][0]['title']='wrong'
  elif v=='status':x['testResults'][0]['assertionResults'][0]['status']='skipped'
  else:x['numPendingTests']=1
  (f/'vitest.json').write_text(json.dumps(x));reject('client '+v,lambda:client(f,c))
print(json.dumps(dict(state='SYNTHETIC_AUDITOR_ONLY',valid_shapes=2,rejected=checks),indent=2))
