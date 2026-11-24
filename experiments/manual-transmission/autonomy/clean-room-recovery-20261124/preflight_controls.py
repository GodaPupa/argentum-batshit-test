"""Archived-report controls only; this program never runs candidate behavior."""
from pathlib import Path
from collections import Counter
import argparse, base64, copy, hashlib, importlib.util, io, json, subprocess, sys, tempfile, types, zipfile
import xml.etree.ElementTree as ET

sys.dont_write_bytecode=True
parser=argparse.ArgumentParser()
parser.add_argument('--source-root',required=True)
parser.add_argument('--output',required=True)
args=parser.parse_args()
source=Path(args.source_root).resolve()
carrier=Path(__file__).resolve().parents[4]
record='experiments/manual-transmission/research/surge-bushwhacker-consolidated-proposal-20261114'
p=source/record
archive_path=record+'/RECEIVED_CANDIDATE_02/NORMALIZED_M14_VALUE_AUDIT_FAILURE_01'
checkpoint='62b95ee8f854cb9a29830832c287106ad8baaac4'
normalized='3c7a9dd9a028b8644c92b67b3351ecf413eec484'
def git_bytes(ref,path):
    return subprocess.check_output(['git','show',ref+':'+path],cwd=source)
def digest(b):return hashlib.sha256(b).hexdigest()
index=json.loads(git_bytes(checkpoint,archive_path+'/ARCHIVE_PARTS.json'))
parts=[]
for part in index['parts']:
    encoded=b''.join(git_bytes(checkpoint,archive_path+'/'+part['name']).split())
    b=base64.b64decode(encoded,validate=True)
    assert len(b)==part['decoded_bytes'] and digest(b)==part['sha256']
    parts.append(b)
archive=b''.join(parts)
assert len(archive)==index['archive_bytes']==4842613
assert digest(archive)==index['archive_sha256']=='4753016dfa370a4667300457b476f09dae03ff8f79dd791e22af93db3255c34b'
z=zipfile.ZipFile(io.BytesIO(archive))
assert z.testzip() is None and len(z.infolist())==556
manifest=json.loads(z.read('qualification/MANIFEST.json'))
assert all(digest(z.read('qualification/'+name))==sha for name,sha in manifest.items())

