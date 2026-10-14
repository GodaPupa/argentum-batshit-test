#!/usr/bin/env python3
"""Fixed one-workflow, reduced-assurance gate. This script never starts an engine.

Absent request: DENY. Caller JSON/environment carries claims only. The fixed TLS
client independently obtains live owner decisions and provider run identities.
An authenticated owner Environment-settings readback is an explicit prerequisite,
NOT an independent Administration API read, host attestation or global ledger.
Any code-injected fixture returns engineAllowed=false and cannot activate Kotlin.
"""
from __future__ import annotations
import argparse
import base64
from datetime import datetime,timezone
import hashlib
import json
import os
from pathlib import Path
import re
import ssl
import subprocess
import sys
import urllib.request
sys.path.insert(0,str(Path(__file__).resolve().parent))
import gameplay_authority as auth
import one_shot_actions as shot
import successor_protocol as successor

REQUEST='ARGENTUM_SUCCESSOR_REQUEST_V2'
ENGINE_CASE='ARGENTUM_SUCCESSOR_ENGINE_CASE_V2'
ENVIRONMENT='ARGENTUM_SUCCESSOR_OWNER_ENVIRONMENT_READBACK_V2'
MODEL='OWNER_REVIEWED_GITHUB_ENVIRONMENT_ONE_WORKFLOW_NONOFFICIAL_ONLY'
ADAPTER='gym/src/test/kotlin/com/wingedsheep/gym/PlayFirstOneShotPestMonsterTronTest.kt'
HOLD='ARGENTUM_SUCCESSOR_PROVIDER_HOLD_V2'
TOKEN_NAME='ARGENTUM_ONE_SHOT_GITHUB_TOKEN'

class RunnerGitHub:
    """Supported provider-issued read-only token; never a trust flag or issuer.
    No caller URL, redirects, insecure TLS, admin writes or configurable backend.
    """
    def get(self,path):
        shot.require(type(path) is str and (path=='' or bool(re.fullmatch(r'[A-Za-z0-9_./?=&-]+',path))) and '..' not in path,'canonical GitHub API path')
        url='https://api.github.com/repos/'+auth.REPOSITORY+('/'+path if path else '')
        headers={'Accept':'application/vnd.github+json','User-Agent':'Argentum-one-shot-readonly-v1'}
        token=os.environ.get(TOKEN_NAME)
        if token:headers['Authorization']='Bearer '+token
        request=urllib.request.Request(url,headers=headers)
        opener=urllib.request.build_opener(auth.NoRedirect(),urllib.request.HTTPSHandler(context=ssl.create_default_context()))
        with opener.open(request,timeout=20) as response:
            shot.require(response.status==200 and response.geturl()==url,'untrusted provider response')
            data=response.read(4*1024*1024+1)
        shot.require(len(data)<=4*1024*1024,'provider response limit')
        return auth.decode(data)

def source_manifests(root):
    """Git-tracked source bytes only; never load a catalog, pilot or engine."""
    root=Path(root)
    listed=subprocess.check_output(['git','ls-tree','-rz','HEAD'],cwd=root)
    groups={'engine':[],'deck':[],'pilot':[]}
    for entry in listed.split(b'\0'):
        if not entry:continue
        meta,name=entry.split(b'\t');mode,typ,blob=meta.decode().split();name=os.fsdecode(name)
        selected=[]
        if name.startswith(('mtg-sdk/','rules-engine/','mtg-sets/','buildSrc/','gradle/')) or name in ('build.gradle.kts','settings.gradle.kts',ADAPTER):selected.append('engine')
        if name.startswith('gym/src/main/kotlin/com/wingedsheep/gym/matchup/'):selected.append('deck')
        if name.startswith('ai/src/main/'):selected.append('pilot')
        if not selected:continue
        p=root/name;data=os.readlink(p).encode() if mode=='120000' else p.read_bytes()
        shot.require(hashlib.sha1(b'blob '+str(len(data)).encode()+b'\0'+data).hexdigest()==blob,'source manifest blob differs: '+name)
        row=dict(path=name,mode=mode,blob=blob,sha256=shot.digest(data))
        for group in selected:groups[group].append(row)
    shot.require(all(groups.values()),'complete engine/deck/pilot source groups unavailable')
    return {k+'Sha256':shot.digest(shot.canonical(v)) for k,v in groups.items()}

