from pathlib import Path
import ast,json,tempfile,os,xml.etree.ElementTree as E
root=Path(__file__).resolve().parent;p=json.loads((root/'PROTOCOL.json').read_text());m=ast.parse((root/'gate.py').read_text());ns={};exec(compile(ast.Module(body=[n for n in m.body if isinstance(n,(ast.Import,ast.ImportFrom)) or isinstance(n,ast.FunctionDef) and n.name=='receive'],type_ignores=[]),'<frozen R4>','exec'),ns)
checks=[]
for mode in ['exact','skip','missing','wrong-frame','wrong-type','compiler-error','extra-failure']:
 with tempfile.TemporaryDirectory() as tmp:
  b=Path(tmp);d=b/'mutation';d.mkdir();rows=[]
  for ident in p['mutation_cases']:
   cls,name=ident.split('::');row=E.Element('testcase',classname=cls,name=name)
   if ident in p['mutation_first_frames']:
    f=E.SubElement(row,'failure',type='org.opentest4j.AssertionFailedError',message='synthetic');f.text=f'{cls.rsplit(chr(46),1)[-1]}.kt:{p["mutation_first_frames"][ident]}'
   rows.append(row)
  if mode=='skip':E.SubElement(rows[-1],'skipped')
  if mode=='missing':rows.pop()
  if mode=='wrong-frame':next(r.find('failure') for r in rows if r.find('failure') is not None).text='CurrentTargetResolutionTest.kt:0'
  if mode=='wrong-type':next(r.find('failure') for r in rows if r.find('failure') is not None).set('type','java.lang.Exception')
  if mode=='extra-failure':E.SubElement(next(r for r in rows if r.find('failure') is None),'failure',type='org.opentest4j.AssertionFailedError').text='CurrentTargetResolutionTest.kt:1'
  suite=E.Element('testsuite',tests=str(len(rows)),failures=str(sum(r.find('failure') is not None for r in rows)),skipped=str(sum(r.find('skipped') is not None for r in rows)),errors='0');suite.extend(rows)
  f=b/'rules-engine/build/test-results/test/TEST-current.xml';f.parent.mkdir(parents=True);E.ElementTree(suite).write(f)
  (d/'exit.txt').write_text('1');(d/'gradle.log').write_text('> Task :rules-engine:test FAILED\n'+('e: compiler failure\n' if mode=='compiler-error' else ''))
  ns.update(OUT=b,P=p,SOURCE='synthetic',reports=lambda:[Path('rules-engine/build/test-results/test/TEST-current.xml')],check_forms=lambda _:None);os.environ['SOURCE_TREE']='synthetic';old=Path.cwd();os.chdir(b)
  try:
   try:ns['receive']('mutation');accepted=True
   except AssertionError:accepted=False
  finally:os.chdir(old)
  assert accepted==(mode=='exact'),mode;checks.append({'case':mode,'passed':True})
(root/'SYNTHETIC_CHECKS.json').write_text(json.dumps({'scope':'synthetic parser checks only, not behavioral evidence','checks':checks},indent=2)+'\n');print('seven checks passed')
