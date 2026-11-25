#!/usr/bin/env python3
"""Read back the one published original Manual lint-followup CI artifact.

Read-only Git objects plus a final ls-remote; no fetch, artifact download,
receiver/qualifier invocation, build, source checkout, publication or admission.
The original ZIP is reconstructed unchanged in temporary scratch. Only the
immutable S2 auditor is executed, against safely staged original evidence and
audit dependencies read from immutable S2. Its exact result must equal the
original receiver result and qualification/AUDIT.json.

Required publication directory: N/RECEIVED_CI_01. Required files: ARCHIVE_PARTS,
RECEIPT, META, EXPECTED, GITHUB_RUN, GITHUB_JOB, GITHUB_ARTIFACT, and
RECEIVING_PROVENANCE (all .json), plus the original.partNNNN.b64 files.
EXPECTED and the three full REST-shaped observations must already be pinned at
one pre-transport claim commit D under N/CI_RECEIVE_PLAN_01. Provenance follows
manual-transmission/lint-ci-receiving-provenance/v1, including the original C
receiver and D's CURRENT/tuple. These files are outside the untouched ZIP.

CLI:
  python immutable_ci_readback.py --repo CARRIER --receipt-commit FULL_SHA \
    --directory N/RECEIVED_CI_01 --expected-live-head FULL_SHA \
    --output-json /scratch/ci-readback.json

Exit zero requires a complete successful original CI pass readback. Failure
still writes a report retaining completed original-byte/integrity checks; it
never converts preserved failure into admission. Missing Git objects must be
explicitly hydrated by root separately, preserving this first readback result.
The helper itself must be frozen in C at N/immutable_ci_readback.py before CI.
"""
from pathlib import Path
import argparse
import base64
import datetime as dt
import hashlib
import json
import os
import re
import stat
import subprocess
import sys
import tempfile
import types
import zipfile

SOURCE='82ec335573572edfd2a2e4d734d2f0d81801139f'
TREE='923ec79e1c867eedf35b408153bf60988fbd26b6'
SOURCE_BASIS='2059afe031fb33dbe43a4741a3ca25ed9a2fabd9'
PINS_BLOB='7e30df16c53a59358d507733b2a5443e2a3c9075'
PINS_SHA='b0e8da912f98b75a35c44db4a3e9e3334d941af3891e0bb28281490ab01e7bbd'
AUDIT_SHA='aed497bac5eafd80fc141557768c7697472324d5da087cf1fe3fb66741075e81'
VERIFIER_SHA='23ea2debb05adc95194f2fcc41fc6c84ad3070d76987f5b6b96af061d3ddabb7'
QUALIFIER_SHA='ac04c813d78d83c0b1e7f4b1d48adac960cca1b7dcef01f62370e84e3bc812a4'
ORIGINAL_RECEIVER_SHA='00be55d69206bae346f602c3a633a2056fe77de59c3e0691e689001bc101fbd6'
RECEIVER_SHA='93a44ed39d2d75996cda6629f9b477ae343c06c1c633d744325928354a5d3ee5'
RECEIVER_BLOB='9d03db10865b880ce45a01882324ac838ec7c9c4'
WORKFLOW_SHA='27952f8595294538394573675f6f41c25c7487996aabbd622a390614810b83ce'
WORKFLOW_BLOB='8ec8c449adf4f93730d14197ff3139a136aad05c'
LOCAL_FREEZE_SHA='9311d1579f8de17772aa139073602999999d343bc1e9083b7d45f0719828902d'
CHARTER_COMMIT='86450c1ecb227804dbd45acf5ba28689ce0c61bc'
CHARTER_BLOB='418fb7f5a0ebeed92582f8285e76305c39250efa'
CHARTER_SHA='a9a2f3b775c6231d1a520679492aba4d2bf3bdea0f1b0abe3fc47be39ef0087e'
PROTOCOL_BLOB='a2c26c3b31834a7182bbe0b3a6ceb83922443cd4'
HISTORICAL_CHECKPOINT='62b95ee8f854cb9a29830832c287106ad8baaac4'
PRIOR_CHECKPOINT='618f562154ff10a9da082a144d43c2011703d2e0'
PRIOR_NAMESPACE='experiments/manual-transmission/autonomy/clean-room-recovery-20261124'
NAMESPACE=PRIOR_NAMESPACE+'/lint-followup-20261124'
RECORD='experiments/manual-transmission/research/surge-bushwhacker-consolidated-proposal-20261114'
DIRECTORY=NAMESPACE+'/RECEIVED_CI_01'
PLAN_DIRECTORY=NAMESPACE+'/CI_RECEIVE_PLAN_01'
REPOSITORY='GodaPupa/argentum-batshit-test'
BRANCH='manual-transmission/clean-room-recovery-20261124'
REF='refs/heads/'+BRANCH
EPOCH='manual-lint-followup-20261124-01'
OWNER='manual-lint-foreground-20261124'
WORKFLOW='.github/workflows/manual-lint-followup-surge-bushwhacker-20261124.yml'
ARTIFACT_PREFIX='manual-lint-followup-candidate02-20261124-'
READBACK_PATH=NAMESPACE+'/immutable_ci_readback.py'
RECEIVER_PATH=NAMESPACE+'/receive_clean_room.py'
CHUNK=450000
PASS='RAW_AUDIT_PASS_READBACK_PENDING'
FAIL='FAILED_OR_INCOMPLETE_ORIGINAL_PRESERVED'
QUALIFICATION_PASS='ALL_LOCAL_GATES_PASS_ORIGINAL_RECEIVING_REQUIRED'
CI_FREEZE_KEYS={'validation','validation_tree','workflow_path','workflow_blob','workflow_sha256',
                'local_freeze_sha256','receiver_sha256','receiver_blob'}
META_KEYS=('repository','branch','epoch','candidate_index','event','source','source_tree','validation',
           'validation_tree','workflow_blob','workflow_sha256','protocol_blob','candidate_pins_blob',
           'run_id','job_id','artifact_id','artifact_name','artifact_digest','artifact_size','attempt',
           'run_number','status','job_status','conclusion','job_conclusion','already_received',
           'fresh_reconciliation','sole_writer','automation_disabled','first_parent','workflow_path',
           'recovery_id','charter_commit','charter_blob','charter_sha256','lane','attempt_id','prior_receipt_count')
