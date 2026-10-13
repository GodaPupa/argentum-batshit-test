#!/usr/bin/env python3
"""Staged authority and historical correspondence; no engine or allocation.
An intent, record, timestamp or environment is never a trust root. Production
hold/single-use and execution-time anchor backends are deliberately UNAVAILABLE.
Only separately reviewed trusted-code backends may authenticate their references.
Underscored injection seams are excluded tests, never CLI/environment inputs.
"""
from __future__ import annotations
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import ssl
import subprocess
import urllib.request

CONTRACT = 'ARGENTUM_GAMEPLAY_AUTHORITY_V2'
REPOSITORY = 'GodaPupa/argentum-batshit-test'
PR = 231
OWNER_ID = 326515697
OWNER_LOGIN = 'GodaPupa'
SOURCE_PARENT = 'e79adf5770a803568a81be73a83fa8627c48c862'
SCOPE = 'NONOFFICIAL_PLAY_FIRST_EXHIBITION_ONLY'
RUN_KEYS = {'runId', 'runAttempt', 'runNumber', 'event', 'ref', 'sourceCommit',
            'sourceParent', 'sourceTree', 'validationCommit', 'validationTree',
            'workflowPath', 'workflowBlob'}
INTENT_CONTRACT = 'ARGENTUM_GAMEPLAY_INTENT_V2'
OWNER_CONTRACT = 'ARGENTUM_GAMEPLAY_OWNER_DECISION_V2'
RELEASE_CONTRACT = 'ARGENTUM_GAMEPLAY_RUN_RELEASE_V2'
PLAN_KEYS = RUN_KEYS - {'runId'}
CASE_KEYS = {'blockId','gameId','pair','pestSeat','fixtureSeedHex','sourceCommit','scope','intendedBlockGames'}
MAX_RECORD_AGE_SECONDS = 60

def require(value, message):
    if not value:
        raise ValueError(message)

def sha(data):
    return hashlib.sha256(data).hexdigest()

def same_json(left, right):
    # Python dictionary equality conflates True with 1 and 1.0. A contract must
    # retain the JSON boolean/integer distinction in nested approval fields.
    def canonical(value):
        return json.dumps(value, sort_keys=True, separators=(',', ':'), allow_nan=False)
    return canonical(left) == canonical(right)

def decode(data):
    def pairs(items):
        result = {}
        for key, value in items:
            require(key not in result, 'duplicate authority JSON key')
            result[key] = value
        return result
    def invalid(value):
        raise ValueError('nonfinite authority JSON')
    return json.loads(data, object_pairs_hook=pairs, parse_constant=invalid)

def utc(value):
    require(type(value) is str and bool(re.fullmatch(r'\d{4}-\d\d-\d\dT\d\d:\d\d:\d\dZ', value)), 'UTC authority timestamp required')
    return datetime.strptime(value, '%Y-%m-%dT%H:%M:%SZ').replace(tzinfo=timezone.utc)

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        raise ValueError('authority transport redirect refused')

class LiveGitHub:
    """No configurable host, disabled TLS, record-supplied URL or fixture mode."""
    def get(self, path):
        require(type(path) is str and (path == '' or bool(re.fullmatch(r'[A-Za-z0-9_./?=&-]+', path))) and '..' not in path, 'noncanonical API path')
        url = 'https://api.github.com/repos/' + REPOSITORY + ('/' + path if path else '')
        request = urllib.request.Request(url, headers={'Accept': 'application/vnd.github+json',
                                                       'User-Agent': 'Argentum-source-bound-authority-v1'})
        opener = urllib.request.build_opener(NoRedirect(), urllib.request.HTTPSHandler(context=ssl.create_default_context()))
        with opener.open(request, timeout=20) as response:
            require(response.status == 200 and response.geturl() == url, 'untrusted authority transport')
            data = response.read(4 * 1024 * 1024 + 1)
        require(len(data) <= 4 * 1024 * 1024, 'authority response too large')
        return decode(data)

def discussion(client, endpoint):
    result = []
    for page in range(1, 101):
        entries = client.get(endpoint + '?per_page=100&page=' + str(page))
        require(type(entries) is list and len(entries) <= 100, 'incomplete discussion response')
        result.extend(entries)
        if len(entries) < 100:
            return result
    raise ValueError('discussion limit: cannot exclude supersession')


