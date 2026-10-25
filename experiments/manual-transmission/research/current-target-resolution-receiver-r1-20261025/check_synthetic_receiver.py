"""Synthetic parser rejection checks only. Not Kotlin or behavioral evidence."""
import ast, copy, json, os, tempfile
from pathlib import Path
import xml.etree.ElementTree as ET
root=Path(__file__).resolve().parent.parent
p=json.loads((root/'resolver-r1/experiments/manual-transmission/research/current-target-resolution-r1-20261025/PROTOCOL.json').read_text())
s=(root/'resolver-r1/experiments/manual-transmission/research/current-target-resolution-r1-20261025/gate.py').read_text()
module=ast.parse(s)
keep=[n for n in module.body if isinstance(n,(ast.Import,ast.ImportFrom)) or isinstance(n,ast.FunctionDef) and n.name=='receive']
ns={};exec(compile(ast.Module(body=keep,type_ignores=[]),'<frozen R1 receive>','exec'),ns)
checks=[]
def check(name,change,accept=False):
 with tempfile.TemporaryDirectory() as tmp:
  base=Path(tmp);stage=base/'mutation';stage.mkdir()
  rows=[]
  for ident in p['mutation_cases']:
   cls,label=ident.split('::',1)
   rows.append(dict(cls=cls,label=label,type='org.opentest4j.AssertionFailedError',frame=p['mutation_first_frames'][ident]))
  ctx=dict(rows=rows,log='> Task :rules-engine:test FAILED\n',rc=1)
  change(ctx)
  suite=ET.Element('testsuite',tests=str(len(ctx['rows'])),failures=str(len(ctx['rows'])),errors='0',skipped='0')
  for row in ctx['rows']:
   case=ET.SubElement(suite,'testcase',classname=row['cls'],name=row['label'])
   error=ET.SubElement(case,'failure',type=row['type'],message='synthetic only')
   error.text=f"at {row['cls']}.invokeSuspend(CurrentTargetResolutionTest.kt:{row['frame']})"
  file=base/'rules-engine/build/test-results/test/TEST-current.xml';file.parent.mkdir(parents=True);ET.ElementTree(suite).write(file)
  (stage/'exit.txt').write_text(str(ctx['rc']));(stage/'gradle.log').write_text(ctx['log'])
  prev=Path.cwd();os.chdir(base)
  try:
   ns.update(OUT=base,P=p,SOURCE='41dfad5616b0143df07dae6aa098539085a94a99',reports=lambda:[Path('rules-engine/build/test-results/test/TEST-current.xml')],check_forms=lambda _:None)
   os.environ['SOURCE_TREE']='6b05894d4e3fc1857ef2c93e907ea08905514624'
   try: ns['receive']('mutation');passed=True
   except AssertionError:passed=False
  finally:os.chdir(prev)
  assert passed==accept,(name,passed,accept)
  checks.append(dict(name=name,expected='accept' if accept else 'reject',passed=True))
check('exact eight witnesses',lambda c:None,True)
check('missing witness',lambda c:c['rows'].pop())
check('extra witness',lambda c:c['rows'].append(copy.deepcopy(c['rows'][0])))
check('wrong identity',lambda c:c['rows'][0].update(label='wrong'))
check('wrong failure type',lambda c:c['rows'][0].update(type='java.lang.IllegalStateException'))
check('wrong first source frame',lambda c:c['rows'][0].update(frame=75))
check('compiler contamination',lambda c:c.update(log=c['log']+'e: unresolved\n'))
check('unrelated failed task',lambda c:c.update(log=c['log']+'> Task :mtg-sdk:test FAILED\n'))
check('zero test exit',lambda c:c.update(rc=0))
check('zero reports',lambda c:c.update(rows=[]))
out=dict(scope='synthetic receiver checks, not behavioral evidence',checks=checks)
(root/'resolver-receiver-r1/SYNTHETIC_CHECKS.json').write_text(json.dumps(out,indent=2)+'\n')
print(json.dumps(out))
