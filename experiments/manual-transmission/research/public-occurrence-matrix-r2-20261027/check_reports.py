"""Synthetic auditor controls; not Kotlin behavioral evidence."""
import copy,json,tempfile
from pathlib import Path
import xml.etree.ElementTree as ET
from reports import P,junit,stage
results=[]
def raw(variant='normal'):
    m=P['mutations'].get(variant.removeprefix('discovery-'),{});w={} if variant.startswith('discovery-') else m.get('witnesses',{});names=m.get('cases',P['cases'])
    r=ET.Element('testsuite',tests=str(len(names)),failures=str(len(w)),errors='0',skipped='0')
    for n in names:
        t=ET.SubElement(r,'testcase',classname=P['test_class'],name=n)
        if n in w:ET.SubElement(t,'failure',type=P['assertion_type']).text=m['marker']+'\nat witness(PublicOccurrenceMatrixTest.kt:'+str(w[n])+')'
    return {'TEST-matrix.xml':ET.tostring(r)}
def reject(label,fn):
    try:fn()
    except (AssertionError,ValueError,KeyError,FileNotFoundError):results.append(label);return
    raise AssertionError('accepted '+label)
for v in ['normal',*P['discovery_stages'],*P['mutations']]:junit(raw(v),v)
reject('missing reports',lambda:junit({}))
for v in P['discovery_stages']:
    reject(v+' full suite instead of selection',lambda:junit(raw(),v))
for v in P['mutations']:
    r=raw(v);r['TEST-matrix.xml']=r['TEST-matrix.xml'].replace(P['mutations'][v]['marker'].encode(),b'other marker')
    reject(v+' missing marker',lambda:junit(r,v))
    r=raw(v);frame=next(iter(P['mutations'][v]['witnesses'].values()));r['TEST-matrix.xml']=r['TEST-matrix.xml'].replace(('.kt:'+str(frame)).encode(),b'.kt:9999')
    reject(v+' wrong frame',lambda:junit(r,v))
    r=raw(v);r['TEST-matrix.xml']=r['TEST-matrix.xml'].replace(P['assertion_type'].encode(),b'java.lang.IllegalStateException')
    reject(v+' nonassertion',lambda:junit(r,v))
    r=raw(v);root=ET.fromstring(r['TEST-matrix.xml']);t=next(t for t in root if t.find('failure') is not None);t.remove(t.find('failure'));root.set('failures',str(int(root.get('failures'))-1));r['TEST-matrix.xml']=ET.tostring(root)
    reject(v+' surviving mutant',lambda:junit(r,v))
for what in ['missing','duplicate','skip','error']:
    r=raw();root=ET.fromstring(r['TEST-matrix.xml'])
    if what=='missing':root.remove(root[-1]);root.set('tests',str(len(root)))
    if what=='duplicate':root[1].set('name',root[0].get('name'))
    if what=='skip':ET.SubElement(root[0],'skipped');root.set('skipped','1')
    if what=='error':ET.SubElement(root[0],'error');root.set('errors','1')
    r['TEST-matrix.xml']=ET.tostring(root);reject(what,lambda:junit(r))
with tempfile.TemporaryDirectory() as tmp:
    d=Path(tmp)/'compile-normal';d.mkdir();(d/'exit.txt').write_text('0');(d/'output.log').write_text('BUILD SUCCESSFUL\n> Task :rules-engine:compileTestKotlin\n');stage(d)
    (d/'output.log').write_text('BUILD SUCCESSFUL\ne: unresolved\n> Task :rules-engine:compileTestKotlin\n');reject('compiler contamination',lambda:stage(d))
    (d/'exit.txt').write_text('1');reject('compile nonzero',lambda:stage(d))
print(json.dumps(dict(state='SYNTHETIC_AUDITOR_CHECKS_ONLY',accepted_shapes=10,rejected=results),indent=2))
