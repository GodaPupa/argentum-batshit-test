"""Full regression continuation for unchanged source; early original controls are reused."""
from pathlib import Path
import hashlib,json,os,sys,textwrap
OUT=Path('manual-fixed-recipient-source-context-r1-evidence')
EARLY=Path('experiments/manual-transmission/research/fixed-recipient-source-context-r1-20261025')
PRIOR=Path('experiments/manual-transmission/research/source-characteristics-r1-20261025')
sha=lambda b:hashlib.sha256(b).hexdigest()
gate=(PRIOR/'gate.py').read_text()
assert sha(gate.encode())=='06e15c08408261bcdcec43110b6d70ebf7b92e7ead6f6ec031359ae91be7967b'
assert sha((PRIOR/'PROTOCOL.json').read_bytes())=='a59f71849fa5c69e6a778e9c221bbe25d16e5858c91001ba069076fff132653a'
body=gate.split('\nmode=sys.argv[1]\n')[0]
anchor="    if stage in P['expected_expanded_identity_counters']:\n"
assert body.count(anchor)==1
body=body.replace('BOUNDED_SOURCE_CHARACTERISTICS_NOT_SPELLSKITE_OR_MULTIPLAYER','INTERNAL_FIXED_RECIPIENT_REGRESSION_NOT_FEATURE_ACCEPTANCE')
body=body.replace(anchor,"    for identity in EXTRA:\n        if stage in ['focused','broad','ordinary']:\n            assert names.pop('rules-engine\\t'+identity.replace('::','\\t',1),None)==1\n"+anchor)
ns={};exec(compile(body,'frozen-regression-base','exec'),ns)
ns['OUT']=OUT
P=ns['P'];extra=json.loads((EARLY/'PROTOCOL.json').read_text())['positive_cases'];ns['EXTRA']=extra
for identity in extra:
    cls=identity.split('::')[0].rsplit('.',1)[-1]
    P['new_test_cases'].setdefault(cls,[]).append(identity)
for cls in P['new_test_cases']:P['new_test_cases'][cls].sort()
for key in ['focused','broad','engine','ordinary_passes']:P['positive_counts'][key]+=len(extra)
def bind():
    early={};exec(compile((EARLY/'gate.py').read_text().split('\nmode=sys.argv[1]')[0],'early-source-binding','exec'),early)
    early['bind']()
    start=gate.index('    old_recipe = ');end=gate.index("    for path in set(P['implementation_sha256'])",start)
    recipe_body=textwrap.dedent(gate[start:end])
    exec(recipe_body,ns)
    recipe=(OUT/'validation.just').read_text()
    anchor="--tests '*EntryChoiceEventProvenanceTest'"
    assert recipe.count(anchor)==1
    recipe=recipe.replace(anchor,anchor+" --tests '*FixedRecipientRetargetingTest' --tests '*FixedRecipientMutationWitnessTest' --tests '*FixedRecipientPlayerRelationTest' --tests '*FixedRecipientSourceContextTest'")
    (OUT/'validation.just').write_text(recipe)
    (OUT/'EFFECTIVE_PROTOCOL.json').write_text(json.dumps(P,indent=2)+'\n')
    (OUT/'EFFECTIVE_GATE.py').write_text(body)
    preserved=OUT/'preserved';preserved.mkdir(exist_ok=True)
    for directory,name in [(ns['R2'],'BINDINGS.json'),(ns['R2'],'BASELINE_SKIPS.json'),(ns['R5'],'QUALIFIED_DUPLICATES.json'),(Path('experiments/manual-transmission/research/ordinary-build-r1-20261022'),'GOLDENS.json')]:
        (preserved/name).write_bytes((directory/name).read_bytes())
    (OUT/'REGRESSION_CONTRACT.json').write_text(json.dumps(dict(source=os.environ['SOURCE'],early_receipt='36d200e9ac6b285ee148b209999e33dc49d9dceb',early_run=40242125247,early_artifact=12616484795,early_artifact_sha256='858fe1e694d4b5f9abb64a6433118c3e3548c7b982f7bab0087a0d69583e59d7',extra_cases=extra,positive_counts=P['positive_counts'],scope='INTERNAL_COMPONENT_COMPATIBILITY_ONLY_NO_FEATURE_ADMISSION',official_games=0,official_seeds=0,precision_reserved=2),indent=2)+'\n')
def assay():
    body=(ns['OLD']/'check_assay.py').read_text().replace('paused-token-r4-evidence',str(OUT))
    dest=OUT/'assay'/'EFFECTIVE_CHECK.py';dest.parent.mkdir(parents=True,exist_ok=True);dest.write_text(body)
    ns['subprocess'].run([sys.executable,str(dest)],env=dict(os.environ,RECORD=str(ns['R2'])),check=True)
mode=sys.argv[1]
if mode=='bind':bind()
elif mode=='prepare':ns['prepare'](sys.argv[2])
elif mode=='receive':ns['receive'](sys.argv[2])
elif mode=='client':ns['client_receive']()
elif mode=='assay':assay()
elif mode=='hash':
    manifest=OUT/'SHA256SUMS'
    manifest.write_text(''.join(sha(p.read_bytes())+'  '+str(p.relative_to(OUT))+'\n' for p in sorted(OUT.rglob('*')) if p.is_file() and p!=manifest))
else:raise ValueError(mode)
