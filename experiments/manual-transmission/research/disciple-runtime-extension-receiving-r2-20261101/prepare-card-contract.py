import json,re,hashlib
from pathlib import Path
base=json.loads(Path('extension-r2-protocol.json').read_text());review=json.loads(Path('card-controls-review.json').read_text());source=Path('disciple-card-controls-isolated.kt').read_text();prefix=Path('extension-r2-test.kt').read_text()[:-3];assert source.startswith(prefix)
new=re.findall(r'test\("([^"\n]+)"\)',source[len(prefix):]);names=[]
for name in new:
 if '$landQualified' in name:names.extend(name.replace('$landQualified',v) for v in ['false','true'])
 else:assert '$' not in name;names.append(name)
assert len(names)==14
all_names=base['case_names']+names;assert len(all_names)==len(set(all_names))==65
card_path=base['paths'][-1]
mutations=[]
for m in review['mutations']:
 k=dict(m);k['path']=card_path;k['witnesses']=[k.pop('witness')]+k.pop('additional_witnesses',[])
 if m['id'] in ['fixed_one','fixed_two','source_power','no_may','allow_self','allow_opponent','gate_bypass','require_source','graveyard_growth','triggered_target']:
  controls=['DF14 Garden pays exactly three life to enter untapped','DF15 declined Garden payment keeps life and enters tapped','DF17 Garden activation produces exactly one green and no other mana']
 elif m['id']=='canonical_land':
  controls=['DF14 Garden pays exactly three life to enter untapped','DF15 declined Garden payment keeps life and enters tapped','DF17 Garden activation produces exactly one green and no other mana']
 else:controls=['DF01 sacrifice gives exactly five life and five cards','DF02 decline preserves the creature life and hand','DF06 power 0 sacrifices without drawing or losing life']
 k['unaffected_controls']=controls
 for w in k['witnesses']:
  assert w['case'] in all_names
  assert source.splitlines()[w['line']-1].strip()==w['assertion']
 mutations.append(k)
p=dict(state='ISOLATED_PROTOCOL_DRAFT_NOT_FROZEN_NOT_EXECUTED',source_parent='be6b10da62d5a9e96eac8e635534e935d489cf0f',prerequisite=dict(run=41292141699,required='Original receiving before merging or dispatch; do not assume success.'),scope='Additive named scenario/registration tests only; candidate production remains byte-identical. 18 isolated card-data mutations only.',forms=['normal']+[m['id'] for m in mutations],compile_first='Compile both named test modules for every exact form; verify no raw behavioral reports. Restore normal before behavior and after every mutation. No compiler/setup/missing-report failure is negative evidence.',tests=65,case_names=all_names,prior_test_identities=51,prior_assertion_lines=297,total_assertion_lines=401,mutations=mutations,normal_requirement='All65normal plus65restored case identities/multiplicities,zero failures/errors/skips. Registration6normal/restored additive cases all pass.',negative_requirement='Each exact bound AssertionFailedError witness must occur after successful normal/mutant compilation, with same case passing normal/restored; all3named unaffected controls per mutant pass. Capture every other row and failure verbatim for classification. Any setup failure at required witness blocks qualification; no behavioral acceptance from metadata or compilation.',registration_file='mtg-sets/src/test/kotlin/com/wingedsheep/mtg/sets/DiscipleOfFreyaliseRegistrationTest.kt',registration_cases=re.findall(r'test\("([^"\n]+)"\)',Path('DiscipleOfFreyaliseRegistrationTest.draft.kt').read_text()),remaining_gates=['DF16 prohibition branch: source review found no static prohibition primitive in11categorized SDK files/ReplacementEffect/LifePaymentService. This is not executable evidence or an authority extension; resolve requirement interpretation or concrete new boundary before full admission.','Exact normal card metadata/rulings bytes versus archived original printing. No new live lookup required when pins remain exact.','One generated MH3 addition only after normal compile/export; verify original67843bytes24entries recovered exactly. Do not apply synthetic snapshot.','390card+additions,19139ordinary+additions/43exacthistoricalskips,64client/typecheck,required locked build/lint/printing/discovery; no accepted historical suite reruns without changed dependency source.','Original MH3 Assay before/after with every row/decline classified, unchanged current-IR absence retained.','Full exact-bound source/tree/workflow/run/job/attempt/artifact/manifest/raw report/source forms/restoration/protected byte original receiving.'],feature_admitted=False,card_admitted=False)
Path('card-qualification-contract.draft.json').write_text(json.dumps(p,indent=2)+'\n')
print(json.dumps(dict(cases=len(all_names),forms=len(p['forms']),witnesses=sum(len(m['witnesses']) for m in mutations),registration_cases=len(p['registration_cases']),frozen=False)))
