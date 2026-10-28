from pathlib import Path
import json,re,xml.etree.ElementTree as ET
P=json.loads(Path(__file__).with_name('PROTOCOL.json').read_text())
def audit(folder,stage):
 d=Path(folder);result=json.loads((d/'RESULT.json').read_text());log=(d/'output.log').read_text();assert not re.search(r'^e: |Compilation error|Unresolved reference',log,re.M)
 if stage.startswith('compile-'):
  assert result['exit']==0 and 'BUILD SUCCESSFUL' in log
  assert re.search(r'^> Task :rules-engine:compileTestKotlin(?: UP-TO-DATE)?$',log,re.M)
  assert not re.search(r'^> Task :rules-engine:test(?: |$)',log,re.M)
  return {'compiled':True,'behavioral_tests':0}
 files=list((d/'junit').glob('TEST-*.xml'));assert len(files)==1
 root=ET.parse(files[0]).getroot();rows=root.findall('testcase');assert len(rows)==49 and root.get('tests')=='49';assert root.get('errors')==root.get('skipped')=='0'
 assert sorted(r.get('name') for r in rows)==sorted(P['cases']);assert all(r.get('classname')==P['class'] for r in rows)
 expected=P['witnesses'].get(stage,{});failed={r.get('name'):r.find('failure') for r in rows if r.find('failure') is not None};assert set(failed)==set(expected);assert int(root.get('failures'))==len(expected)
 assert not any(r.find('error') is not None or r.find('skipped') is not None for r in rows)
 for n,f in failed.items():
  assert f.get('type')=='org.opentest4j.AssertionFailedError'
  frame=re.search(r'SpellskiteScenarioTest\.kt:(\d+)',f.text or '');assert frame and int(frame.group(1))==expected[n]['line'],n
 if expected:
  assert result['exit']!=0;assert re.findall(r'^> Task (\S+) FAILED$',log,re.M)==[':rules-engine:test']
 else:assert result['exit']==0 and 'BUILD SUCCESSFUL' in log
 return {'cases':49,'passes':49-len(failed),'witnesses':expected,'errors':0,'skips':0}