API_FILES={'run':'GITHUB_RUN.json','job':'GITHUB_JOB.json','artifact':'GITHUB_ARTIFACT.json'}
CORE_FILES={'ARCHIVE_PARTS.json','RECEIPT.json','META.json','EXPECTED.json','RECEIVING_PROVENANCE.json',*API_FILES.values()}
AUDIT_INPUTS=(RECORD+'/audit_reports.py',RECORD+'/TEST_MATRIX.json',RECORD+'/COMPILE_WITNESSES.json',
              RECORD+'/ACCEPTED_INVENTORY.json.gz.b64',
              'experiments/manual-transmission/research/reckless-own-prior-spell-diagnostic-proposal-20261106/PROTOCOL.json',
              'experiments/manual-transmission/research/final-compatibility-inventory-r1-20261027/CLIENT64_CONTRACT.json')


def need(condition,message):
    if not condition:raise ValueError(message)


def sha(data):return hashlib.sha256(data).hexdigest()


def exact_int(value,expected,label):
    need(type(value) is int and value==expected,label)


def oid(value,label):
    need(isinstance(value,str) and re.fullmatch(r'[0-9a-f]{40}',value) is not None,'full immutable Git ID required: '+label)
    return value


def hash_string(value):return isinstance(value,str) and re.fullmatch(r'[0-9a-f]{64}',value) is not None


def safe_name(name):
    need(isinstance(name,str) and name,'empty/non-string relative path')
    need(not any(ord(c)<32 or ord(c)==127 for c in name),'control character in relative path')
    need('\\' not in name and ':' not in name and all(part not in ('','.','..') for part in name.split('/')),'unsafe relative path')
    return name


def parse(data):
    def pairs(items):
        result={}
        for key,value in items:
            need(key not in result,'duplicate JSON key: '+key);result[key]=value
        return result
    return json.loads(data.decode('utf-8'),object_pairs_hook=pairs,
                      parse_constant=lambda _:(_ for _ in ()).throw(ValueError('nonfinite JSON number')))


def manifest_map(data):
    value=parse(data);need(isinstance(value,dict),'manifest must be a mapping')
    for name,digest in value.items():safe_name(name);need(hash_string(digest),'invalid manifest digest: '+name)
    return value


def audit_counts(value):
    mutants={'M01-surge-price':5,'M02-red-payment':20,'M03-paid-guard':32,'M04-other-filter':32,
             'M05-controller-filter':32,'M06-toughness':32,'M07-granted-haste':32,'M08-duration':32,
             'M09-intrinsic-haste':32,'M10-entity-only-exclusion':10,'M11-exclude-none':10,
             'M12-followed-origin':10,'M13-missing-choice':32,'M14-copy-choice':20}
    need(isinstance(value,dict) and set(value)==set(mutants)|{'normal','restored','cards','ordinary','client'},'exact raw audit gate set')
    def rows(name,wanted):
        observed=value[name];need(isinstance(observed,dict) and set(observed)==set(wanted),'raw audit fields: '+name)
        for key,number in wanted.items():exact_int(observed[key],number,'raw audit count: '+name+'/'+key)
    for name in ('normal','restored'):rows(name,dict(rows=67,skips=0,failures=0))
    rows('cards',dict(rows=401,skips=0,failures=0))
    rows('ordinary',dict(rows=19429,skips=43,failures=0))
    for name,count in mutants.items():rows(name,dict(rows=count,executed=2,skips=count-2,failures=1))
    client=value['client'];need(isinstance(client,dict) and set(client)=={'focused','full','typecheck'},'client audit fields')
    exact_int(client['focused'],70,'focused client count')
    need(type(client['full']) is int and client['full']>=70 and client['typecheck'] is True,'full client/typecheck audit')


