"""Read-only review of the single recovered original; never runs tests or receiving."""
from pathlib import Path
from collections import Counter
import base64, gzip, hashlib, importlib.util, json, sys, traceback, datetime, subprocess
sys.dont_write_bytecode = True
O=Path(__file__).resolve().parent; N=O.parent; R=N.parents[5]
P=R/'experiments/manual-transmission/research/surge-bushwhacker-consolidated-proposal-20261114'
def read(p): return json.loads(Path(p).read_text())
def sha(p): return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def write(name,x): (O/name).write_text(json.dumps(x,indent=2)+'\n')
def load(name,p):
 s=importlib.util.spec_from_file_location(name,p);m=importlib.util.module_from_spec(s);s.loader.exec_module(m);return m
receiver=load('frozen_hosted_receiver',N/'receive_hosted.py')
receiver.configure(R)
meta=read(O/'META.json'); X=O/'extracted'
assert not X.exists(), 'Extraction already exists; do not repeat this review driver.'
files=receiver.unpack(O/'original.zip',X,meta)
Q=X/'qualification'
manifest=read(Q/'MANIFEST.json')
actual={p.relative_to(Q).as_posix():sha(p) for p in Q.rglob('*') if p.is_file() and p!=Q/'MANIFEST.json'}
assert manifest==actual, 'Inner qualification manifest mismatch'
pins=read(P/'CANDIDATE_PINS.json'); assert read(Q/'CANDIDATE_PINS.json')==pins
for path,pin in pins['bindings'].items():
 p=Q/'frozen-source'/path
 assert p.stat().st_size==pin['bytes'] and sha(p)==pin['sha256'],path
restored=read(Q/'RESTORATION.json');assert restored['exact_normal_bytes'] is True and restored['bindings']==pins['bindings']
context=receiver.verify_expected_topology(meta)
assert read(X/'RECOVERY_EVENT.json')==context
contract=read(X/'READINESS_CONTRACT.json')
assert contract==receiver._READINESS_CONTRACT
readiness=receiver._READINESS_MODULE.audit_readiness(X,contract,context)
write('INTEGRITY_REVIEW.json',dict(state='ORIGINAL_BYTES_ARCHIVE_MANIFESTS_SOURCE_BINDINGS_AND_READINESS_VERIFIED',archive_bytes=(O/'original.zip').stat().st_size,archive_sha256=sha(O/'original.zip'),archive_members=len(files)+1,outer_manifest_files=len(files),inner_manifest_files=len(manifest),frozen_bindings=len(pins['bindings']),source_restored=True,source_topology_verified=True,original_envelope_matches_immutable_carrier=True,readiness=readiness,formal_receipt=False,prior_receipt_history='UNKNOWN_UNCHANGED'))
audit=receiver._BASE.audit_all
try:
 result=audit(Q); full={'state':'PASS','result':result}
except Exception as exc:
 full={'state':'FAIL','exception':type(exc).__name__,'message':str(exc),'traceback':traceback.format_exc()}
write('FROZEN_FULL_AUDIT.json',full)
a=load('frozen_audit_independent',P/'audit_reports.py');matrix=read(P/'TEST_MATRIX.json');witness=read(P/'COMPILE_WITNESSES.json')
normal=Counter({r['identity']:1 for r in matrix['rows'] if r['family']!='client'})
gates={}
def check(name,fn):
 try: gates[name]={'state':'PASS','result':fn()}
 except Exception as exc: gates[name]={'state':'FAIL','exception':type(exc).__name__,'message':str(exc)}
for name in ['normal','restored']:check(name,lambda name=name:a.check_rows(Q/name,normal))
for m in witness['mutants']:check(m['id'],lambda m=m:a.check_rows(Q/m['id'],{m['witness']:1,m['positive_control']:1},negative=m))
baseline=json.loads(gzip.decompress(base64.b64decode((P/'ACCEPTED_INVENTORY.json.gz.b64').read_bytes())))
cards=Counter(baseline['cards']['identities']);cards.update({r['identity']:1 for r in matrix['rows'] if r['family']=='registry'})
check('cards',lambda:a.check_rows(Q/'cards',cards,baseline['cards']['skips']))
ordinary=Counter(baseline['ordinary']['identities']);ordinary.update(normal)
r2=read(P.parent/'reckless-own-prior-spell-diagnostic-proposal-20261106/PROTOCOL.json')
ordinary.update({'rules-engine\tcom.wingedsheep.engine.scenarios.SelfAlternativePriorSpellEligibilityDiagnosticTest\t'+n:1 for n in r2['cases']})
# Read the exact literal tuple from immutable auditor AST; no rewritten inventory.
import ast
tree=ast.parse((P/'audit_reports.py').read_text())
lint_names=next(ast.literal_eval(node.value) for node in ast.walk(tree) if isinstance(node,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='lint_names' for t in node.targets))
ordinary.update({'mtg-sdk\tcom.wingedsheep.sdk.serialization.SelfAlternativeChoiceLintTest\t'+n:1 for n in lint_names})
assert sum(ordinary.values())==19429 and sum(baseline['ordinary']['skips'].values())==43
check('ordinary',lambda:a.check_rows(Q/'ordinary',ordinary,baseline['ordinary']['skips']))
rows=a.xml_rows(Q/'ordinary');observed=Counter(r['identity'] for r in rows);skips=Counter(r['identity'] for r in rows if r['skip']);failures=[]
for row in rows:
 for kind in ['failure','error']:
  el=row[kind]
  if el is not None:failures.append(dict(identity=row['identity'],xml=row['xml'],kind=kind,type=el.get('type'),message=el.get('message'),stacktrace=''.join(el.itertext())))
ordinary_result=dict(expected_rows=sum(ordinary.values()),expected_passes=19386,expected_skips=43,observed_rows=len(rows),passed=sum(not r['skip'] and r['failure'] is None and r['error'] is None for r in rows),failures=sum(r['failure'] is not None for r in rows),errors=sum(r['error'] is not None for r in rows),skips=sum(r['skip'] for r in rows),missing=dict(ordinary-observed),extra=dict(observed-ordinary),missing_historical_skips=dict(Counter(baseline['ordinary']['skips'])-skips),extra_skips=dict(skips-Counter(baseline['ordinary']['skips'])))
write('ORDINARY_COUNTS.json',ordinary_result);write('ORDINARY_FAILURES.json',failures);write('PARTIAL_RAW_GATE_AUDIT.json',gates)
commands=read(Q/'COMMANDS.json')
write('OBSERVED_COMMANDS.json',[{'name':c['name'],'returncode':c['returncode']} for c in commands])
write('HOSTED_RUNTIME_OBSERVATION.json',read(X/'HOSTED_RUN.json'))
print(json.dumps({'integrity':'PASS','readiness':readiness,'ordinary':ordinary_result,'failures':failures,'gates':gates,'full_audit':{k:v for k,v in full.items() if k!='traceback'},'commands':[{'name':c['name'],'returncode':c['returncode']} for c in commands]},indent=2))
