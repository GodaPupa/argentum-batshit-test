from pathlib import Path
import importlib.util,json,copy
import xml.etree.ElementTree as ET
r=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('receive_fixed',r/'RECEIVE.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)

def fixture(stage,frames=None):
    ids=sorted(m.P['negative_first_frames']) if stage=='mutation' else m.P['positive_cases']
    groups={}
    for identity in ids:groups.setdefault(identity.split('::')[0],[]).append(identity)
    raw={}
    for cls,rows in groups.items():
        suite=ET.Element('testsuite',tests=str(len(rows)),failures=str(len(rows) if stage=='mutation' else 0),errors='0',skipped='0')
        for identity in rows:
            c=ET.SubElement(suite,'testcase',classname=cls,name=identity.split('::',1)[1])
            if stage=='mutation':
                f=ET.SubElement(c,'failure',type='org.opentest4j.AssertionFailedError',message='synthetic only')
                f.text='at X(FixedRecipientPlayerRelationTest.kt:'+str((frames or m.P['negative_first_frames'])[identity])+')'
        raw[stage+'/junit/rules-engine/build/test-results/test/TEST-'+cls+'.xml']=ET.tostring(suite)
    raw[stage+'/exit.txt']=b'1' if stage=='mutation' else b'0'
    raw[stage+'/gradle.log']=b'> Task :rules-engine:test FAILED\n' if stage=='mutation' else b'> Task :rules-engine:test\n'
    result=m.parse_reports(raw,stage)
    raw[stage+'/SUMMARY.json']=json.dumps(dict(stage=stage,cases=sorted(ids),failures=[{k:f[k] for k in ['identity','type','stack']} for f in result['failures']],errors=[],skips=[],exit=int(raw[stage+'/exit.txt']))).encode()
    return raw

checks=[]
def rejected(label,fn):
    try:fn()
    except (AssertionError,KeyError,ValueError):checks.append(dict(check=label,passed=True));return
    raise AssertionError('Accepted invalid '+label)

for stage in ['targeted','mutation','restored']:
    m.verify_reports(fixture(stage),stage);checks.append(dict(check='accept exact synthetic '+stage,passed=True))
bad=fixture('mutation');bad['mutation/gradle.log']+=b'e: compiler failure\n'
rejected('compiler error contamination',lambda:m.verify_reports(bad,'mutation'))
frames={k:v+1 for k,v in m.P['negative_first_frames'].items()}
rejected('wrong assertion frame',lambda:m.verify_reports(fixture('mutation',frames),'mutation'))
bad=fixture('targeted');del bad[next(k for k in bad if k.endswith('.xml'))]
rejected('missing positive report',lambda:m.verify_reports(bad,'targeted'))
bad=fixture('mutation');bad={k:(v.replace(b'org.opentest4j.AssertionFailedError',b'java.lang.IllegalStateException') if k.endswith('.xml') else v) for k,v in bad.items()}
rejected('unrelated failure type',lambda:m.verify_reports(bad,'mutation'))
bad=fixture('mutation');bad['mutation/exit.txt']=b'0'
rejected('negative command succeeded',lambda:m.verify_reports(bad,'mutation'))
bad=fixture('mutation');bad['mutation/gradle.log']+=b'> Task :other:test FAILED\n'
rejected('unrelated task failure',lambda:m.verify_reports(bad,'mutation'))
bad=fixture('targeted');bad={k:(v.replace(b'<testcase ',b'<testcase ').replace(b'failures="0"',b'failures="1"') if k.endswith('.xml') else v) for k,v in bad.items()}
rejected('false suite totals',lambda:m.verify_reports(bad,'targeted'))
(r/'SYNTHETIC_CHECKS.json').write_text(json.dumps(dict(scope='Parser checks only; no Kotlin or gameplay evidence',checks=checks),indent=2)+'\n')
print(len(checks),'synthetic parser checks passed')