def freeze_intent(data):
    """Immutable byte identity before provider IDs exist; no authorization."""
    intent=decode(data)
    require(type(intent) is dict and set(intent)=={'contract','case','plan'} and intent['contract']==INTENT_CONTRACT,'immutable intent schema')
    case,plan=intent['case'],intent['plan']
    require(type(case) is dict and set(case)==CASE_KEYS and type(plan) is dict and set(plan)==PLAN_KEYS,'case/plan schema; no future provider IDs')
    require(type(case['blockId']) is str and bool(re.fullmatch(r'PLAY_FIRST_[A-Z0-9_]{1,80}',case['blockId'])) and 'B01' not in case['blockId'] and type(case['gameId']) is str and bool(re.fullmatch(r'G[0-9]{2,4}',case['gameId'])) and case['gameId']!='G08','historical B01/G08 cannot be replayed')
    require(type(case['pair']) is int and case['pair']>=1 and type(case['pestSeat']) is int and case['pestSeat'] in (0,1) and type(case['intendedBlockGames']) is int and 1<=case['intendedBlockGames']<=100,'case pair/seat/block declaration')
    seed=case['fixtureSeedHex']
    require(type(seed) is str and bool(re.fullmatch(r'0x[0-7][0-9a-f]{15}',seed)) and int(seed,16)>0 and seed not in ('0x504c415946495253','0x504c4159424c3031','0x504c4159424c3032','0x504c4159424c3033','0x504c4159424c3034'),'historical/invalid case seed label; no allocation')
    require(case['scope']==SCOPE and case['sourceCommit']==plan['sourceCommit'] and plan['sourceParent']==SOURCE_PARENT,'intent scope/source binding')
    require(type(plan['runAttempt']) is int and plan['runAttempt']==1 and type(plan['runNumber']) is int and plan['runNumber']==1 and plan['event']=='push','one original push only')
    for key in ('sourceCommit','sourceParent','sourceTree','validationCommit','validationTree','workflowBlob'):
        require(type(plan[key]) is str and bool(re.fullmatch('[0-9a-f]{40}',plan[key])),'authority Git identity: '+key)
    require(type(plan['ref']) is str and bool(re.fullmatch(r'refs/heads/[A-Za-z0-9_./-]+',plan['ref'])) and '..' not in plan['ref'],'authority ref')
    require(type(plan['workflowPath']) is str and bool(re.fullmatch(r'\.github/workflows/[A-Za-z0-9_-]+\.ya?ml',plan['workflowPath'])),'authority workflow path')
    return dict(stage='INTENT_FROZEN',engineAllowed=False,intentSha256=sha(data),intent=intent)

def check_run(intent,run):
    require(type(run) is dict and set(run)==RUN_KEYS,'complete provider-assigned run schema')
    require(type(run['runId']) is str and bool(re.fullmatch('[1-9][0-9]*',run['runId'])),'provider-assigned run ID')
    require(same_json({k:run[k] for k in PLAN_KEYS},intent['plan']),'run/immutable intent cross-binding')

def owner_expected(h):
    # No self comment ID and no future run ID in the owner decision body.
    return dict(contract=OWNER_CONTRACT,intentSha256=h,scope=SCOPE,originalOnly=True,maxGames=1)

def release_expected(h,owner_id,run):
    return dict(contract=RELEASE_CONTRACT,intentSha256=h,ownerDecisionId=owner_id,run=run,scope=SCOPE,originalOnly=True,maxGames=1)

def decision_claims(comment,decision_id,contract,expected,at):
    require(type(decision_id) is int and decision_id>0 and comment.get('id')==decision_id,'actual provider decision ID')
    require(comment.get('user',{}).get('id')==OWNER_ID and comment.get('user',{}).get('login')==OWNER_LOGIN and comment.get('author_association')=='OWNER','actual controller issuer mismatch')
    require(comment.get('issue_url')=='https://api.github.com/repos/'+REPOSITORY+'/issues/231' and comment.get('html_url')=='https://github.com/'+REPOSITORY+'/pull/231#issuecomment-'+str(decision_id) and not comment.get('minimized'),'decision outside controlling PR')
    body=comment.get('body')
    require(type(body) is str and body.startswith(contract+'\n'),'no positive gameplay decision; construction/non-gameplay comment')
    claims=decode(body[len(contract)+1:])
    require(type(claims) is dict and set(claims)==set(expected)|{'expiresAt'} and same_json({k:claims[k] for k in expected},expected),'approved identities/scope/original restrictions differ')
    require(utc(comment['created_at'])<=utc(comment['updated_at'])<=at<utc(claims['expiresAt']),'expired/not-yet-valid approval at execution')
    return claims,sha(body.encode())

def client_owner_matches(client):
    owner=client.get('')['owner']
    return owner.get('id')==OWNER_ID and owner.get('login')==OWNER_LOGIN