spec=importlib.util.spec_from_file_location('clean_room_audit',p/'audit_reports.py')
audit=importlib.util.module_from_spec(spec);spec.loader.exec_module(audit)
old=types.ModuleType('historical_normalized_audit');old.__file__=str(p/'audit_reports.py')
exec(compile(git_bytes(normalized,record+'/audit_reports.py'),old.__file__,'exec'),old.__dict__)
matrix=json.loads((p/'TEST_MATRIX.json').read_text())
mutants=json.loads((p/'COMPILE_WITNESSES.json').read_text())['mutants']
commands=json.loads(z.read('qualification/COMMANDS.json'))
results=[]
with tempfile.TemporaryDirectory(prefix='manual-m14-controls-') as tmp:
    root=Path(tmp)
    originals=root/'archived-reports';originals.mkdir()
    (originals/'COMMANDS.json').write_text(json.dumps(commands))
    for name in z.namelist():
        if name.startswith('qualification/') and '/build/test-results/test/TEST-' in name and name.endswith('.xml'):
            rel=Path(name).relative_to('qualification')
            assert not rel.is_absolute() and '..' not in rel.parts
            dest=originals/rel;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(z.read(name))
    normal={r['identity']:1 for r in matrix['rows'] if r['family']!='client'}
    assert audit.check_rows(originals/'normal',normal)==old.check_rows(originals/'normal',normal)
    results.append({'name':'historical normal67 unchanged','result':'PASS'})
    for m in mutants[:-1]:
        expected={m['witness']:1,m['positive_control']:1}
        assert audit.check_rows(originals/m['id'],expected,negative=m)==old.check_rows(originals/m['id'],expected,negative=m)
        results.append({'name':m['id']+' historical result unchanged','result':'PASS'})
    m=mutants[-1];assert m['id']=='M14-copy-choice'
    expected={m['witness']:1,m['positive_control']:1}
    try:old.check_rows(originals/m['id'],expected,negative=m)
    except ValueError as e:
        assert 'wrong actual value' in str(e)
        results.append({'name':'preserved old M14 audit stop reproduced','result':'PASS','original_error':str(e).replace(str(root),'$CONTROL_ROOT')})
    else:raise AssertionError('historical M14 failure disappeared')
    observed=audit.check_rows(originals/m['id'],expected,negative=m)
    assert observed=={'rows':20,'executed':2,'skips':18,'failures':1}
    results.append({'name':'preserved M14 actual-was report accepted','result':'PASS','raw_counts':observed})
    rel=next((originals/m['id']).rglob('TEST-*.xml')).relative_to(originals/m['id'])
    original_xml=ET.parse(originals/m['id']/rel).getroot()
    witness=m['witness'].split('\t')[2];positive=m['positive_control'].split('\t')[2]
    def row(xml,name):return next(c for c in xml.findall('testcase') if c.get('name')==name)
    def failure(xml):return row(xml,witness).find('failure')
    def set_message(xml,text,body=None):
        f=failure(xml);f.set('message',text)
        tail='\n'.join((f.text or '').splitlines()[1:])
        f.text=(text if body is None else body)+'\n'+tail
    def recount(xml):
        rows=xml.findall('testcase')
        for key,tag in [('failures','failure'),('errors','error'),('skipped','skipped')]:
            xml.set(key,str(sum(c.find(tag) is not None for c in rows)))
        xml.set('tests',str(len(rows)))
    def control(name,mutation=None,command_mutation=None,accept=False,recount_counts=True):
        xml=copy.deepcopy(original_xml);cmd=copy.deepcopy(commands)
        if mutation:mutation(xml)
        if command_mutation:command_mutation(next(c for c in cmd if c['name']==m['id']))
        if recount_counts:recount(xml)
        case=root/name;dest=case/m['id']/rel;dest.parent.mkdir(parents=True)
        ET.ElementTree(xml).write(dest,encoding='utf-8',xml_declaration=True)
        (case/'COMMANDS.json').write_text(json.dumps(cmd))
        reason=None
        try:audit.check_rows(case/m['id'],expected,negative=m)
        except ValueError as e:reason=str(e).replace(str(root),'$CONTROL_ROOT')
        assert (reason is None)==accept,(name,reason,accept)
        results.append({'name':name,'result':'PASS','expected':'accept' if accept else 'reject','rejection':reason})
    for label,text in [
        ('prior-but-was','expected:<SELF_ALTERNATIVE> but was:<null>'),
        ('prior-actual','Expected SELF_ALTERNATIVE actual null'),
        ('observed-label','org.opentest4j.AssertionFailedError: Expected SELF_ALTERNATIVE but actual was null'),
    ]:control(label,lambda x,t=text:set_message(x,t),accept=True)
    for label,text in [
        ('reversed-values','Expected null but actual was SELF_ALTERNATIVE'),
        ('wrong-expected','Expected OTHER but actual was null'),
        ('wrong-actual','Expected SELF_ALTERNATIVE but actual was OTHER'),
        ('actual-value-suffix','Expected SELF_ALTERNATIVE but actual was nullify'),
        ('expected-value-suffix','Expected SELF_ALTERNATIVE_extra but actual was null'),
        ('actual-value-prefix','Expected SELF_ALTERNATIVE but actual was notnull'),
        ('unexpected-prefix','noise Expected SELF_ALTERNATIVE but actual was null'),
        ('unexpected-suffix','Expected SELF_ALTERNATIVE but actual was null noise'),
        ('unbalanced-brackets','Expected <SELF_ALTERNATIVE but actual was <null'),
        ('two-value-pairs','Expected SELF_ALTERNATIVE but actual was null; expected OTHER actual OTHER'),
    ]:control(label,lambda x,t=text:set_message(x,t))
    control('later-line-decoy',lambda x:set_message(x,'wrong assertion',body='wrong assertion\nExpected SELF_ALTERNATIVE but actual was null'))
    control('disagreeing-message-and-stack',lambda x:set_message(x,'Expected SELF_ALTERNATIVE but actual was null',body='Expected SELF_ALTERNATIVE but actual was OTHER'))
    control('wrong-exception',lambda x:failure(x).set('type','java.lang.IllegalStateException'))
    control('wrong-source-line',lambda x:setattr(failure(x),'text',failure(x).text.replace('SurgeCostCompositionTest.kt:137','SurgeCostCompositionTest.kt:1370')))
    control('missing-row',lambda x:x.remove(x.findall('testcase')[-1]))
    control('duplicate-row',lambda x:x.append(copy.deepcopy(x.findall('testcase')[0])))
    control('wrong-class',lambda x:row(x,witness).set('classname','unexpected.Class'))
    control('wrong-witness',lambda x:row(x,witness).set('name','unexpected witness'))
    control('witness-skipped',lambda x:ET.SubElement(row(x,witness),'skipped'))
    control('positive-skipped',lambda x:ET.SubElement(row(x,positive),'skipped'))
    control('positive-failed',lambda x:row(x,positive).append(copy.deepcopy(failure(x))))
    control('positive-errored',lambda x:ET.SubElement(row(x,positive),'error'))
    control('third-executed',lambda x:next(c for c in x.findall('testcase') if c.find('skipped') is not None).remove(next(c for c in x.findall('testcase') if c.find('skipped') is not None).find('skipped')))
    control('inconsistent-xml-count',lambda x:x.set('tests','19'),recount_counts=False)
    control('masked-mutant-exit',command_mutation=lambda c:c.update(returncode=0))
    control('swapped-selectors',command_mutation=lambda c:c['argv'].__setitem__(slice(4,7),[c['argv'][6],c['argv'][5],c['argv'][4]]))
    control('wildcard-selector',command_mutation=lambda c:c['argv'].__setitem__(4,'*'))
    control('third-selector',command_mutation=lambda c:c['argv'].extend(['--tests','*']))
    control('missing-command',command_mutation=lambda c:c.update(name='wrong-command'))