def request_record(data):
    shot.require(type(data) is bytes and 0<len(data)<=256*1024,'missing/bounded external one-shot request; DENY')
    value=auth.decode(data)
    shot.exact(value, {'contract','intentBytesBase64','caseBytesBase64','ownerDecisionId',
        'allocationDecisionId','allocationBodySha256',
        'environmentVerificationId','environmentPolicy','environmentFacts'},'one-shot external request schema')
    shot.require(value['contract']==REQUEST,'external request contract')
    decoded=[]
    for field in ('intentBytesBase64','caseBytesBase64'):
        shot.require(type(value[field]) is str,'exact immutable base64 bytes required')
        try:b=base64.b64decode(value[field],validate=True)
        except (ValueError,TypeError) as error:raise ValueError('invalid immutable byte encoding') from error
        shot.require(base64.b64encode(b).decode()==value[field] and len(b)<=65536,'noncanonical/oversized immutable bytes')
        decoded.append(b)
    frozen=shot.freeze_intent(decoded[0]);case=auth.decode(decoded[1])
    shot.exact(case,{'contract','case','plan'},'exact engine case schema')
    shot.require(case['contract']==ENGINE_CASE,'one-shot engine-case contract')
    inp,plan=case['case'],case['plan'];shot.exact(inp,auth.CASE_KEYS,'engine case inputs schema');shot.exact(plan,auth.PLAN_KEYS,'engine plan cannot depend on future run ID')
    ci=frozen['intent']['case'];source=frozen['intent']['source'];workflow=frozen['intent']['workflow']
    shot.require(inp['scope']==auth.SCOPE and inp['sourceCommit']==source['commit'] and
        inp['gameId']==ci['gameId'] and ci['caseId']==inp['blockId']+'-'+inp['gameId'] and
        type(inp['pestSeat']) is int and inp['pestSeat']==ci['pestSeat'] and
        type(inp['intendedBlockGames']) is int and inp['intendedBlockGames']==1 and
        type(inp['pair']) is int and inp['pair']>0,'engine/case/seat/one-original binding')
    shot.require(bool(re.fullmatch(r'PLAY_FIRST_[A-Z0-9_]{1,80}',inp['blockId'])) and 'B01' not in inp['blockId'] and
        bool(re.fullmatch(r'G[0-9]{2,4}',inp['gameId'])) and inp['gameId']!='G08','B01/G08 excluded')
    seed=inp['fixtureSeedHex'];shot.require(type(seed) is str and bool(re.fullmatch('0x[0-7][0-9a-f]{15}',seed)) and int(seed,16)>0 and
        seed not in ('0x504c415946495253','0x504c4159424c3031','0x504c4159424c3032','0x504c4159424c3033','0x504c4159424c3034'),
        'future explicitly approved nonofficial input required; historical seed labels refused')
    expected=dict(sourceCommit=source['commit'],sourceParent=shot.PARENT,sourceTree=source['tree'],
        validationCommit=source['commit'],validationTree=source['tree'],workflowPath=shot.WORKFLOW,
        workflowBlob=workflow['blob'],ref=shot.REF,event='push',runNumber=1,runAttempt=1)
    shot.require(auth.same_json(plan,expected),'engine source/workflow/ref/original plan differs')
    shot.require(shot.digest(decoded[1])==ci['permittedInputsSha256'],'permitted engine inputs bytes differ')
    shot.integer(value['ownerDecisionId'],'actual owner decision ID missing')
    shot.integer(value['environmentVerificationId'],'actual Environment owner readback ID missing')
    shot.require(value['ownerDecisionId']!=value['environmentVerificationId'],'Environment readback and case decision must be separate')
    shot.verify_environment(value['environmentFacts'],value['environmentPolicy'],shot.REF)
    successor.validate_request(value,frozen,case)
    return value,frozen,decoded[1]

def environment_expected(request,frozen):
    return dict(contract=ENVIRONMENT,intentSha256=frozen['intentSha256'],environment=request['environmentPolicy'],
        configurationSha256=shot.digest(shot.canonical(request['environmentFacts'])),
        ownerInspectedExistingEnvironment=True,requiredReviewerProtectionVerified=True,
        publicationPrerequisiteOnly=True,assuranceModel=MODEL)|successor.binding(request)