def live_history(client):
    events=[]
    for endpoint in ('issues/231/comments','pulls/231/comments','pulls/231/reviews'):
        events.extend(discussion(client,endpoint))
    return events

def check_history(events,owner,release,at,*,historical=False):
    """Conservative pre-execution supersession; later changes do not erase it.
    Historical backend must authenticate complete VERSION history through launch,
    not today's mutable comments. Later edits cannot hide an earlier revocation.
    """
    require(type(events) is list,'complete controlling history required')
    for decision in (owner,release):
        require(any(same_json(e,decision) for e in events),'decision absent/contradictory in authenticated history')
    for event in events:
        if event.get('user',{}).get('id')!=OWNER_ID:continue
        stamp=event.get('updated_at') or event.get('submitted_at') or event.get('created_at')
        require(stamp is not None,'undated owner history')
        require(historical or utc(stamp)<=at,'live owner event newer than verification clock')
        if event.get('id') in (owner['id'],release['id']):
            chosen=owner if event['id']==owner['id'] else release
            require(not (utc(chosen['updated_at'])<=utc(stamp)<=at) or same_json(event,chosen),'contradictory decision version before execution')
            continue
        require(not (utc(owner['updated_at'])<=utc(stamp)<=at),'pre-execution revocation/supersession/consumption')

def verify_owner_decision(data,decision_id,*,_client=None,_now=None):
    """Live read-only owner stage. Its output alone cannot release an engine."""
    frozen=freeze_intent(data);client=LiveGitHub() if _client is None else _client
    now=datetime.now(timezone.utc) if _now is None else _now
    require(client_owner_matches(client),'trusted repository owner changed')
    comment=client.get('issues/comments/'+str(decision_id))
    claims,h=decision_claims(comment,decision_id,OWNER_CONTRACT,owner_expected(frozen['intentSha256']),now)
    events=live_history(client)
    require(any(same_json(e,comment) for e in events),'decision missing from complete discussion')
    for e in events:
        if e.get('user',{}).get('id')==OWNER_ID and e.get('id')!=decision_id:
            stamp=e.get('updated_at') or e.get('submitted_at') or e.get('created_at')
            require(stamp is not None and utc(stamp)<utc(comment['updated_at']),'superseding owner decision')
    require(same_json(client.get('issues/comments/'+str(decision_id)),comment),'decision changed during verification')
    verify_provider_source(client,frozen['intent']['plan'])
    return dict(stage='OWNER_DECISION_VERIFIED',engineAllowed=False,ownerDecisionId=decision_id,intentSha256=frozen['intentSha256'],decisionBodySha256=h,claims=claims,origin='LIVE_GITHUB_TLS_OWNER_FACTS' if type(client) is LiveGitHub else 'SYNTHETIC_TRUSTED_FIXTURE_NOT_AUTHENTICATED')

def check_hold(hold,h,run,at):
    require(type(hold) is dict and set(hold)=={'intentSha256','run','observedAt','engineStarted','originalSlotCreated'},'incomplete trusted hold')
    require(hold['intentSha256']==h and same_json(hold['run'],run),'hold/head/ref/intent mismatch')
    require(hold['engineStarted'] is False and hold['originalSlotCreated'] is False and utc(hold['observedAt'])<=at,'hold already initialized engine or original slot')

def verify_provider_source(client,run):
    source=client.get('git/commits/'+run['sourceCommit']);validation=client.get('git/commits/'+run['validationCommit'])
    require(source.get('sha')==run['sourceCommit'] and source.get('tree',{}).get('sha')==run['sourceTree'] and [p['sha'] for p in source['parents']]==[SOURCE_PARENT],'actual source identity/sole parent')
    require(validation.get('sha')==run['validationCommit'] and validation.get('tree',{}).get('sha')==run['validationTree'] and [p['sha'] for p in validation['parents']]==[run['sourceCommit']],'actual validation identity/sole parent')
    require(client.get('contents/'+run['workflowPath']+'?ref='+run['validationCommit']).get('sha')==run['workflowBlob'],'actual approved workflow blob')

def verify_provider_source_run(client,run):
    verify_provider_source(client,run)
    actual=client.get('actions/runs/'+run['runId'])
    require(actual.get('id')==int(run['runId']) and actual.get('head_sha')==run['validationCommit'] and actual.get('head_branch')==run['ref'][len('refs/heads/'):] and actual.get('event')=='push' and type(actual.get('run_attempt')) is int and actual.get('run_attempt')==1 and type(actual.get('run_number')) is int and actual.get('run_number')==1 and actual.get('path')==run['workflowPath'] and actual.get('repository',{}).get('full_name')==REPOSITORY and actual.get('actor',{}).get('id')==OWNER_ID and actual.get('status')=='in_progress','actual original run/source/workflow correspondence')

