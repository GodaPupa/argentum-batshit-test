#!/usr/bin/env python3
from __future__ import annotations
import argparse, hashlib, json, pathlib, zipfile

ROOT = pathlib.Path('.').resolve()
OUT = ROOT / 'build/reports/ferocity-final-development-manifest'
MAT = OUT / 'materialized'
EVID = pathlib.PurePosixPath('ferocity-recycling/evidence/admission/D2-first-cell-current')
Q_SHA='9849c7aade4ffe015fd5ab859ae57e4ba3c4e5461514bbc72bc6ffa355be0816'
E_SHA='94ec8d2ee12f929316c39ce0c69fbf9e5ad869a3b4ecedaa48faa2f8280b7ee6'
W_SHA='c436b7df09091a001df6ecdad25a909c2ce7f4bb100d7ddb6f6020a1e4c4dedf'
SOURCE='3a4f99a7653839506e96d19e6639f58d9e8c5ced'
SOURCE_TREE='56c6b8dd46dc112cdb70db496fd9d0c3e915e8a6'
COMPILED_FILE_SHA='36cfd36a923f6883ae7b55925957055ae65389ae41a17eafb2ad48fdf4a14908'
COMPILED_CANON='663af924957ed0d67af8cd594a8abcd6c1e404ea8181acda3fcbac28b6961fd3'
BUNDLE_SHA='abc31a66ca4fc15602dfa861af83119c4418a9cc0cf9bc39bb01b2c7034d42ba'
INLINE_SHA='16f8532a53a15a24c1b7318f3915e7c4d85b203e365f6348d99ade2e82b7535f'
CLASSPATH_SHA='3615e99ef8a8c108eb6fc0bf4bbd65c6f8843c5bb37894cc5fa360752f103415'
JAVA_SHA='2a207f5e7d075afa01d97f8048389a64432a44c4a5af0f5e77d6e286ec5f401d'
JAVA_PATH='/home/runner/work/_temp/ferocity-source08-jdk-36655120463/jdk-21.0.12.1+1/bin/java'
REPO_PATH='/home/runner/work/argentum-batshit-test/argentum-batshit-test'
RULES_SHA='d8a4c1de79be6c91bce555dfb0e1eb69870f6d3e8a858b9461c74af7a4ee8759'
WD_PREFIX='build/reports/ferocity-watchdog-current-runtime-requalification/watchdog/'

def sha(b: bytes)->str: return hashlib.sha256(b).hexdigest()
def fsha(p:pathlib.Path)->str:
    with p.open('rb') as f: return hashlib.file_digest(f,'sha256').hexdigest()
def canon(v)->bytes: return json.dumps(v,sort_keys=True,separators=(',',':'),ensure_ascii=False).encode()
def git_blob(b:bytes)->str: return hashlib.sha1(f'blob {len(b)}\0'.encode()+b).hexdigest()
def put(rel:str,data:bytes):
    p=MAT/rel; p.parent.mkdir(parents=True,exist_ok=True); p.write_bytes(data); return p

def reassemble_source08(dest:pathlib.Path):
    publication=json.loads((ROOT/'ferocity-recycling/evidence/source08-takeover/publication-manifest.json').read_text())
    a=next(x for x in publication['originals'] if x['id']==10914678223)
    assert a['sha256']==Q_SHA and a['bytes']==95124680 and len(a['parts'])==12
    h=hashlib.sha256(); written=0
    with dest.open('xb') as out:
        for part in sorted(a['parts'],key=lambda x:x['offset']):
            b=(ROOT/'ferocity-recycling/evidence/source08-takeover'/part['path']).read_bytes()
            assert written==part['offset'] and len(b)==part['bytes']
            assert sha(b)==part['sha256'] and git_blob(b)==part['git_blob_sha1']
            out.write(b); h.update(b); written+=len(b)
    assert h.hexdigest()==Q_SHA