def owner_expected(request,frozen,environment_body_sha):
    return shot.owner_expected(frozen,request['environmentPolicy'])|dict(
        environmentVerificationId=request['environmentVerificationId'],environmentVerificationBodySha256=environment_body_sha,
        executionAuthorized=True,assuranceModel=MODEL,formalAdmissionAuthorized=False)|successor.binding(request)

def release_expected(request,frozen,context,hold_sha,env_sha):
    return shot.release_expected(frozen,request['environmentPolicy'],request['ownerDecisionId'],context,hold_sha)|dict(
        environmentVerificationId=request['environmentVerificationId'],environmentVerificationBodySha256=env_sha,
        environmentStillProtected=True,executionAuthorized=True,assuranceModel=MODEL,formalAdmissionAuthorized=False)|successor.binding(request)

def decision(client,ident,contract,expected,now):
    record=client.get('issues/comments/'+str(ident))
    claims,body_sha=auth.decision_claims(record,ident,contract,expected,now)
    shot.require(auth.same_json(record,client.get('issues/comments/'+str(ident))),'decision changed during readback')
    return record,claims,body_sha

def same_comment_list_detail(detail, listed):
    """Strict GitHub issue-comment list/detail correspondence.

    GitHub's list endpoint includes app.client_id absent from the individual
    comment endpoint, while the individual endpoint includes pin:null omitted
    by the list endpoint. Ignore *only* those two observed representation
    differences in this direction. All identity, body, timestamp, issuer,
    minimization, non-null pin, and other app fields remain exact. Never use
    this comparison for a token, decision contract, or arbitrary provider run.
    """
    if type(detail) is not dict or type(listed) is not dict:
        return False
    if type(detail.get('id')) is not int or detail['id'] != listed.get('id'):
        return False
    normalized_detail = dict(detail)
    normalized_listed = dict(listed)
    if 'pin' not in listed and detail.get('pin', object()) is None:
        normalized_detail.pop('pin')
    detail_app = detail.get('performed_via_github_app')
    listed_app = listed.get('performed_via_github_app')
    if type(detail_app) is dict and type(listed_app) is dict and \
            'client_id' not in detail_app and 'client_id' in listed_app:
        client_id = listed_app['client_id']
        if type(client_id) is not str or not client_id:
            return False
        normalized_listed['performed_via_github_app'] = {
            key: value for key, value in listed_app.items() if key != 'client_id'
        }
    return auth.same_json(normalized_detail, normalized_listed)


def history_check(events,records,now):
    shot.require(type(events) is list,'complete current owner history required')
    for record in records:
        shot.require(any(same_comment_list_detail(record,x) for x in events),'authenticated decision missing from history')
    chosen={x['id']:x for x in records};start=min(auth.utc(x['updated_at']) for x in records)
    for event in events:
        if event.get('user',{}).get('id')!=auth.OWNER_ID:continue
        stamp=auth.utc(event.get('updated_at') or event.get('submitted_at') or event.get('created_at'))
        shot.require(stamp<=now,'future owner history')
        if event.get('id') in chosen:shot.require(same_comment_list_detail(chosen[event['id']],event),'contradictory current decision')
        else:shot.require(stamp<start,'subsequent revocation/supersession/consumption; STOP')

def context_from_provider_environment():
    def integer(name):
        value=os.environ.get(name,'');shot.require(bool(re.fullmatch('[1-9][0-9]*',value)),'missing provider context '+name);return int(value)
    return dict(repository=os.environ.get('GITHUB_REPOSITORY'),event=os.environ.get('GITHUB_EVENT_NAME'),
        ref=os.environ.get('GITHUB_REF'),sha=os.environ.get('GITHUB_SHA'),runId=integer('GITHUB_RUN_ID'),
        runNumber=integer('GITHUB_RUN_NUMBER'),runAttempt=integer('GITHUB_RUN_ATTEMPT'),workflowRef=os.environ.get('GITHUB_WORKFLOW_REF'))

def verify_prepublication_absence(client):
    """Before a future authorized push. No write or test branch permission probe."""
    for path in ('git/ref/heads/'+shot.BRANCH,'actions/workflows/'+Path(shot.WORKFLOW).name):
        try:client.get(path)
        except urllib.error.HTTPError as error:
            shot.require(error.code==404,'cannot authenticate prepublication absence; STOP')
        else:raise ValueError('destination ref or workflow already exists; STOP')
    history=client.get('actions/runs?branch='+shot.BRANCH+'&per_page=100&page=1')
    shot.require(type(history) is dict and history.get('total_count')==0 and
        type(history.get('total_count')) is int and history.get('workflow_runs')==[],
        'destination has prior runs or unavailable history; STOP')