def proof(frozen,run,owner_id,release_id,owner_hash,release_hash,origin,stage):
    case=frozen['intent']['case']
    return dict(contract=CONTRACT,stage=stage,engineAllowed=stage=='RUN_SPECIFIC_RELEASE_VERIFIED',claims=dict(caseSha256=frozen['intentSha256'],blockId=case['blockId'],gameId=case['gameId'],decisionId=owner_id,releaseDecisionId=release_id,scope=SCOPE,run=run),decisionBodySha256=owner_hash,releaseBodySha256=release_hash,provenance=dict(origin=origin,repository=REPOSITORY,pr=PR))

def verify_release(data,run,record,*,_trusted=None,_client=None,_now=None):
    """Fresh release only after externally authenticated engine-free hold.
    Trusted backend is separately installed CODE, not a caller-supplied document.
    It must authenticate provider run/host/code and atomically reserve the original
    across hosts, rechecking current authority and preparing a durable execution
    anchor. No production backend, signer or host attestation exists here.
    """
    frozen=freeze_intent(data);check_run(frozen['intent'],run)
    require(type(record) is dict and set(record)=={'contract','intentSha256','ownerDecisionId','releaseDecisionId','holdRef'} and record['contract']==CONTRACT and record['intentSha256']==frozen['intentSha256'],'missing/forged run-specific release record')
    require(_trusted is not None,'UNQUALIFIED: trusted hold/launcher/cross-host single-use backend unavailable')
    now=datetime.now(timezone.utc) if _now is None else _now
    hold=_trusted.authenticate_hold(record['holdRef']);check_hold(hold,frozen['intentSha256'],run,now)
    require(0<=(now-utc(hold['observedAt'])).total_seconds()<=MAX_RECORD_AGE_SECONDS,'stale trusted hold')
    client=LiveGitHub() if _client is None else _client
    require(client_owner_matches(client),'trusted repository owner changed')
    owner=client.get('issues/comments/'+str(record['ownerDecisionId']));release=client.get('issues/comments/'+str(record['releaseDecisionId']))
    _,oh=decision_claims(owner,record['ownerDecisionId'],OWNER_CONTRACT,owner_expected(frozen['intentSha256']),now)
    _,rh=decision_claims(release,record['releaseDecisionId'],RELEASE_CONTRACT,release_expected(frozen['intentSha256'],owner['id'],run),now)
    require(owner['id']!=release['id'] and utc(owner['updated_at'])<=utc(hold['observedAt'])<=utc(release['created_at']) and 0<=(now-utc(release['updated_at'])).total_seconds()<=MAX_RECORD_AGE_SECONDS,'interrupted/stale staged release')
    check_history(live_history(client),owner,release,now);verify_provider_source_run(client,run)
    require(same_json(client.get('issues/comments/'+str(owner['id'])),owner) and same_json(client.get('issues/comments/'+str(release['id'])),release),'authority changed during release')
    # Atomic trusted service MUST reject already-consumed or concurrently revoked
    # originals and host/code changes. Local files cannot emulate this service.
    reservation=_trusted.reserve_original(frozen['intentSha256'],run,owner,release,now)
    require(type(reservation) is dict and reservation.get('intentSha256')==frozen['intentSha256'] and same_json(reservation.get('run'),run) and type(reservation.get('originalCount')) is int and reservation.get('originalCount')==1 and reservation.get('anchorReady') is True and reservation.get('reservedAt')==now.strftime('%Y-%m-%dT%H:%M:%SZ'),'incomplete authenticated original reservation/anchor')
    require(_trusted.origin in ('TRUSTED_EXTERNAL_LAUNCH_RELEASE','SYNTHETIC_TRUSTED_FIXTURE_NOT_AUTHENTICATED'),'unqualified release provenance')
    return proof(frozen,run,owner['id'],release['id'],oh,rh,_trusted.origin,'RUN_SPECIFIC_RELEASE_VERIFIED')

