import copy,json,xml.etree.ElementTree as ET
from pathlib import Path
from receive_failure import archive_files,failed_ordinary
F=archive_files('departure-time-full-r1-original.zip');I=json.loads(F['INVENTORY.json'])['ordinary']
r=failed_ordinary(F,I);assert r['rows']==8417 and r['missing_rows']==10905
path='ordinary/junit/rules-engine/build/test-results/test/TEST-com.wingedsheep.engine.state.LegacySuspensionMigrationTest.xml'
def reject(label,mutate):
 f=F.copy();root=ET.fromstring(f[path]);mutate(root);f[path]=ET.tostring(root)
 try:failed_ordinary(f,I)
 except AssertionError:return label
 raise AssertionError('Invalid evidence accepted: '+label)
def failed(root):return next(t for t in root.iter('testcase') if t.find('failure') is not None)
def wrong_actual(root):
 f=failed(root).find('failure');f.set('message',f.get('message').replace('("castChoices", [])','("castChoices", {})'))
def wrong_identity(root):failed(root).set('name','unrelated failure')
def wrong_line(root):
 f=failed(root).find('failure');f.text=f.text.replace('LegacySuspensionMigrationTest.kt:65','LegacySuspensionMigrationTest.kt:66')
def duplicate(root):
 root.append(copy.deepcopy(failed(root)));root.set('tests',str(int(root.get('tests'))+1));root.set('failures',str(int(root.get('failures'))+1))
def skip(root):
 t=next(t for t in root.iter('testcase') if t.find('failure') is None);ET.SubElement(t,'skipped');root.set('skipped',str(int(root.get('skipped'))+1))
labels=[reject(n,f) for n,f in [('wrong actual',wrong_actual),('wrong identity',wrong_identity),('wrong assertion line',wrong_line),('duplicate row',duplicate),('new skip',skip)]]
Path('full-failed-received/SYNTHETIC.json').write_text(json.dumps({'type':'RECEIVER_ONLY_NOT_GAMEPLAY','positive_original':1,'rejections':labels},indent=2)+'\n')
print(labels)
