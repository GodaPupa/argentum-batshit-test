from pathlib import Path
import importlib.util,json,copy,xml.etree.ElementTree as ET
spec=importlib.util.spec_from_file_location('receiver',Path(__file__).with_name('RECEIVE.py'));r=importlib.util.module_from_spec(spec);spec.loader.exec_module(r)
p=json.loads(Path(__file__).with_name('PROTOCOL.json').read_text());results={}
def raw_stage(stage):
 negative=stage.startswith('mutation-'); frames=p['mutation_first_frames'].get(stage.removeprefix('mutation-'),{})
 ids=p['mutation_cases'] if negative else sorted(x for rows in p['targeted_cases'].values() for x in rows)
 classes={}
 for identity in ids:
  cls,name=identity.split('::');classes.setdefault(cls,[]).append((identity,name))
 raw={}
 for cls,rows in classes.items():
  fail=sum(i in frames for i,_ in rows)
  suite=ET.Element('testsuite',tests=str(len(rows)),failures=str(fail),errors='0',skipped='0')
  for identity,name in rows:
   row=ET.SubElement(suite,'testcase',classname=cls,name=name)
   if identity in frames:
    f=ET.SubElement(row,'failure',type='org.opentest4j.AssertionFailedError',message='expected:<true> but was:<false>')
    f.text='expected:<true> but was:<false>\n at '+cls+'.invoke('+cls.rsplit('.',1)[-1]+'.kt:'+str(frames[identity])+')'
  raw[f'{stage}/junit/rules-engine/build/test-results/test/TEST-{cls}.xml']=ET.tostring(suite)
 raw[stage+'/exit.txt']=b'1' if negative else b'0'
 raw[stage+'/gradle.log']=('scripts/gradle-locked --max-workers=2 -PkotlinCompileParallelism=1\n> Task :rules-engine:test'+(' FAILED\nBUILD FAILED' if negative else '\nBUILD SUCCESSFUL')).encode()
 summary=r.parse_reports(raw,stage);summary.pop('names');summary.update(source=r.PINS['source'],tree=r.PINS['tree'],validation=r.PINS['validation'],run=str(r.PINS['run']),official_games=0,official_seeds=0)
 raw[stage+'/SUMMARY.json']=json.dumps(summary).encode();return raw

def reject(name,stage,fn):
 raw=raw_stage(stage);fn(raw)
 try:r.verify_stage(raw,stage,p,r.PINS['run'])
 except (AssertionError,KeyError,ValueError):results[name]='REJECTED_AS_REQUIRED'
 else:raise AssertionError('accepted corruption: '+name)

for stage in ['targeted','mutation-live-only','mutation-wrong-visit']:
 r.verify_stage(raw_stage(stage),stage,p,r.PINS['run']);results['valid_'+stage]='ACCEPTED_SYNTHETIC_CONTRACT'
reject('wrong_first_frame','mutation-live-only',lambda raw:raw.update({k:v.replace(b'.kt:97)',b'.kt:98)') for k,v in raw.items() if k.endswith('.xml')}))
reject('compile_failure_is_not_behavior','mutation-live-only',lambda raw:raw.__setitem__('mutation-live-only/gradle.log',raw['mutation-live-only/gradle.log']+b'\ne: Unresolved reference'))
reject('missing_report','targeted',lambda raw:raw.pop(next(k for k in raw if k.endswith('.xml'))))
reject('missing_test_task','mutation-wrong-visit',lambda raw:raw.__setitem__('mutation-wrong-visit/gradle.log',b'BUILD FAILED'))
reject('zero_exit_for_negative','mutation-live-only',lambda raw:raw.__setitem__('mutation-live-only/exit.txt',b'0'))
reject('forged_green_totals','targeted',lambda raw:raw.__setitem__('targeted/SUMMARY.json',b'{}'))
reject('source_substitution','targeted',lambda raw:raw.__setitem__('targeted/SUMMARY.json',raw['targeted/SUMMARY.json'].replace(r.PINS['source'].encode(),b'0'*40)))
Path(__file__).with_name('RECEIVER_SYNTHETIC_CHECKS.json').write_text(json.dumps(dict(engine_executed=False,qualification_acceptance=False,checks=results),indent=2)+'\n')
print(json.dumps(results,indent=2))