pins=json.loads((p/'CANDIDATE_PINS.json').read_text())
bad=[path for path,pin in pins['bindings'].items() if digest((source/path).read_bytes())!=pin['sha256'] or (source/path).stat().st_size!=pin['bytes']]
assert not bad,bad
changed=subprocess.check_output(['git','diff','--name-only',normalized],cwd=source,text=True).splitlines()
assert set(changed)=={record+'/'+n for n in ['audit_reports.py','verify_dispatch.py','CANDIDATE_PINS.json']}
out={'state':'PASS','kind':'ARCHIVED_REPORT_AND_STATIC_PREFLIGHT_ONLY','fresh_behavioral_runs':0,
     'historical_archive':{'checkpoint':checkpoint,'bytes':len(archive),'sha256':digest(archive),'members':len(z.infolist()),'crc':'PASS','qualification_manifest':'PASS'},
     'old_failure_remains_failed':True,'source_bindings_verified':len(pins['bindings']),'exact_source_delta':changed,
     'controls':results,'control_count':len(results),'new_source_hashes':{n:digest((p/n).read_bytes()) for n in ['audit_reports.py','verify_dispatch.py','CANDIDATE_PINS.json']}}
Path(args.output).write_text(json.dumps(out,indent=2)+'\n')
print(json.dumps({'state':out['state'],'controls':len(results),'source_bindings':len(pins['bindings']),'fresh_behavioral_runs':0}))