def verify(data,root,*,context=None,hold=None,_client=None,_checkout=None,_now=None):
    """Read-only verification. Prepublication requires no future provider IDs.
    Every injected seam unconditionally labels output synthetic and disables it.
    """
    genuine=_client is None and _checkout is None and _now is None
    client=_client if _client is not None else RunnerGitHub();now=_now or datetime.now(timezone.utc)
    request,frozen,case_bytes=request_record(data)
    if _checkout is None:
        shot.verify_checkout(root,frozen)
        shot.require(auth.same_json(source_manifests(root),{k:frozen['intent']['source'][k] for k in ('engineSha256','deckSha256','pilotSha256')}),'source engine/deck/pilot manifests differ')
    else:_checkout(frozen)
    shot.require(auth.client_owner_matches(client),'actual repository/controller owner differs')
    allocation,predecessor_evidence=successor.verify_allocation(client,request,frozen,now,decision)
    env,_,env_sha=decision(client,request['environmentVerificationId'],ENVIRONMENT,environment_expected(request,frozen),now)
    owner,_,owner_sha=decision(client,request['ownerDecisionId'],shot.OWNER,owner_expected(request,frozen,env_sha),now)
    shot.require(auth.utc(env['updated_at'])<=auth.utc(owner['created_at']),'owner decision predates Environment readback')
    shot.require(auth.utc(allocation['updated_at'])<=auth.utc(env['created_at']),
        'Environment decision predates successor allocation')
    records=[allocation,env,owner];run=None;release_sha=None;release_id=None
    if context is None:verify_prepublication_absence(client)
    if context is not None:
        run=client.get('actions/runs/'+str(context['runId']))
        shot.verify_provider(frozen,run,context,client.get('git/commits/'+frozen['intent']['source']['commit']))
        successor.reject_previous_context(context,run)
        shot.verify_original_history(shot.run_history(client,run['workflow_id']),run)
        shot.require(type(run.get('created_at')) is str and
            auth.utc(env['updated_at'])<=auth.utc(run['created_at']) and
            auth.utc(owner['updated_at'])<=auth.utc(run['created_at']),
            'Environment/case owner verification must precede workflow-triggering publication')
    if hold is not None:
        shot.exact(hold,{'contract','intentSha256','context','environmentVerificationBodySha256','ownerBodySha256','providerRunCreatedAt'},'complete engine-free hold required')
        expected_hold=dict(contract=HOLD,intentSha256=frozen['intentSha256'],context=context,
            environmentVerificationBodySha256=env_sha,ownerBodySha256=owner_sha,providerRunCreatedAt=run.get('created_at'))
        shot.require(auth.same_json(hold,expected_hold),'interrupted/substituted/cross-bound hold')
        shot.require(type(run.get('created_at')) is str,'provider run creation observation missing')
        expected=release_expected(request,frozen,context,shot.digest(shot.canonical(hold)),env_sha)
        events=auth.live_history(client);matches=[]
        for event in events:
            body=event.get('body','')
            if event.get('user',{}).get('id')!=auth.OWNER_ID or not body.startswith(shot.RELEASE+'\n'):continue
            claims=auth.decode(body.split('\n',1)[1])
            if claims.get('intentSha256')==frozen['intentSha256'] and claims.get('run',{}).get('runId')==context['runId']:matches.append(event)
        shot.require(len(matches)==1,'missing/ambiguous original run-specific owner release')
        release_id=matches[0]['id']
        release,_,release_sha=decision(client,release_id,shot.RELEASE,expected,now)
        shot.require(release_id not in (owner['id'],env['id']) and auth.utc(release['created_at'])>=auth.utc(run['created_at']),'release must follow assigned original run')
        records.append(release)
    observed_history=auth.live_history(client)
    history_check(observed_history,records,now)
    # Last readback follows provider/history checks; this code never initializes.
    for record in records:shot.require(auth.same_json(record,client.get('issues/comments/'+str(record['id']))),'owner facts changed at final boundary')
    final=hold is not None
    return dict(contract='ARGENTUM_SUCCESSOR_LIVE_GATE_V2',stage='RUN_SPECIFIC_RELEASE_VERIFIED' if final else ('ENGINE_FREE_PREFLIGHT' if context else 'PREPUBLICATION_OWNER_CORRESPONDENCE'),
        successor=successor.binding(request),predecessorEvidence=predecessor_evidence,
        engineAllowed=genuine and final,authenticatedOwnerDecision=genuine,
        authenticatedProductionOrigin=False,productionQualified=False,assuranceModel=MODEL,
        origin='LIVE_GITHUB_OWNER_AND_PROVIDER_READBACK' if genuine else shot.SYNTHETIC,
        caseSha256=shot.digest(case_bytes),intentSha256=frozen['intentSha256'],decisionId=request['ownerDecisionId'],
        releaseId=release_id,decisionBodySha256=owner_sha,releaseBodySha256=release_sha,
        environmentVerificationBodySha256=env_sha,environment=request['environmentPolicy']['name'],
        source=frozen['intent']['source'],context=context,providerRunCreatedAt=run.get('created_at') if run else None,
        environmentProtectionProvenance='AUTHENTICATED_OWNER_SETTINGS_READBACK_NOT_ADMINISTRATION_API_READ',
        decisionReadbacks=records,controllingHistoryReadback=observed_history,providerRunReadback=run,
        persistentCrossWorkflowSingleUse=False,hostAttested=False,independentTimeAttested=False,
        caseBytesBase64=base64.b64encode(case_bytes).decode())