def verify_historical(data,run,record,manifest_sha256,*,_anchor=None):
    """Historical-at-execution verification, without a current freshness check.
    authenticate_historical must retrieve authenticated provider/host provenance,
    trusted execution time, immutable decision versions, and complete history
    through launch independently of the inspected artifact. Parsing the supplied
    record, its timestamp, or an unsigned snapshot cannot implement that method.
    Re-inspection is read-only; a different original cannot reuse the same anchor.
    Production backend deliberately unavailable until independent implementation.
    """
    frozen=freeze_intent(data);check_run(frozen['intent'],run)
    require(type(record) is dict and set(record)=={'contract','intentSha256','ownerDecisionId','releaseDecisionId','anchorRef'} and record['contract']==CONTRACT and record['intentSha256']==frozen['intentSha256'],'missing/forged historical approval reference')
    require(_anchor is not None,'UNQUALIFIED: independently authenticated execution-time anchor unavailable')
    facts=_anchor.authenticate_historical(record['anchorRef'])
    keys={'intentSha256','run','owner','release','history','historyCompleteThrough','executedAt','hold','sourceVerified','manifestSha256','originalConsumption'}
    require(type(facts) is dict and set(facts)==keys,'incomplete authenticated historical anchor')
    require(facts['intentSha256']==frozen['intentSha256'] and same_json(facts['run'],run) and facts['manifestSha256']==manifest_sha256 and facts['sourceVerified'] is True,'historical anchor source/case/run/artifact mismatch')
    at=utc(facts['executedAt']) # Normative time from trusted backend, NOT caller.
    require(utc(facts['historyCompleteThrough'])>=at,'incomplete execution-time version history')
    owner,release=facts['owner'],facts['release']
    _,oh=decision_claims(owner,record['ownerDecisionId'],OWNER_CONTRACT,owner_expected(frozen['intentSha256']),at)
    _,rh=decision_claims(release,record['releaseDecisionId'],RELEASE_CONTRACT,release_expected(frozen['intentSha256'],owner['id'],run),at)
    check_hold(facts['hold'],frozen['intentSha256'],run,at)
    require(owner['id']!=release['id'] and utc(owner['updated_at'])<=utc(facts['hold']['observedAt'])<=utc(release['created_at'])<=at and 0<=(at-utc(release['updated_at'])).total_seconds()<=MAX_RECORD_AGE_SECONDS,'historical release sequencing/freshness at execution')
    check_history(facts['history'],owner,release,at,historical=True)
    require(same_json(facts['originalConsumption'],dict(intentSha256=frozen['intentSha256'],runId=run['runId'],executedAt=facts['executedAt'],count=1)),'replayed/cross-bound original anchor')
    require(_anchor.origin in ('TRUSTED_EXTERNAL_EXECUTION_TIME_ANCHOR','SYNTHETIC_TRUSTED_FIXTURE_NOT_AUTHENTICATED'),'unqualified historical provenance')
    result=proof(frozen,run,owner['id'],release['id'],oh,rh,_anchor.origin,'HISTORICAL_AT_EXECUTION_CORRESPONDENCE');result['executedAt']=facts['executedAt']
    return result

def verify_local_execution_source(run):
    """Git correspondence, not host/runner attestation. No candidate override."""
    root = Path(__file__).resolve().parents[2]
    def git(*args):
        return subprocess.check_output(['git', '-C', str(root), *args], text=True, timeout=20).strip()
    require(git('rev-parse', 'HEAD') == run['validationCommit'] and
            git('rev-parse', 'HEAD^{tree}') == run['validationTree'], 'actual local validation checkout differs')
    require(git('show', '-s', '--format=%P', 'HEAD').split() == [run['sourceCommit']], 'actual local validation parent differs')
    require(git('rev-parse', run['sourceCommit'] + '^{tree}') == run['sourceTree'] and
            git('show', '-s', '--format=%P', run['sourceCommit']).split() == [SOURCE_PARENT], 'actual local approved source differs')
    require(not git('status', '--porcelain=v1', '--untracked-files=all'), 'actual executing checkout is dirty')


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('operation',choices=('freeze-intent','verify-release','verify-historical'))
    parser.add_argument('--case',required=True,type=Path)
    parser.add_argument('--run-context',type=Path)
    parser.add_argument('--record',type=Path)
    parser.add_argument('--manifest-sha256')
    args=parser.parse_args()
    try:
        data=args.case.read_bytes()
        if args.operation=='freeze-intent':result=freeze_intent(data)
        else:
            run=decode(args.run_context.read_bytes());record=decode(args.record.read_bytes()) if args.record else None
            if args.operation=='verify-release':
                verify_local_execution_source(run);result=verify_release(data,run,record)
            else:result=verify_historical(data,run,record,args.manifest_sha256)
        print(json.dumps(result,sort_keys=True));return 0
    except Exception as error:
        print(json.dumps({'status':'REFUSED','errors':[type(error).__name__+': '+str(error)]}));return 1

if __name__=='__main__':raise SystemExit(main())