class Reader:
    def __init__(self,args):
        self.args=args
        self.env=dict(os.environ,GIT_OPTIONAL_LOCKS='0',GIT_NO_LAZY_FETCH='1')
        self.cache={}
        self.report={'schema':'manual-transmission/immutable-ci-readback/v1',
                     'readback_program_sha256':sha(Path(__file__).read_bytes()),
                     'started_utc':dt.datetime.now(dt.timezone.utc).isoformat(),
                     'receipt_commit':args.receipt_commit,'directory':args.directory,
                     'expected_live_head':args.expected_live_head,'source':SOURCE,'source_tree':TREE,
                     'repository':REPOSITORY,'branch':BRANCH,'recovery_id':EPOCH,'checks':{},
                     'artifact_downloads':0,'receiver_invocations':0,'qualification_invocations':0,
                     'builds_or_tests_invoked':0,'source_verifier_invocations':0,'read_only_auditor_invocations':0,
                     'ci_activations':0,'admitted':False,'phase2_games':0,'phase2_seeds':0}

    def git(self,*arguments):
        result=subprocess.run(['git','--no-replace-objects','--no-lazy-fetch','-C',str(self.args.repo),*arguments],
                              stdout=subprocess.PIPE,stderr=subprocess.PIPE,env=self.env,timeout=60)
        need(result.returncode==0,'read-only Git operation failed (exit '+str(result.returncode)+')')
        return result.stdout

    def raw(self,commit,path):
        oid(commit,'raw commit');safe_name(path)
        return self.git('show','--no-ext-diff','--no-textconv',commit+':'+path)

    def text(self,*arguments):return self.git(*arguments).decode().strip()

    def ancestor(self,parent,child,label):
        oid(parent,label+' ancestor');oid(child,label+' descendant')
        need(self.text('merge-base','--is-ancestor',parent,child)=='',label)

    def file(self,name):
        safe_name(name)
        if name not in self.cache:self.cache[name]=self.raw(self.args.receipt_commit,self.args.directory+'/'+name)
        return self.cache[name]

    def tree(self,commit,directory):
        oid(commit,'tree commit');safe_name(directory)
        result={}
        for item in self.git('ls-tree','-rz','--full-tree',commit,'--',directory).split(b'\0'):
            if not item:continue
            header,path=item.split(b'\t',1);mode,kind,blob=header.decode().split(' ');path=path.decode()
            need(path.startswith(directory+'/'),'Git subtree escaped required prefix')
            relative=safe_name(path[len(directory)+1:])
            need(relative not in result and kind=='blob' and mode in ('100644','100755'),'nonregular/duplicate publication member')
            result[relative]={'mode':mode,'blob':oid(blob,'publication blob')}
        return result

    def stage(self,name,action):
        try:
            result=action();self.report['checks'][name]={'state':'PASS'};return result
        except Exception as exc:
            self.report['checks'][name]={'state':'FAIL','error':str(exc)};return None

    def publication(self):
        need(self.text('cat-file','-t',self.args.receipt_commit)=='commit','receipt object must be a commit')
        members=self.tree(self.args.receipt_commit,self.args.directory)
        need(CORE_FILES<=set(members),'missing original receiving publication records')
        self.publication_members=members
        self.report['publication_files']=len(members)
        return True

    def source(self):
        need(self.text('rev-parse',SOURCE+'^{tree}')==TREE,'exact immutable S2 tree')
        need(self.text('show','-s','--format=%P',SOURCE)==SOURCE_BASIS,'S2 sole predecessor parent')
        permitted={RECORD+'/'+name for name in ('audit_reports.py','verify_dispatch.py','CANDIDATE_PINS.json')}
        permitted|={'mtg-sdk/src/main/kotlin/com/wingedsheep/sdk/serialization/CardLinter.kt',
                    'mtg-sdk/src/test/kotlin/com/wingedsheep/sdk/serialization/SelfAlternativeChoiceLintTest.kt'}
        need(set(self.text('diff','--name-only',SOURCE_BASIS,SOURCE).splitlines())==permitted,'exact five-file S2 source delta')
        need(self.text('rev-parse',SOURCE+':'+RECORD+'/CANDIDATE_PINS.json')==PINS_BLOB,'exact immutable S2 pins blob')
        pins_bytes=self.raw(SOURCE,RECORD+'/CANDIDATE_PINS.json');need(sha(pins_bytes)==PINS_SHA,'exact S2 pins bytes')
        pins=parse(pins_bytes);need(pins['candidate_index']=='02' and len(pins['bindings'])==378,'exact378 Candidate02 source bindings')
        for path,pin in pins['bindings'].items():
            safe_name(path);content=self.raw(SOURCE,path)
            need(pin=={'sha256':sha(content),'bytes':len(content)},'immutable source binding '+path)
        for name,wanted in {'audit_reports.py':AUDIT_SHA,'verify_dispatch.py':VERIFIER_SHA,
                            'qualify.py':QUALIFIER_SHA,'receive.py':ORIGINAL_RECEIVER_SHA}.items():
            need(sha(self.raw(SOURCE,RECORD+'/'+name))==wanted,'frozen source helper '+name)
        need(self.text('rev-parse',SOURCE+':'+RECORD+'/PROTOCOL.json')==PROTOCOL_BLOB,'frozen protocol blob')
        charter=self.raw(CHARTER_COMMIT,NAMESPACE+'/CHARTER.json')
        need(self.text('rev-parse',CHARTER_COMMIT+':'+NAMESPACE+'/CHARTER.json')==CHARTER_BLOB and sha(charter)==CHARTER_SHA,'exact immutable charter')
        self.pins=pins;self.pins_bytes=pins_bytes;self.charter_bytes=charter;self.charter=parse(charter)
        self.report['source_bindings_verified']=378
        self.report['source_pins_sha256']=PINS_SHA
        return True

    def metadata(self):
        meta=parse(self.file('META.json'));expected=parse(self.file('EXPECTED.json'))
        for name,value in [('META',meta),('EXPECTED',expected)]:
            need(isinstance(value,dict) and set(value)==set(META_KEYS),name+' exact38 metadata fields')
            for key in ('run_id','job_id','artifact_id','artifact_size'):
                need(type(value[key]) is int and value[key]>0,name+' positive integer '+key)
            for key in ('attempt','run_number'):exact_int(value[key],1,name+' '+key)
            exact_int(value['prior_receipt_count'],0,name+' prior receipt count')
            for key in ('event','first_parent','source','source_tree','validation','validation_tree','workflow_blob','protocol_blob','candidate_pins_blob','charter_commit','charter_blob'):oid(value[key],key)
            need(re.fullmatch(r'sha256:[0-9a-f]{64}',value['artifact_digest']) is not None,name+' API artifact digest')
            for key in ('workflow_sha256','charter_sha256'):need(hash_string(value[key]),name+' SHA256 '+key)
            for key in ('fresh_reconciliation','sole_writer','automation_disabled'):need(value[key] is True,name+' fresh receiving gate '+key)
            need(value['already_received'] is False,name+' no prior original receipt')
            for key in ('status','job_status'):need(value[key]=='completed',name+' terminal '+key)
        need(meta==expected,'transport metadata differs from independently frozen expectation')
        fixed=dict(repository=REPOSITORY,branch=BRANCH,epoch=EPOCH,candidate_index='02',source=SOURCE,source_tree=TREE,
                   candidate_pins_blob=PINS_BLOB,protocol_blob=PROTOCOL_BLOB,workflow_path=WORKFLOW,
                   workflow_blob=WORKFLOW_BLOB,workflow_sha256=WORKFLOW_SHA,recovery_id=EPOCH,
                   charter_commit=CHARTER_COMMIT,charter_blob=CHARTER_BLOB,charter_sha256=CHARTER_SHA,
                   lane='ci',attempt_id=EPOCH+'/ci-01')
        need(all(meta[key]==value for key,value in fixed.items()),'exact frozen new-epoch metadata')
        need(meta['artifact_name']==ARTIFACT_PREFIX+str(meta['run_id']),'exact actual run artifact name')
        self.meta=meta;self.expected=expected
        self.report['metadata']={key:meta[key] for key in ('event','first_parent','validation','run_id','job_id','artifact_id','artifact_name','artifact_digest','artifact_size','conclusion','job_conclusion')}
        self.report['metadata_file_sha256']={name:sha(self.file(name)) for name in ['META.json','EXPECTED.json',*API_FILES.values(),'RECEIVING_PROVENANCE.json']}
        return True

    def topology(self):
        m=self.meta;first=m['first_parent'];validation=m['validation'];event=m['event']
        need(self.text('show','-s','--format=%P',event).split()==[first,validation],'E ordered parents must be C,V')
        need(self.text('show','-s','--format=%P',validation)==SOURCE,'V sole parent must be S2')
        need(self.text('diff','--name-only',first,event).splitlines()==[WORKFLOW],'C to E changes only new workflow')
        need(self.text('diff','--name-only',SOURCE,validation).splitlines()==[WORKFLOW],'S2 to V changes only new workflow')
        need(self.text('rev-parse',validation+'^{tree}')==m['validation_tree'],'exact validation tree')
        self.ancestor(CHARTER_COMMIT,first,'carrier descends from charter')
        self.ancestor(SOURCE,first,'carrier contains frozen S2')
        need(self.raw(first,NAMESPACE+'/CHARTER.json')==self.charter_bytes,'carrier charter unchanged')
        historical_path='experiments/manual-transmission/autonomy/CURRENT.json'
        historical=self.raw(HISTORICAL_CHECKPOINT,historical_path)
        need(self.raw(first,historical_path)==historical,'original historical ledger unchanged')
        need(self.raw(first,PRIOR_NAMESPACE+'/CURRENT.json')==self.raw(PRIOR_CHECKPOINT,PRIOR_NAMESPACE+'/CURRENT.json'),'previous failed clean-room ledger unchanged')
        changes=self.text('diff','--name-only',PRIOR_CHECKPOINT,first,'--',PRIOR_NAMESPACE).splitlines()
        need(all(path.startswith(NAMESPACE+'/') for path in changes),'every previous clean-room record remains unchanged')
        current=parse(self.raw(first,NAMESPACE+'/CURRENT.json'))
        need(current['recovery_id']==EPOCH and current['branch']==BRANCH and current['status']=='CI_RESERVED','original prospective CI reservation')
        need(current['charter']==NAMESPACE+'/CHARTER.json' and current['charter_sha256']==CHARTER_SHA,'CI current exact charter binding')
        need(current['owner']==current['lease']['owner']==OWNER,'original CI sole owner')
        need(current['lease']['sole_source_writer'] is True and current['lease']['scheduled_executor_enabled'] is False and current['lease']['writable_ref']==REF,'original CI lease scope')
        for key in ('preflight_reserved','preflight_started','preflight_completed','local_reserved','local_started','local_completed','ci_activated'):exact_int(current['attempts'][key],1,'C attempt counter '+key)
        exact_int(current['attempts']['original_artifact_downloads'],0,'C predates transport claim')
        exact_int(current['prior_candidate02_ci_activations'],0,'historical CI activations remain zero')
        need(current['admitted'] is False and current['safeguards']==self.charter['safeguards'],'CI pre-admission safeguards')
        exact_int(current['safeguards']['phase2_games'],0,'CI Phase2 games');exact_int(current['safeguards']['phase2_seeds'],0,'CI Phase2 seeds')
        q=current['source_freeze']
        for key in ('source','source_tree','candidate_index','candidate_pins_blob','validation','validation_tree','workflow_path','workflow_blob','workflow_sha256'):
            need(q[key]==m[key],'frozen CI source metadata '+key)
        need(q['source_basis']==SOURCE_BASIS,'CI source predecessor');exact_int(q['binding_count'],378,'CI frozen binding count')
        workflow=self.raw(event,WORKFLOW)
        need(workflow==self.raw(validation,WORKFLOW)==self.raw(first,NAMESPACE+'/CI_ACTIVATION.yml') and sha(workflow)==WORKFLOW_SHA,'exact frozen C/V/E workflow bytes')
        need(self.text('rev-parse',event+':'+WORKFLOW)==self.text('rev-parse',validation+':'+WORKFLOW)==WORKFLOW_BLOB,'exact operative workflow blob')
        local_freeze_bytes=self.raw(first,NAMESPACE+'/SOURCE_FREEZE.json')
        need(sha(local_freeze_bytes)==LOCAL_FREEZE_SHA==q['local_freeze_sha256'],'original local source freeze bytes')
        local_freeze=parse(local_freeze_bytes)['qualification']
        need(set(q)==set(local_freeze)|CI_FREEZE_KEYS and all(q[key]==value for key,value in local_freeze.items()),'CI preserves every local freeze field with exactly eight additions')
        receiver=self.raw(first,RECEIVER_PATH)
        need(sha(receiver)==RECEIVER_SHA==q['receiver_sha256'] and self.text('rev-parse',first+':'+RECEIVER_PATH)==RECEIVER_BLOB==q['receiver_blob'],'prospectively frozen exact receiver')
        need(self.raw(first,READBACK_PATH)==Path(__file__).read_bytes(),'executing readback helper was frozen before activation')
        local=current['local_result'];local_commit=oid(local['receipt_commit'],'local receipt commit')
        local_path=NAMESPACE+'/LOCAL_QUALIFICATION_01/LOCAL_RECEIPT.json'
        readback_path=NAMESPACE+'/LOCAL_QUALIFICATION_01/READBACK_VERIFICATION.json'
        need(local['receipt_path']==local_path and local['readback_path']==readback_path,'exact prior local proof paths')
        self.ancestor(CHARTER_COMMIT,local_commit,'local receipt belongs to this followup')
        self.ancestor(local_commit,first,'local receipt precedes CI')
        local_bytes=self.raw(local_commit,local_path)
        need(self.raw(first,local_path)==local_bytes,'original local pass receipt preserved')
        receipt=parse(local_bytes);readback=parse(self.raw(first,readback_path))
        need(receipt['schema']=='manual-transmission/clean-room-local-receipt/v1' and readback['schema']=='manual-transmission/immutable-local-readback/v1','actual local receipt and readback schemas')
        need(receipt['state']==local['state']==QUALIFICATION_PASS and receipt['source']==local['source']==SOURCE,'exact local pass source and result')
        need(receipt['source_basis']==SOURCE_BASIS and receipt['all_frozen_local_gates_pass'] is True,'every local gate passed')
        exact_int(receipt['source_bindings_verified'],378,'local pass binding count')
        need(receipt['archive_sha256']==local['archive_sha256'] and local['immutable_readback']=='PASS','local archive/readback identity')
        need(readback['state']=='ARCHIVE_READBACK_PASS_LOCAL_QUALIFICATION_PASS_CI_REQUIRED','actual local readback successful state')
        for key in ('complete_readback','published_local_qualification_pass_validated','exact_original_archive_bytes_verified',
                    'zip_members_and_crc_verified','outer_manifest_verified','nested_manifest_verified',
                    'receipt_identity_verified','receipt_claim_verified','live_branch_verified'):
            need(readback[key] is True,'actual local readback flag '+key)
        need(readback['expected_source']==SOURCE and readback['receipt_commit']==local_commit and readback['archive']['sha256']==local['archive_sha256'],'actual local readback exact source/receipt/archive')
        need(readback['checks'] and all(value['state']=='PASS' for value in readback['checks'].values()),'actual local readback has no failed checks')
        self.q=q;self.current=current;self.grant=parse(historical)['surge_bushwhacker_production_authorization']
        need(self.grant['explicit_user_grant'] is True and self.grant['protocol_blob']==PROTOCOL_BLOB,'immutable original protocol grant')
        self.report['topology']={'source':SOURCE,'validation':validation,'carrier':first,'event':event,
                                 'receiver_sha256':RECEIVER_SHA,'workflow_sha256':WORKFLOW_SHA,
                                 'readback_program_present_before_activation':True,'local_pass_readback_verified':True}
        return True

    def provenance(self):
        value=parse(self.file('RECEIVING_PROVENANCE.json'))
        need(value['schema']=='manual-transmission/lint-ci-receiving-provenance/v1','CI provenance schema')
        claim=value['download_reservation'];commit=oid(claim['commit'],'download claim commit')
        self.ancestor(self.meta['event'],commit,'download claim follows actual CI event')
        self.ancestor(commit,self.args.receipt_commit,'download claim precedes original receipt publication')
        need(not self.tree(commit,DIRECTORY),'new-epoch receipt directory absent before original transport')
        records={'EXPECTED.json':value['expected']}
        need(set(value['api_observations'])==set(API_FILES),'complete independently pinned API observation set')
        records.update({filename:value['api_observations'][name] for name,filename in API_FILES.items()})
        for filename,binding in records.items():
            need(set(binding)=={'commit','path','sha256'} and binding['commit']==commit and binding['path']==PLAN_DIRECTORY+'/'+filename,'single pre-transport metadata binding '+filename)
            original=self.raw(commit,binding['path'])
            need(hash_string(binding['sha256']) and sha(original)==binding['sha256'] and original==self.file(filename),'original independently pinned observation unchanged '+filename)
        need(claim['current_path']==NAMESPACE+'/CURRENT.json','download claim CURRENT path')
        current_bytes=self.raw(commit,claim['current_path'])
        need(sha(current_bytes)==claim['current_sha256'],'immutable download claim CURRENT hash')
        current=parse(current_bytes)
        need(current['status']=='ORIGINAL_ARTIFACT_DOWNLOAD_RESERVED' and current['recovery_id']==EPOCH and current['branch']==BRANCH,'actual pre-transport claim state')
        need(current['source_freeze']==self.q and current['admitted'] is False,'transport claim preserves CI freeze and admission boundary')
        need(current['owner']==current['lease']['owner']==OWNER and current['lease']['sole_source_writer'] is True and current['lease']['scheduled_executor_enabled'] is False and current['lease']['writable_ref']==REF,'transport claim sole foreground scope')
        for key in ('preflight_reserved','preflight_started','preflight_completed','local_reserved','local_started','local_completed','ci_activated','original_artifact_downloads'):
            exact_int(current['attempts'][key],1,'download claim counter '+key)
        exact_int(current['prior_candidate02_ci_activations'],0,'transport no historical activation reset')
        need(current['safeguards']==self.charter['safeguards'],'transport claim safeguards')
        reservation=current['ci_receiving_reservation']
        expected_tuple={key:self.meta[key] for key in ('run_id','job_id','artifact_id','artifact_digest','artifact_size','attempt_id','prior_receipt_count')}
        expected_tuple.update(original_artifact_downloads=1,expected_path=PLAN_DIRECTORY+'/EXPECTED.json',expected_sha256=sha(self.file('EXPECTED.json')))
        need(set(reservation)==set(expected_tuple) and reservation==expected_tuple,'exact pre-transport CURRENT artifact tuple and independent expectation')
        for key in ('run_id','job_id','artifact_id','artifact_size','prior_receipt_count','original_artifact_downloads'):
            exact_int(reservation[key],expected_tuple[key],'typed transport reservation '+key)
        for key in ('run_id','job_id','artifact_id'):
            exact_int(claim[key],self.meta[key],'provenance claimed '+key)
        exact_int(claim['original_artifact_downloads'],1,'one original artifact transport claim')
        receiver=value['receiver']
        need(receiver==dict(commit=self.meta['first_parent'],path=RECEIVER_PATH,blob=RECEIVER_BLOB,sha256=RECEIVER_SHA),'exact prospective receiver provenance')
        changes=self.text('diff','--name-only',self.meta['event'],commit).splitlines()
        need(all(path.startswith(NAMESPACE+'/') for path in changes),'pre-transport records cannot alter activated workflow or source')
        changes=self.text('diff','--name-only',commit,self.args.receipt_commit).splitlines()
        need(all(path.startswith(NAMESPACE+'/') for path in changes),'receipt publication remains within the new operational namespace')
        published_current=parse(self.raw(self.args.receipt_commit,NAMESPACE+'/CURRENT.json'))
        need(published_current['recovery_id']==EPOCH and published_current['branch']==BRANCH and published_current['source_freeze']==self.q,'receipt publication preserves exact CI freeze')
        exact_int(published_current['attempts']['ci_activated'],1,'published single CI activation')
        exact_int(published_current['attempts']['original_artifact_downloads'],1,'published single original transport')
        need(published_current['admitted'] is False,'receipt publication remains pre-admission')
        self.report['pretransport_provenance']={'claim_commit':commit,'expected_sha256':sha(self.file('EXPECTED.json')),
                                              'observation_sha256':{key:sha(self.file(name)) for key,name in API_FILES.items()},
                                              'receiver_sha256':RECEIVER_SHA,'original_transport_claimed':1,
                                              'preexisting_new_epoch_receipts':0}
        return True

    def api(self):
        run=parse(self.file(API_FILES['run']));job=parse(self.file(API_FILES['job']));artifact=parse(self.file(API_FILES['artifact']));m=self.meta
        exact_int(run['id'],m['run_id'],'API actual run ID');exact_int(run['run_attempt'],1,'API run attempt');exact_int(run['run_number'],1,'API run number')
        need(run['repository']['full_name']==REPOSITORY and run['head_branch']==BRANCH and run['head_sha']==m['event'],'API run repository/branch/event')
        need(run['event']=='push' and run['path']==WORKFLOW and run['status']==m['status'] and run['conclusion']==m['conclusion'],'API actual workflow run and conclusion')
        exact_int(job['id'],m['job_id'],'API actual job ID');exact_int(job['run_id'],m['run_id'],'API job belongs to actual run')
        need(job['head_sha']==m['event'] and job['name']=='verify' and job['status']==m['job_status'] and job['conclusion']==m['job_conclusion'],'API actual verify job/event/conclusion')
        # Some REST versions omit these redundant fields; run_id + head_sha tie
        # the job to the exact run whose branch and sole attempt are mandatory.
        if 'run_attempt' in job:exact_int(job['run_attempt'],1,'API job attempt when supplied')
        if 'head_branch' in job:need(job['head_branch']==BRANCH,'API job branch when supplied')
        exact_int(artifact['id'],m['artifact_id'],'API actual artifact ID')
        exact_int(artifact['size_in_bytes'],m['artifact_size'],'API original artifact byte size')
        need(artifact['name']==m['artifact_name'] and artifact['digest']==m['artifact_digest'] and artifact['expired'] is False,'API original artifact name/digest/availability')
        workflow_run=artifact['workflow_run'];exact_int(workflow_run['id'],m['run_id'],'API artifact belongs to actual run')
        need(workflow_run['head_sha']==m['event'] and workflow_run['head_branch']==BRANCH,'API original artifact event/branch')
        if 'repository_id' in workflow_run:exact_int(workflow_run['repository_id'],run['repository']['id'],'API artifact repository ID')
        self.report['api_identity_verified']={'run_id':m['run_id'],'job_id':m['job_id'],'artifact_id':m['artifact_id'],
                                             'artifact_digest':m['artifact_digest'],'artifact_bytes':m['artifact_size'],
                                             'new_api_requests':0,'observation_fields_match_metadata':True,
                                             'independent_pretransport_binding_verified':self.report['checks'].get('pretransport_independent_provenance',{}).get('state')=='PASS'}
        return True

    def index(self):
        data=self.file('ARCHIVE_PARTS.json');value=parse(data)
        need(isinstance(value,dict) and set(value)=={'bytes','sha256','parts'},'exact archive part index fields')
        need(type(value['bytes']) is int and value['bytes']>0 and hash_string(value['sha256']),'archive positive size and SHA256')
        need(isinstance(value['parts'],list) and len(value['parts'])==(value['bytes']+CHUNK-1)//CHUNK,'exact original part count')
        for number,item in enumerate(value['parts']):
            need(isinstance(item,dict) and set(item)=={'file','bytes','sha256'},'exact part record fields')
            need(item['file']==f'original.part{number:04d}.b64','canonical ordered original part name')
            exact_int(item['bytes'],min(CHUNK,value['bytes']-number*CHUNK),'decoded original part size')
            need(hash_string(item['sha256']),'decoded original part SHA256')
        published={name for name in self.publication_members if re.fullmatch(r'original\.part[0-9]+\.b64',name)}
        need(published=={item['file'] for item in value['parts']},'exact published original part set')
        self.report['parts_index_sha256']=sha(data)
        return value

    def assemble(self,index,archive):
        combined=hashlib.sha256();total=0;parts=[]
        with archive.open('xb') as output:
            for item in index['parts']:
                encoded=self.raw(self.args.receipt_commit,self.args.directory+'/'+item['file']);need(encoded.endswith(b'\n'),'canonical base64 terminal newline')
                raw=base64.b64decode(encoded[:-1],validate=True)
                need(base64.b64encode(raw)+b'\n'==encoded,'canonical exact original base64')
                exact_int(len(raw),item['bytes'],'decoded part byte count');need(sha(raw)==item['sha256'],'decoded original part hash')
                output.write(raw);combined.update(raw);total+=len(raw)
                parts.append(dict(item,encoded_sha256=sha(encoded)))
        need(total==index['bytes'] and combined.hexdigest()==index['sha256'],'assembled original ZIP exact size/hash')
        need(total==self.meta['artifact_size'] and 'sha256:'+combined.hexdigest()==self.meta['artifact_digest'],'original ZIP equals independent API digest and size')
        if 'original.zip' in self.publication_members:
            extra=self.file('original.zip');need(len(extra)==total and sha(extra)==combined.hexdigest(),'published binary ZIP agrees with original parts')
        self.report['archive']={'bytes':total,'sha256':combined.hexdigest(),'repacked':False,'matches_published_metadata':True}
        self.report['parts']=parts
        return True

    def zip_stage(self,archive,destination):
        need(not destination.exists(),'fresh scratch audit evidence destination required')
        destination.mkdir()
        hashes={};sizes={};total=0;directories=0
        with zipfile.ZipFile(archive) as z:
            entries={}
            for info in z.infolist():
                name=safe_name(info.filename[:-1] if info.is_dir() else info.filename)
                need(name not in entries,'duplicate/conflicting ZIP member name')
                need(not info.flag_bits&1,'encrypted ZIP member')
                mode=stat.S_IFMT(info.external_attr>>16)
                if info.is_dir():need(mode in (0,stat.S_IFDIR) and info.file_size==0,'nonempty/non-directory ZIP directory')
                else:need(mode in (0,stat.S_IFREG),'nonregular/symlink ZIP member')
                entries[name]=info
            regular={name for name,info in entries.items() if not info.is_dir()}
            for name in entries:
                parts=name.split('/')
                need(not any('/'.join(parts[:n]) in regular for n in range(1,len(parts))),'ZIP file/directory prefix collision')
            for name,info in entries.items():
                path=destination/name
                if info.is_dir():path.mkdir(parents=True,exist_ok=True);directories+=1;continue
                path.parent.mkdir(parents=True,exist_ok=True)
                digest=hashlib.sha256();length=0
                with z.open(info) as source,path.open('xb') as out:
                    for block in iter(lambda:source.read(1024*1024),b''):
                        out.write(block);digest.update(block);length+=len(block)
                exact_int(length,info.file_size,'original ZIP member length')
                hashes[name]=digest.hexdigest();sizes[name]=length;total+=length
            need(z.testzip() is None,'original ZIP CRC failure')
        need('MANIFEST.json' in hashes and 'qualification/MANIFEST.json' in hashes,'both original ZIP manifests required')
        self.zip_hashes=hashes;self.zip_sizes=sizes;self.evidence=destination
        self.report['zip']={'files':len(hashes),'directories':directories,'uncompressed_bytes':total,
                            'crc':'PASS','safe_unique_regular_files':True,'staged_without_repacking':True}
        return True

    def manifests(self):
        for prefix in ('','qualification/'):
            name=prefix+'MANIFEST.json';declared=manifest_map((self.evidence/name).read_bytes())
            actual={path[len(prefix):]:digest for path,digest in self.zip_hashes.items() if path.startswith(prefix) and path!=name}
            need(declared==actual,'exact original '+name+' member set and hashes')
            self.report['outer_manifest' if not prefix else 'nested_manifest']={'sha256':self.zip_hashes[name],'files':len(actual),'exact':True}
        return True

    def received_claim(self):
        receipt=parse(self.file('RECEIPT.json'));need(isinstance(receipt,dict),'original receiver receipt object');self.receipt=receipt
        self.report['original_receiver_state']=receipt.get('state')
        need(receipt['schema']=='manual-transmission/surge-original-receipt/v1','original CI receipt schema')
        need(receipt['metadata']==self.meta and receipt['original_received_once'] is True and receipt['admitted'] is False,'original CI receipt metadata and boundary')
        need(receipt['state'] in (PASS,FAIL),'recognized original receiver state')
        if receipt['state']==FAIL:
            need(isinstance(receipt.get('blocker'),str) and receipt['blocker'],'failed original receipt must retain its blocker')
            self.report['original_receiver_blocker']=receipt['blocker']
            raise ValueError('Original receiver failed; exact bytes remain preserved and no admission pass is possible')
        need(set(receipt)=={'schema','metadata','original_received_once','admitted','members','audit','state'},'exact successful original receiver receipt fields')
        need(receipt['members']=={name:digest for name,digest in self.zip_hashes.items() if name!='MANIFEST.json'},'receiver member proof equals original ZIP manifest')
        need(self.meta['conclusion']==self.meta['job_conclusion']=='success','failed original CI conclusions cannot pass admission readback')
        audit_counts(receipt['audit'])
        self.report['receipt_sha256']=sha(self.file('RECEIPT.json'))
        return True

    def frozen_evidence(self):
        prefix='qualification/frozen-source/'
        archived={name[len(prefix):] for name in self.zip_hashes if name.startswith(prefix)}
        need(archived==set(self.pins['bindings']),'exact378 archived frozen-source paths')
        need((self.evidence/'qualification/CANDIDATE_PINS.json').read_bytes()==self.pins_bytes,'archived exact S2 source pins bytes')
        for path,pin in self.pins['bindings'].items():
            name=prefix+path
            need(self.zip_hashes[name]==pin['sha256'] and self.zip_sizes[name]==pin['bytes'],'archived immutable source binding '+path)
        outer=(self.evidence/'RECOVERY_EVENT.json').read_bytes()
        inner=(self.evidence/'qualification/RECOVERY_EVENT.json').read_bytes()
        need(outer==inner,'original workflow and qualifier envelope bytes agree')
        event=parse(outer)
        wanted=dict(event=self.meta['event'],first_parent=self.meta['first_parent'],validation=self.meta['validation'],
                    source=SOURCE,qualification=self.q,grant=self.grant,run=str(self.meta['run_id']),attempt=1,
                    lint_followup=dict(recovery_id=EPOCH,branch=BRANCH,charter_commit=CHARTER_COMMIT,
                                       charter_blob=CHARTER_BLOB,lane='ci',attempt=1,attempt_id=EPOCH+'/ci-01'))
        need(event==wanted and 'clean_room' not in event and 'local_execution' not in event,'exclusive actual CI envelope equals independent C/V/E expectation')
        exact_int(event['attempt'],1,'original envelope attempt');exact_int(event['lint_followup']['attempt'],1,'original lint followup attempt')
        exact_int(event['qualification']['binding_count'],378,'original envelope binding count')
        need(type(event['run']) is str and event['run']==str(self.meta['run_id']),'original GitHub run string')
        result=parse((self.evidence/'qualification/RESULT.json').read_bytes())
        need(result==dict(state=QUALIFICATION_PASS,admitted=False),'original qualifier completed all gates without admitting')
        compile_first=parse((self.evidence/'qualification/COMPILE_FIRST.json').read_bytes())
        exact_int(compile_first['forms'],15,'original compile-first forms');need(compile_first['all_compiled'] is True,'original all15 compile forms')
        restoration=parse((self.evidence/'qualification/RESTORATION.json').read_bytes())
        need(restoration['exact_normal_bytes'] is True and restoration['bindings']==self.pins['bindings'],'original complete source restoration')
        need('qualification/FAILURE.txt' not in self.zip_hashes,'original qualifier failure cannot accompany pass')
        recorded=parse((self.evidence/'qualification/AUDIT.json').read_bytes());audit_counts(recorded)
        need(recorded==self.receipt['audit'],'original qualifier and receiver raw audit agree')
        self.recorded_audit=recorded
        self.report['archived_frozen_bindings_verified']=378
        self.report['original_envelope_sha256']=sha(outer)
        self.report['original_audit_sha256']=self.zip_hashes['qualification/AUDIT.json']
        return True

    def independent_audit(self,source_directory):
        need(not source_directory.exists(),'fresh immutable audit staging directory required')
        source_directory.mkdir()
        inputs={}
        for path in AUDIT_INPUTS:
            content=self.raw(SOURCE,path)
            if path in self.pins['bindings']:
                need(self.pins['bindings'][path]=={'sha256':sha(content),'bytes':len(content)},'immutable auditor input binding '+path)
            else:
                need(path.endswith('/reckless-own-prior-spell-diagnostic-proposal-20261106/PROTOCOL.json') and content==self.raw(SOURCE_BASIS,path),'exact unchanged non-binding auditor dependency')
            destination=source_directory/path;destination.parent.mkdir(parents=True,exist_ok=True);destination.write_bytes(content)
            inputs[path]={'sha256':sha(content),'bytes':len(content)}
        auditor_path=source_directory/RECORD/'audit_reports.py';code=auditor_path.read_bytes()
        need(sha(code)==AUDIT_SHA,'execute only the immutable exact S2 auditor')
        module=types.ModuleType('_manual_immutable_ci_auditor');module.__file__=str(auditor_path)
        prior_bytecode=sys.dont_write_bytecode;sys.dont_write_bytecode=True
        self.report['immutable_audit_inputs']=inputs
        try:
            exec(compile(code,str(auditor_path),'exec'),module.__dict__)
            self.report['read_only_auditor_invocations']+=1
            fresh=module.audit_all(self.evidence/'qualification')
            audit_counts(fresh)
            need(fresh==self.receipt['audit']==self.recorded_audit,'fresh immutable auditor equals both original recorded audits')
            self.report['independent_raw_audit']={'state':'PASS','auditor_source':SOURCE,'auditor_sha256':AUDIT_SHA,
                                                  'result':fresh,'qualifier_audit_sha256':self.zip_hashes['qualification/AUDIT.json'],
                                                  'receiver_receipt_sha256':sha(self.file('RECEIPT.json'))}
        except Exception as exc:
            self.report['independent_raw_audit']={'state':'FAIL','auditor_source':SOURCE,'auditor_sha256':AUDIT_SHA,
                                                  'error_type':type(exc).__name__,'error':str(exc)}
            raise
        finally:
            sys.dont_write_bytecode=prior_bytecode
            actual={path.relative_to(self.evidence).as_posix():sha(path.read_bytes()) for path in self.evidence.rglob('*') if path.is_file()}
            unchanged=actual==self.zip_hashes
            self.report['original_staged_members_unchanged_after_audit']=unchanged
            need(unchanged,'read-only audit must preserve every original staged evidence byte')
        return True

    def live(self):
        expected=oid(self.args.expected_live_head,'expected live head')
        need(self.text('cat-file','-t',expected)=='commit','expected live head must be available as immutable commit')
        self.ancestor(self.args.receipt_commit,expected,'live carrier preserves immutable receipt ancestry')
        changes=self.text('diff','--name-only',self.args.receipt_commit,expected).splitlines()
        need(all(path.startswith(NAMESPACE+'/') for path in changes),'live publication preserves frozen workflow/source and all historical namespaces')
        current=self.tree(expected,self.args.directory)
        need(all(current.get(name)==binding for name,binding in self.publication_members.items()),'every published original receiving member unchanged at expected live head')
        self.report['live_original_publication_files_verified']=len(self.publication_members)
        self.report['later_added_publication_files']=sorted(set(current)-set(self.publication_members))
        # This is deliberately the final external observation after object checks.
        lines=[line.split('\t') for line in self.git('ls-remote','--exit-code','--refs',self.args.remote,REF).decode('ascii').splitlines() if line]
        need(len(lines)==1 and len(lines[0])==2 and lines[0][1]==REF,'one exact live branch response')
        observed=lines[0][0]
        self.report['observed_live_head']=observed
        self.report['live_readback_utc']=dt.datetime.now(dt.timezone.utc).isoformat()
        need(observed==expected,'live branch differs from explicitly expected head')
        return True

    def run(self,temporary):
        publication=self.stage('immutable_publication',self.publication)
        source=self.stage('immutable_source_and_charter',self.source)
        metadata=self.stage('independent_metadata',self.metadata) if publication else None
        topology=self.stage('prospective_ci_topology_and_local_pass',self.topology) if metadata and source else None
        provenance=self.stage('pretransport_independent_provenance',self.provenance) if topology else None
        api=self.stage('original_api_observations',self.api) if metadata else None
        index=self.stage('original_archive_part_index',self.index) if publication else None
        assembled=self.stage('original_metadata_bound_archive_bytes',lambda:self.assemble(index,temporary/'original.zip')) if metadata and index else None
        staged=self.stage('original_zip_safety_crc_and_staging',lambda:self.zip_stage(temporary/'original.zip',temporary/'original-evidence')) if assembled else None
        manifests=self.stage('original_outer_and_nested_manifests',self.manifests) if staged else None
        claim=self.stage('original_receiver_receipt',self.received_claim) if staged and metadata else None
        frozen=self.stage('original_envelope_source_and_qualifier_result',self.frozen_evidence) if claim and manifests and topology and source else None
        audit=self.stage('fresh_immutable_raw_report_audit',lambda:self.independent_audit(temporary/'immutable-audit-source')) if frozen and provenance and api else None
        live=self.stage('exact_live_branch_and_original_publication',self.live) if publication else None
        complete=bool(all((publication,source,metadata,topology,provenance,api,index is not None,assembled,staged,manifests,claim,frozen,audit,live)))
        failed_original=bool(getattr(self,'receipt',{}).get('state')==FAIL and self.report.get('original_receiver_blocker'))
        self.report.update(complete_readback=complete,original_ci_pass_readback_verified=complete,
                           exact_original_archive_bytes_verified=bool(assembled),zip_members_and_crc_verified=bool(staged),
                           independent_api_bound_archive_bytes_verified=bool(assembled and api and provenance),
                           outer_and_nested_manifests_verified=bool(manifests),source_and_envelope_verified=bool(frozen),
                           independent_raw_audit_verified=bool(audit),live_branch_verified=bool(live),
                           receiver_failure_preserved=failed_original,scientific_admission=False,
                           state='ORIGINAL_CI_PASS_READBACK_VERIFIED_ADMISSION_PENDING' if complete else
                                 'ORIGINAL_BYTES_VERIFIED_RECEIVING_FAILED' if assembled and failed_original else 'CI_READBACK_INCOMPLETE_OR_INVALID',
                           finished_utc=dt.datetime.now(dt.timezone.utc).isoformat())
        return 0 if complete else 1


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo',type=Path,required=True)
    parser.add_argument('--receipt-commit',required=True)
    parser.add_argument('--directory',required=True)
    parser.add_argument('--expected-live-head',required=True)
    parser.add_argument('--remote',default='origin')
    parser.add_argument('--output-json',type=Path,required=True)
    args=parser.parse_args()
    for label,value in [('source',SOURCE),('tree',TREE),('source basis',SOURCE_BASIS),('pins',PINS_BLOB),
                        ('receiver',RECEIVER_BLOB),('workflow',WORKFLOW_BLOB),('charter',CHARTER_COMMIT),
                        ('receipt',args.receipt_commit),('expected live head',args.expected_live_head)]:oid(value,label)
    for value in (PINS_SHA,AUDIT_SHA,VERIFIER_SHA,QUALIFIER_SHA,ORIGINAL_RECEIVER_SHA,RECEIVER_SHA,WORKFLOW_SHA,LOCAL_FREEZE_SHA,CHARTER_SHA):
        need(hash_string(value),'all immutable expectations must be finalized before use')
    need(args.directory==DIRECTORY,'readback limited to the frozen new-epoch original CI directory')
    need(re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._-]*',args.remote) is not None,'named read-only Git remote required')
    args.repo=args.repo.resolve();output=args.output_json.resolve()
    need(args.repo.is_dir(),'carrier repository directory absent')
    need(not output.is_relative_to(args.repo),'readback report must be outside the repository')
    need(not output.exists(),'refusing to overwrite an existing readback report')
    output.parent.mkdir(parents=True,exist_ok=True)
    reader=Reader(args)
    with tempfile.TemporaryDirectory(prefix='manual-immutable-ci-readback-',dir=output.parent) as location:
        code=reader.run(Path(location))
    with output.open('x',encoding='utf-8') as destination:
        json.dump(reader.report,destination,indent=2,sort_keys=True);destination.write('\n')
    print(json.dumps({key:reader.report[key] for key in ('state','complete_readback','original_ci_pass_readback_verified',
                                                        'exact_original_archive_bytes_verified','independent_raw_audit_verified','live_branch_verified')}))
    return code


if __name__=='__main__':raise SystemExit(main())
