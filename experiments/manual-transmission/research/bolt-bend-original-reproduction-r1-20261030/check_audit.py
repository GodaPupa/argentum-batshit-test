"""Synthetic auditor rejection checks; these are not engine behavioral evidence."""
from pathlib import Path
import tempfile,copy,xml.etree.ElementTree as ET,json
from audit import P,verify
with tempfile.TemporaryDirectory() as tmp:
    folder=Path(tmp);(folder/'exit.txt').write_text('1\n')
    (folder/'output.log').write_text('> Task :rules-engine:test FAILED\nBUILD FAILED\n')
    path=folder/'junit/rules-engine/build/test-results/test'/('TEST-'+P['class']+'.xml');path.parent.mkdir(parents=True)
    root=ET.Element('testsuite',name=P['class'],tests='5',failures='2',errors='0',skipped='0')
    for name in P['cases']:
        t=ET.SubElement(root,'testcase',name=name,classname=P['class'])
        if name in P['witnesses']:
            msg='org.opentest4j.AssertionFailedError: expected:<"{R}"> but was:<"{3}{R}">'
            ET.SubElement(t,'failure',type='org.opentest4j.AssertionFailedError',message=msg).text=msg+'\n at fixture(BoltBendCostPredicateTest.kt:'+str(P['witnesses'][name])+')'
    def write(r):path.write_bytes(ET.tostring(r))
    write(root);verify(folder);rejected=[]
    def reject(label,mutate):
        r=copy.deepcopy(root);mutate(r);write(r)
        try:verify(folder)
        except (AssertionError,ValueError):rejected.append(label)
        else:raise AssertionError('Accepted invalid report: '+label)
    reject('missing-case',lambda r:r.remove(r[0]))
    reject('duplicate-case',lambda r:r.append(copy.deepcopy(r[0])))
    reject('wrong-class',lambda r:r[0].set('classname','wrong'))
    reject('skip',lambda r:ET.SubElement(r[0],'skipped'))
    reject('unrelated-failure',lambda r:ET.SubElement(r[0],'failure'))
    reject('wrong-type',lambda r:r[2].find('failure').set('type','CompilationException'))
    reject('wrong-message',lambda r:r[2].find('failure').set('message','expected other'))
    reject('wrong-frame',lambda r:setattr(r[2].find('failure'),'text','expected:<"{R}"> but was:<"{3}{R}">\n at fixture(BoltBendCostPredicateTest.kt:1)'))
    reject('missing-witness',lambda r:r[2].remove(r[2].find('failure')))
    reject('wrong-count',lambda r:r.set('tests','4'))
    write(root);(folder/'output.log').write_text('> Task :rules-engine:compileTestKotlin FAILED\nBUILD FAILED\n')
    try:verify(folder)
    except AssertionError:rejected.append('compiler-failure')
    else:raise AssertionError('Accepted compilation failure')
    print(json.dumps({'synthetic_rejections':rejected,'count':len(rejected)}))