def hold_record(proof):
    shot.require(proof['stage']=='ENGINE_FREE_PREFLIGHT','hold can only follow engine-free preflight')
    return dict(contract=HOLD,intentSha256=proof['intentSha256'],context=proof['context'],
        environmentVerificationBodySha256=proof['environmentVerificationBodySha256'],
        ownerBodySha256=proof['decisionBodySha256'],providerRunCreatedAt=proof['providerRunCreatedAt'])

def write_original(path,data):
    path=Path(path);shot.require(not path.is_symlink(),'original evidence link refused')
    if path.exists():shot.require(path.read_bytes()==data,'original evidence conflict; never overwrite')
    else:
        with path.open('xb') as f:f.write(data)

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('operation',choices=['prepublication','preflight','final','gate','manifest-source'])
    parser.add_argument('--root',type=Path,default=Path.cwd());parser.add_argument('--evidence',type=Path)
    parser.add_argument('--case',type=Path)
    args=parser.parse_args()
    if args.operation=='manifest-source':print(json.dumps(source_manifests(args.root),sort_keys=True));return 0
    data=os.environ.get('ARGENTUM_SUCCESSOR_REQUEST_JSON','').encode()
    # Missing activation input is refused before provider context or network.
    request_record(data)
    context=None if args.operation=='prepublication' else context_from_provider_environment()
    hold=None
    if args.operation in ('final','gate'):
        shot.require(args.evidence is not None and (args.evidence/'hold.json').is_file(),'original preflight hold missing')
        hold=auth.decode((args.evidence/'hold.json').read_bytes())
    proof=verify(data,args.root,context=context,hold=hold)
    if args.case is not None:shot.require(shot.digest(args.case.read_bytes())==proof['caseSha256'],'adapter case changed; no launch')
    if args.evidence is not None:
        shot.require(args.evidence.is_dir() and not args.evidence.is_symlink(),'existing original evidence root required')
        if args.operation=='preflight':
            write_original(args.evidence/'case.json',base64.b64decode(proof['caseBytesBase64'],validate=True))
            write_original(args.evidence/'hold.json',shot.canonical(hold_record(proof))+b'\n')
        write_original(args.evidence/(args.operation+'-verification.json'),shot.canonical(proof)+b'\n')
    if args.operation=='preflight':
        output=os.environ.get('GITHUB_OUTPUT');shot.require(output is not None,'provider output channel missing')
        with Path(output).open('a') as f:f.write('protected_environment='+proof['environment']+'\nactivation_verified=true\n')
    print(json.dumps({k:v for k,v in proof.items() if k!='caseBytesBase64'},sort_keys=True))
    return 0

if __name__=='__main__':
    try:raise SystemExit(main())
    except (ValueError,KeyError,TypeError,OSError,subprocess.SubprocessError) as error:
        print(json.dumps(dict(stage='REFUSED',engineAllowed=False,reason=str(error))),file=sys.stderr);raise SystemExit(78)
