"""Verify the two original Assay repair artifacts without executing a game or build."""
import hashlib
import json
import re
import shutil
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
SOURCE = '6cf30d14d4be570bc1786e48079549cf850fa16a'
VALIDATION = '0317a91b0bdbbebf386d1d035936e1e26c67e627'
originals = [
    dict(id=12431055463, name='manual-assay-search-negative-control-39787740747.zip', size=15548, sha256='b92474648ce84170706ac0b903cda810aea137b84761813845bf43e3e3247734', stage='negative-control', expected=dict(rows=31,passed=24,failures=7,errors=0,skipped=0,classes=3)),
    dict(id=12430330947, name='manual-assay-search-qualified-39787740747.zip', size=75231, sha256='bbd6818da07c8b9d9679624b98a0df7f8ce8f1c77bb27b967bea2c7677ff47d1', stage='assay', expected=dict(rows=568,passed=568,failures=0,errors=0,skipped=0,classes=39)),
]
receipts = []
for original in originals:
    path = HERE/original['name']
    if not path.exists():
        shutil.copyfile(HERE.parent/original['name'],path)
    raw=path.read_bytes()
    assert len(raw)==original['size'] and hashlib.sha256(raw).hexdigest()==original['sha256']
    with zipfile.ZipFile(path) as z:
        names=z.namelist()
        assert len(names)==len(set(names)) and z.testzip() is None
        manifest=dict(line.split('  ',1)[::-1] for line in z.read('SHA256SUMS').decode().splitlines())
        assert set(manifest)==set(names)-{'SHA256SUMS'}
        assert all(hashlib.sha256(z.read(name)).hexdigest()==sha for name,sha in manifest.items())
        assert z.read('source-sha.txt').decode().strip()==SOURCE
        assert z.read('validation-sha.txt').decode().strip()==VALIDATION
        summary=json.loads(z.read('SUMMARY.json'))
        assert summary['source']==SOURCE and summary['validation']==VALIDATION
        totals=dict.fromkeys(original['expected'],0)
        failed=[]
        for name in names:
            if not (name.startswith('junit/') and name.endswith('.xml')):
                continue
            suite=ET.fromstring(z.read(name))
            cases=suite.findall('testcase')
            assert len(cases)==int(suite.get('tests'))
            totals['classes']+=1
            totals['rows']+=len(cases)
            for case in cases:
                category='passed'
                for tag,field in [('failure','failures'),('error','errors'),('skipped','skipped')]:
                    if case.find(tag) is not None:
                        category=field
                        if tag!='skipped': failed.append(case.get('classname').rsplit('.',1)[-1]+':'+case.get('name'))
                        break
                totals[category]+=1
        assert totals==original['expected']==summary['totals']
        binding=json.loads(z.read('source-binding.json'))
        assert binding['frozen_deck_blob']=='c350bdc86eb2d37e5da4eb712e8ee2fa729b7e89'
        assert binding['original_test_blobs']=={
            'oracle-assay/src/test/kotlin/com/wingedsheep/assay/grammar/ManaValuesTest.kt':'c0efe06f759f5259d58bee67d54246cca41e2227',
            'oracle-assay/src/test/kotlin/com/wingedsheep/assay/grammar/SubtypesTest.kt':'8a936ec9f27e9442942519f3ef250c166d50f05f',
        }
        if original['stage']=='negative-control':
            assert z.read('exit.txt').decode().strip()=='1'
            assert z.read('control-production-blob.txt').decode().strip()=='483dbaa6fddf20fe276d0ff6c592f2980d2088fe'
            assert z.read('tracked-changes.txt').decode().strip()=='M oracle-assay/src/main/kotlin/com/wingedsheep/assay/grammar/Library.kt'
            assert {x.split(':')[0] for x in failed}=={'LibrarySearchTest','ManaValuesTest','SubtypesTest'}
            assert sum(x.startswith('LibrarySearchTest:') for x in failed)==3
        else:
            assert z.read('tracked-changes.txt')==b''
            for name in ['exit.txt','gate-exit.txt','differential-exit.txt']:
                assert z.read(name).decode().strip()=='0'
            gate=z.read('gate.log').decode()
            for label in ['Ambiguous — distinct readings','Print mismatch','Normalization not invertible','Full inverse not reproduced','Redundant readings (same model)']:
                assert re.search(re.escape(label)+r'\s+0(?:\s|$)',gate)
            assert re.search(r'Cards assayed\s+35085',gate)
            differential=z.read('differential.log').decode()
            assert re.search(r'golden would not decode\s+0',differential)
            assert re.search(r'DIVERGENT — read every one\s+51',differential)
            classified=(HERE/'CLASSIFICATION.md').read_text()
            names_in_report=re.findall(r'^  (.+)  \[([A-Z0-9]+)\]$',differential,re.M)
            assert len(names_in_report)==51
            assert all(name in classified for name,_ in names_in_report)
        receipts.append(dict(**original,unique_members=len(names),verified_internal_payloads=len(manifest),crc_clean=True,actual_xml=totals,failed_cases=failed))
result={'run':39787740747,'attempt':1,'source':SOURCE,'validation':VALIDATION,'originals':receipts,'whole_project_build':'PENDING_SEPARATE_RECEIPT','independent_human_or_agent_review':False,'official_games':0,'precision_reserved':2}
(HERE/'VERIFIED.json').write_text(json.dumps(result,indent=2)+'\n')
published=['README.md','CLASSIFICATION.md','verify.py','inspect_differential.py','VERIFIED.json']+[x['name'] for x in originals]
(HERE/'SHA256SUMS').write_text(''.join(hashlib.sha256((HERE/name).read_bytes()).hexdigest()+'  '+name+'\n' for name in sorted(published)))
print(json.dumps(result,indent=2))