def main():
    ap=argparse.ArgumentParser(); ap.add_argument('--offline',required=True); ap.add_argument('--watchdog',required=True); a=ap.parse_args()
    OUT.mkdir(parents=True,exist_ok=False); MAT.mkdir()
    offline=pathlib.Path(a.offline).resolve(); watchdog_zip=pathlib.Path(a.watchdog).resolve()
    assert fsha(offline)==E_SHA and fsha(watchdog_zip)==W_SHA
    qzip=OUT/'source08-qualification-original.zip'; reassemble_source08(qzip)
    with zipfile.ZipFile(qzip) as q, zipfile.ZipFile(offline) as e, zipfile.ZipFile(watchdog_zip) as w:
        assert q.testzip() is None and e.testzip() is None and w.testzip() is None
        receipt_b=q.read('qualification/receipt.json'); receipt=json.loads(receipt_b)
        compiled_b=q.read('qualification/compiled-inputs-before.json'); compiled=json.loads(compiled_b)
        assert sha(receipt_b)=='d898102510976b4791410e43fd9d71b61c4b6044763366e2438e3383fb212e27'
        assert sha(compiled_b)==COMPILED_FILE_SHA and sha(canon(compiled))==COMPILED_CANON
        assert receipt['status']=='PASS' and receipt['compiled_inputs_unchanged'] is True
        stage=receipt['stages'][0]; assert stage['status']=='PASS'
        qbase=str(EVID/'qualification')
        put(qbase+'/receipt.json',receipt_b); put(qbase+'/compiled-inputs-before.json',compiled_b)
        validator_b=q.read('qualification/validator-attempt.py'); put(qbase+'/validator-attempt.py',validator_b)
        log_b=q.read('qualification/batch-01-gym/build.log'); put(qbase+'/build.log',log_b)
        classes={k:v['tests'] for k,v in stage['class_totals'].items()}
        xml_files={}
        for row in stage['test_xml']:
            cls=row['name'].split('.ferocity.')[-1].removesuffix('.xml')
            b=q.read('qualification/batch-01-gym/'+row['name']); assert sha(b)==row['sha256']
            put(qbase+'/'+row['name'],b); xml_files[cls]={'path':qbase+'/'+row['name'],'sha256':row['sha256']}
        required={'FerocityObservationBoundaryTest':28,'FerocityStackSourceObservationTest':12,'FerocityTriggerOrderObservationTest':6,'FerocityPriorityObservationTest':4,'FerocityTrialJournalTest':20,'FerocityCardDefinitionBundleTest':16,'FerocityInlineTokenProvenanceTest':12,'ArtifactControlPolicyTest':24,'RedMadnessPilotScenarioTest':24,'FerocityDevelopmentAdmissionTest':14,'FerocityResourceBoundaryTest':2}
        assert all(classes.get(k)==v for k,v in required.items())

        bundle_b=e.read('export-output/definition-bundle.json'); inline_b=e.read('export-output/inline-token-admission.json')
        pins_b=e.read('export-output/artifact-validation-pins.json'); classpath_b=e.read('prelaunch/class-path.json'); material_b=e.read('prelaunch/material.json')
        assert sha(bundle_b)==BUNDLE_SHA and sha(inline_b)==INLINE_SHA and sha(classpath_b)==CLASSPATH_SHA
        bundle=json.loads(bundle_b); pins=json.loads(pins_b); classpath=json.loads(classpath_b); material=json.loads(material_b)
        assert len(bundle['definitionsInRegistrationOrder'])==36 and len(bundle['registryBindings'])==68 and len(classpath)==56
        rbase=str(EVID/'runtime'); put(rbase+'/definition-bundle.json',bundle_b); put(rbase+'/inline-token-admission.json',inline_b)
        put(rbase+'/artifact-validation-pins.json',pins_b); put(rbase+'/class-path.json',classpath_b)

        wdbase=str(EVID/'watchdog')
        wd_members={'source':'source/trial_watchdog.py','acceptance':'ACCEPTANCE.json','runReceipt':'RUN_RECEIPT.json','authorReceipt':'AUTHOR_RECEIPT.json','processClaim':'fixtures/normal-exit/supervisor/fixed-run/claim.json'}
        wd_files={}
        for key,rel in wd_members.items():
            b=w.read(WD_PREFIX+rel); put(wdbase+'/'+rel,b); wd_files[key]={'path':wdbase+'/'+rel,'sha256':sha(b)}
        assert wd_files['source']['sha256']=='803a03aae3f63ffb114d9a8b56d4bf108b2f5f71f051057e204220f5f52adb37'
        assert wd_files['acceptance']['sha256']=='b1d779398aff49d6c4075d9c055c197a165fac7b415052e3731ae908c46244c1'
        assert wd_files['runReceipt']['sha256']=='d9c98fac0ab2db9be7da54fefa7e383cdd82937078c81308faa6202a572a9042'
        assert wd_files['authorReceipt']['sha256']=='077447186d7979210bc58dae40701661663c7c467706ac4b6de00f34805f6e48'
        assert wd_files['processClaim']['sha256']=='cd03cc4c456bc3be73d5c95b2823ea182c447b09719d7e0d910a36735cad79e5'

    protocols=material['protocolFiles']; policies=material['policies']; deps=dict(pins['dependencySha256'])
    assert sha(canon(protocols))==pins['protocolSha256']
    for k,v in pins['policySha256'].items(): assert sha(canon(policies[k]))==v
    assert deps['ferocity/runtime-card-definition-bundle/v1']==BUNDLE_SHA
    assert deps['ferocity/runtime-classpath/v1']==CLASSPATH_SHA and deps['ferocity/runtime-java-executable/v1']==JAVA_SHA
    inline_paths={'ferocity/inline-source/GiftDsl.kt':'mtg-sdk/src/main/kotlin/com/wingedsheep/sdk/dsl/mechanics/GiftDsl.kt','ferocity/inline-source/CreateTokenExecutor.kt':'rules-engine/src/main/kotlin/com/wingedsheep/engine/handlers/effects/token/CreateTokenExecutor.kt','ferocity/inline-source/TokenCreationReplacementHelper.kt':'rules-engine/src/main/kotlin/com/wingedsheep/engine/handlers/effects/token/TokenCreationReplacementHelper.kt','ferocity/inline-source/StackResolver.kt':'rules-engine/src/main/kotlin/com/wingedsheep/engine/mechanics/stack/StackResolver.kt'}
    dep_files={}; special={'ferocity/runtime-card-definition-bundle/v1','ferocity/runtime-classpath/v1','ferocity/runtime-java-executable/v1'}
    for k,v in deps.items():
        if k in special: continue
        if k=='ferocity/inline-tokens/gift-fish/v1': p=str(EVID/'runtime/inline-token-admission.json')
        elif k.startswith('ferocity/card-source/'): p=k[len('ferocity/card-source/'):]; assert compiled[p]==v
        elif k in inline_paths: p=inline_paths[k]; assert compiled[p]==v
        else: raise AssertionError(k)
        dep_files[k]={'path':p,'sha256':v}

    assert fsha(ROOT/'ferocity-recycling/RULES_LEGALITY_AUDIT.md')==RULES_SHA
    decks={'A3-F4':{'path':'ferocity-recycling/decks/candidates/A3-F4.json','sha256':'41d3ea465858649cea3aa21d0ea7d82513c6461a3f7d94c98c089e04c4ea2ad7'},'A3-N0':{'path':'ferocity-recycling/decks/comparators/A3-N0.json','sha256':'95aa88b0f66524250588fdb0226e9792b9bef2f8dd0e411809689aec04f73aea'},'mono_red_madness_mistertwin_20260924':{'path':'ferocity-recycling/decks/benchmarks/mono_red_madness_mistertwin_20260924.json','sha256':'9880736a719b2758f48b5f0a254a05d870a061146b9135d74be1581d7fd1ea2a'}}
    for x in decks.values(): assert fsha(ROOT/x['path'])==x['sha256']
    for p,h in protocols.items(): assert fsha(ROOT/p)==h
    for policy in policies.values():
        for p,h in policy['files'].items(): assert compiled[p]==h and fsha(ROOT/p)==h

    gate_id='source08-gym-36268324461'
    gate={'id':gate_id,'receipt':{'path':str(EVID/'qualification/receipt.json'),'sha256':sha(receipt_b)},'compiledInputs':{'path':str(EVID/'qualification/compiled-inputs-before.json'),'sha256':COMPILED_FILE_SHA},'classes':classes,'xmlFiles':xml_files,'validator':{'path':str(EVID/'qualification/validator-attempt.py'),'sha256':sha(validator_b)},'logs':{k:{'path':str(EVID/'qualification/build.log'),'sha256':sha(log_b)} for k in classes}}
    ref=lambda c:{'gateId':gate_id,'className':c}
    names=sorted({json.loads(x['definition']['wireJson'])['name'] for x in bundle['definitionsInRegistrationOrder']}); assert len(names)==36
    coverage={n:[ref('FerocityCardDefinitionBundleTest')] for n in names}
    mechanics={'mechanic:ferocity-complete':['FerocityDiagnosticsTest','ArtifactControlPolicyTest'],'mechanic:shaman-lki-and-priority':['FerocityStackSourceObservationTest','FerocityPriorityObservationTest','ArtifactControlPolicyTest'],'mechanic:toxin-lifelink-clue':['ArtifactControlPolicyTest','FerocityDiagnosticsTest'],'mechanic:combat-damage':['ArtifactControlPolicyTest','RedMadnessPilotScenarioTest'],'mechanic:trigger-ordering':['FerocityTriggerOrderObservationTest'],'mechanic:flashback-all-stack-exits':['RedMadnessPilotScenarioTest','FerocityStackSourceObservationTest'],'mechanic:madness-payment':['FerocityBloodActivationTest','RedMadnessPilotScenarioTest'],'mechanic:blood-discard-draw':['FerocityBloodActivationTest'],'mechanic:nda-return-role-death':['FerocityWickedRoleSourceTest','FerocityDiagnosticsTest'],'mechanic:brew-gift-and-targets':['FerocityInlineTokenProvenanceTest','RedMadnessPilotScenarioTest'],'mechanic:inline-fish-provenance':['FerocityInlineTokenProvenanceTest'],'mechanic:highway-resolution-plot':['RedMadnessPilotScenarioTest'],'runtime:actor-observation':['FerocityObservationBoundaryTest'],'runtime:journal-replay':['FerocityTrialJournalTest'],'runtime:definition-bundle':['FerocityCardDefinitionBundleTest'],'runtime:development-admission':['FerocityDevelopmentAdmissionTest'],'runtime:resource-boundary':['FerocityResourceBoundaryTest'],'policy:artifact-control':['ArtifactControlPolicyTest'],'policy:red-madness':['RedMadnessPilotScenarioTest']}
    for k,cs in mechanics.items(): coverage[k]=[ref(c) for c in cs]
    assert len(coverage)==55 and all(r['className'] in classes for refs in coverage.values() for r in refs)
    watchdog={**wd_files,'pythonExecutable':'/usr/local/python/3.14.2/bin/python3.14','pythonExecutableSha256':'4c275e98e459979b46ff33a08ab0e317db9267fecaffa01d3c70fd0a34711601','wallSeconds':300,'termGraceSeconds':5}
    red='mono_red_madness_mistertwin_20260924'; allocations=[f'FEROCITY_RECYCLING/v0.1/D2/{d}/{red}/{s}/{i}' for d in ('A3-F4','A3-N0') for s in ('play','draw') for i in range(1,5)]
    manifest={'schemaVersion':1,'cellId':'D2-FIRST-A3-RED','stage':'DEVELOPMENT','rulesMode':'VERIFIED_TEXT_PRERELEASE','repositoryPath':REPO_PATH,'source':{'commit':SOURCE,'treeSha256':COMPILED_CANON,'compiledInputs':gate['compiledInputs'],'dependencies':deps,'dependencyFiles':dep_files,'serializerSha256':'5c3e70fea5aeeb0f1b64b216cd0dd5133785388706c56fdbbc27b768ed855042','classPath':classpath,'javaExecutable':JAVA_PATH},'protocolFiles':protocols,'rulesAudit':{'path':'ferocity-recycling/RULES_LEGALITY_AUDIT.md','sha256':RULES_SHA},'decks':decks,'mainDeckSha256':{'A3-F4':'c72947c48bc45f2b31352a662a99e75dfc0f8ede72ecccc69b574a568eae6ba4','A3-N0':'9aa9d3133f29b5793d66e9941da65d5c74d551f2d670b162f547ad0c1ae79298',red:'d3427549613095ae1af366d3a060e94c1eb5be1715337133928c35ed7ecfee94'},'policies':policies,'bundle':{'path':str(EVID/'runtime/definition-bundle.json'),'sha256':BUNDLE_SHA},'inlineTokenAdmission':{'path':str(EVID/'runtime/inline-token-admission.json'),'sha256':INLINE_SHA},'cardDefinitionSha256':pins['cardDefinitionSha256'],'gates':[gate],'coverage':coverage,'watchdog':watchdog,'limits':{'maxSubmittedActions':6000,'maxCompletedPlayerTurns':150,'maxRuntimeMillis':300000},'allocationIds':allocations,'journalDirectory':'ferocity-recycling/evidence/development/D2-first-cell','ledgerDirectory':'ferocity-recycling/allocations/D2-first-cell','supervisorDirectory':'ferocity-recycling/evidence/process/development/D2-first-cell','replacementsAuthorized':0}
    manifest_b=canon(manifest); manifest_rel=str(EVID/'manifest.json'); put(manifest_rel,manifest_b)
    coverage_receipt={'schema':'ferocity-final-development-coverage-derivation-v1','qualification_gate':gate_id,'card_definition_keys':names,'mechanics':mechanics,'coverage':coverage,'required_key_count':55,'all_references_preserved_passing_classes':True}
    (OUT/'coverage-derivation.json').write_bytes(canon(coverage_receipt))
    members=[]
    for p in sorted(x for x in MAT.rglob('*') if x.is_file()): members.append({'path':p.relative_to(MAT).as_posix(),'bytes':p.stat().st_size,'sha256':fsha(p)})
    construction={'schema':'ferocity-final-development-manifest-construction-receipt-v1','status':'CANONICAL_MANIFEST_CANDIDATE_READY_FOR_INDEPENDENT_REVIEW_ONLY','source08_commit':SOURCE,'source08_tree':SOURCE_TREE,'inputs':{'qualification_artifact_sha256':Q_SHA,'offline_export_artifact_sha256':E_SHA,'watchdog_artifact_sha256':W_SHA,'accepted_red_v02_run':36649260154,'accepted_red_v02_pilot_sha256':'6ddd9d1daa60e777775b9f66de777b87bb84034b8973c92589d0f5c2ed40cef5'},'manifest':{'path':manifest_rel,'bytes':len(manifest_b),'sha256':sha(manifest_b),'coverage_keys':len(coverage),'qualification_classes':len(classes),'classpath_entries':len(classpath),'definitions':len(bundle['definitionsInRegistrationOrder'])},'materialized_members':members,'java_started':False,'gradle_started':False,'entropy_read':False,'allocation_read':False,'ledger_created':False,'trial_claim_created':False,'game_initialized':False,'outcomes_exposed':0,'official_counters_delta':0}
    (OUT/'construction-receipt.json').write_bytes(canon(construction)); qzip.unlink()
    print(json.dumps({'manifestSha256':sha(manifest_b),'manifestBytes':len(manifest_b),'coverageKeys':len(coverage),'materializedFiles':len(members),'status':construction['status']},sort_keys=True))
if __name__=='__main__': main()
