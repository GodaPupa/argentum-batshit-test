#!/usr/bin/env python3
"""Inactive one-workflow contract. No launcher, engine, allocation or activation.

Code-only synthetic seams exercise correspondence, never authenticate fixtures.
Environment variables, JSON, hashes and provider IDs cannot install a trust root.
The existing production runtime remains DENY. This separate interface grants no
capability even when every synthetic correspondence check succeeds.
"""
from __future__ import annotations
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys

# Fixed local import for Python -I; no environment-supplied import/backend path.
sys.path.insert(0, str(Path(__file__).resolve().parent))
import gameplay_authority as auth

PARENT = '54e0fedbcd6c9c7e719ae4e8720f24fb2633fd2a'
PARENT_TREE = '3792b023fd430030ad2d46937c0b1a8de1454f68'
BRANCH = 'play-first/successor-nonofficial-v2'
REF = 'refs/heads/' + BRANCH
WORKFLOW = '.github/workflows/play-first-successor-nonofficial-v2.yml'
CONFIG = 'docs/experiments/play-first/isolation-v2/ONE_SHOT_ACTIONS_INACTIVE.json'
INTENT = 'ARGENTUM_SUCCESSOR_ACTIONS_INTENT_V2'
OWNER = 'ARGENTUM_SUCCESSOR_ACTIONS_OWNER_V2'
RELEASE = 'ARGENTUM_SUCCESSOR_ACTIONS_RELEASE_V2'
SYNTHETIC = 'UNAUTHENTICATED_SYNTHETIC_TEST_INPUT'
SOURCE_KEYS = {'commit','tree','parent','engineSha256','deckSha256','pilotSha256'}
WORKFLOW_KEYS = {'path','ref','blob','sha256'}
CASE_KEYS = {'caseId','gameId','pestSeat','monsterSeat','scope','originalOnly','maxGames','permittedInputsSha256'}

def require(value, message):
    auth.require(value, message)

def digest(data):
    return hashlib.sha256(data).hexdigest()

def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(',', ':'), allow_nan=False).encode()

def exact(value, keys, message):
    require(type(value) is dict and set(value) == set(keys), message)

def integer(value, message):
    require(type(value) is int and value > 0, message)

def hex_identity(value, size, message):
    require(type(value) is str and bool(re.fullmatch('[0-9a-f]{'+str(size)+'}', value)), message)

def denied(stage, **details):
    return dict(stage=stage, engineAllowed=False, gameplayAuthorized=False,
                executableActivation=False, productionQualified=False,
                authenticatedProductionOrigin=False, **details)

def freeze_intent(data):
    """Byte-frozen before owner comment/run/workflow numeric IDs exist."""
    require(type(data) is bytes and len(data) <= 65536, 'bounded intent bytes required')
    value = auth.decode(data)
    exact(value, {'contract','source','workflow','case'}, 'immutable intent schema; no future IDs')
    require(value['contract'] == INTENT, 'one-shot intent contract')
    source, workflow, case = value['source'], value['workflow'], value['case']
    exact(source, SOURCE_KEYS, 'complete source/engine/deck/pilot identities')
    for k in ('commit','tree','parent'): hex_identity(source[k],40,'source '+k)
    require(source['parent'] == PARENT, 'exact frozen focused-admission sole parent required')
    for k in ('engineSha256','deckSha256','pilotSha256'): hex_identity(source[k],64,k)
    exact(workflow, WORKFLOW_KEYS, 'complete frozen workflow identities')
    require(workflow['path'] == WORKFLOW and workflow['ref'] == REF, 'unique workflow/ref mismatch')
    hex_identity(workflow['blob'],40,'workflow Git blob')
    hex_identity(workflow['sha256'],64,'workflow SHA256')
    exact(case, CASE_KEYS, 'case/seats/inputs/original scope binding')
    for k in ('caseId','gameId'):
        require(type(case[k]) is str and bool(re.fullmatch('[A-Za-z0-9_-]{1,80}',case[k])) and
                'B01' not in case[k] and case[k] != 'G08', 'historical B01/G08 excluded')
    require(type(case['pestSeat']) is int and type(case['monsterSeat']) is int and
            {case['pestSeat'],case['monsterSeat']} == {0,1}, 'distinct exact seats')
    require(case['scope'] == auth.SCOPE and case['originalOnly'] is True and
            type(case['maxGames']) is int and case['maxGames'] == 1, 'one nonofficial original only')
    hex_identity(case['permittedInputsSha256'],64,'approved permitted inputs bytes')
    return denied('INTENT_FROZEN', intent=value, intentSha256=digest(data))

def verify_checkout(root, frozen):
    """Read-only Git correspondence to EXTERNALLY approved pins, never authority."""
    root = Path(root)
    def git(*args):
        return subprocess.check_output(['git',*args],cwd=root,stderr=subprocess.PIPE).decode().strip()
    source = frozen['intent']['source']; workflow = frozen['intent']['workflow']
    require(git('rev-parse','HEAD') == source['commit'], 'checkout commit differs')
    require(git('rev-parse','HEAD^{tree}') == source['tree'], 'checkout tree differs')
    require(git('show','-s','--format=%P','HEAD').split() == [PARENT], 'checkout sole parent differs')
    require(not git('status','--porcelain=v1','--untracked-files=all'), 'dirty/untracked checkout refused')
    require(git('rev-parse','HEAD:'+WORKFLOW) == workflow['blob'], 'checkout workflow blob differs')
    require(digest((root/WORKFLOW).read_bytes()) == workflow['sha256'], 'checkout workflow bytes differ')
    return denied('CHECKOUT_CORRESPONDENCE_ONLY')

def verify_provider(frozen, run, context, commit):
    """API readback plus provider context; neither caller JSON nor ID suffices."""
    source = frozen['intent']['source']; workflow = frozen['intent']['workflow']
    exact(context, {'repository','event','ref','sha','runId','runNumber','runAttempt','workflowRef'},
          'complete provider context required')
    integer(context['runId'], 'provider-issued run ID required after hold exists')
    require(context['repository'] == auth.REPOSITORY and context['event'] == 'push' and
            context['ref'] == REF and context['sha'] == source['commit'], 'provider context/source/ref differs')
    require(type(context['runNumber']) is int and context['runNumber'] == 1 and
            type(context['runAttempt']) is int and context['runAttempt'] == 1, 'run1 attempt1 only; no retries')
    require(context['workflowRef'] == auth.REPOSITORY+'/'+WORKFLOW+'@'+REF, 'workflow ref differs')
    require(run.get('id') == context['runId'] and type(run.get('id')) is int and
            type(run.get('run_number')) is int and run['run_number'] == 1 and
            type(run.get('run_attempt')) is int and run['run_attempt'] == 1 and
            run.get('event') == 'push' and run.get('head_sha') == source['commit'] and
            run.get('head_branch') == BRANCH and run.get('path') == WORKFLOW,
            'live provider run/head/ref/path/attempt mismatch')
    integer(run.get('workflow_id'), 'provider-issued workflow ID absent')
    require(run.get('repository',{}).get('full_name') == auth.REPOSITORY and
            run.get('head_repository',{}).get('full_name') == auth.REPOSITORY, 'foreign/fork run refused')
    require(run.get('status') == 'in_progress' and run.get('conclusion') is None,
            'finished/cancelled/ambiguous run cannot release')
    require(commit.get('sha') == source['commit'] and commit.get('tree',{}).get('sha') == source['tree'] and
            [x.get('sha') for x in commit.get('parents',[])] == [PARENT], 'provider Git commit/tree/sole parent differs')
    return denied('PROVIDER_RECORD_CORRESPONDENCE_ONLY',runId=run['id'],workflowId=run['workflow_id'])

def run_history(client, workflow_id):
    integer(workflow_id,'workflow ID missing')
    rows=[]
    for page in range(1,101):
        response=client.get('actions/workflows/'+str(workflow_id)+'/runs?per_page=100&page='+str(page))
        require(type(response) is dict and type(response.get('total_count')) is int and
                type(response.get('workflow_runs')) is list, 'incomplete run history')
        total=response['total_count']; entries=response['workflow_runs']
        require(len(entries)<=100, 'invalid run history page')
        rows.extend(entries)
        if len(entries)<100:
            require(len(rows)==total and len({x.get('id') for x in rows})==len(rows),
                    'truncated/duplicated/changing run history')
            return rows
    raise ValueError('run history pagination limit')

def verify_original_history(rows, run):
    require(type(rows) is list and len(rows)==1 and type(rows[0]) is dict,
            'prior/additional/orphaned attempt exists; no replacement')
    # List/detail endpoints may add observation metadata independently. Compare
    # every execution identity and terminal state, never mutable incidental
    # timestamps or endpoint-specific extra fields as if they were identities.
    for key in ('id','workflow_id','head_sha','head_branch','path','event','run_number','run_attempt','status','conclusion'):
        require(key in rows[0] and auth.same_json(rows[0][key],run.get(key)), 'original run history identity differs: '+key)
    for key in ('repository','head_repository'):
        require(rows[0].get(key,{}).get('full_name')==run.get(key,{}).get('full_name'), 'original run repository differs')
    return denied('ONE_WORKFLOW_ORIGINAL_CORRESPONDENCE_ONLY',
                  persistentCrossWorkflowSingleUse=False,hostAttested=False)

def verify_environment(environment, policy, ref):
    """Fetched facts must come from a separately trusted external verifier.
    Never accept environment name, repo variable or a snapshot as authentication.
    Existing required-reviewer protection is mandatory before publishing a push.
    """
    exact(policy, {'id','name','ownerReviewerId','allowSelfReview','deploymentBranch'}, 'owner-reviewed Environment binding required')
    integer(policy['id'], 'existing Environment ID required')
    require(policy['ownerReviewerId']==auth.OWNER_ID and type(policy['ownerReviewerId']) is int and
            type(policy['allowSelfReview']) is bool, 'actual owner reviewer policy required')
    require(type(policy['name']) is str and bool(re.fullmatch('[A-Za-z0-9_-]{1,80}',policy['name'])) and
            policy['deploymentBranch']==BRANCH and ref==REF, 'exact protected Environment/ref binding')
    require(environment.get('id')==policy['id'] and type(environment.get('id')) is int and
            environment.get('name')==policy['name'] and environment.get('can_admins_bypass') is False,
            'missing/replaced/unprotected/bypassable Environment')
    require(environment.get('deployment_branch_policy') == {'protected_branches':False,'custom_branch_policies':True},
            'Environment must restrict selected deployment branch')
    rules=environment.get('protection_rules')
    require(type(rules) is list, 'Environment protection facts missing')
    required=[x for x in rules if x.get('type')=='required_reviewers']
    require(len(required)==1, 'required reviewers protection absent/ambiguous')
    rule=required[0]
    # Exactly the reviewed owner must approve; GitHub accepts any ONE listed reviewer.
    reviewers=rule.get('reviewers')
    require(type(reviewers) is list and len(reviewers)==1 and reviewers[0].get('type')=='User' and
            reviewers[0].get('reviewer',{}).get('id')==auth.OWNER_ID and
            reviewers[0].get('reviewer',{}).get('login')==auth.OWNER_LOGIN,
            'unreviewed reviewer/team could release game job')
    require(type(rule.get('prevent_self_review')) is bool and
            rule['prevent_self_review'] is (not policy['allowSelfReview']), 'self-review policy differs')
    require(environment.get('verified_branch_policies') == [{'type':'branch','name':BRANCH}],
            'exact externally fetched deployment branch policy required')
    return denied('ENVIRONMENT_FACT_CORRESPONDENCE_ONLY', environment=policy['name'])

def owner_expected(frozen, policy):
    return dict(contract=OWNER,intentSha256=frozen['intentSha256'],environment=policy,
                scope=auth.SCOPE,originalOnly=True,maxGames=1,runNumber=1,runAttempt=1)

def release_expected(frozen, policy, owner_id, context, hold_sha):
    return dict(contract=RELEASE,intentSha256=frozen['intentSha256'],ownerDecisionId=owner_id,
                environment=policy,run=context,holdSha256=hold_sha,scope=auth.SCOPE,
                originalOnly=True,maxGames=1)

def synthetic_protection(backend, stage, now):
    # No qualified external Environment backend exists in this local candidate.
    require(backend is not None, 'externally authenticated Environment verification unavailable: STOP')
    value=backend.verify(stage)
    require(type(value) is dict and value.get('origin')==SYNTHETIC and value.get('authenticatedProductionOrigin') is False,
            'self-promoting/unknown external verification provenance refused')
    exact(value, {'origin','authenticatedProductionOrigin','observedAt','environment','policy'}, 'complete synthetic external observation required')
    age=(now-auth.utc(value['observedAt'])).total_seconds()
    require(0<=age<=60, 'stale/future Environment protection observation')
    verify_environment(value['environment'],value['policy'],REF)
    return value

def owner_readback(client, frozen, decision_id, policy, now):
    integer(decision_id, 'actual owner decision ID required after decision exists')
    require(auth.client_owner_matches(client), 'repository owner differs')
    comment=client.get('issues/comments/'+str(decision_id))
    claims,body_sha=auth.decision_claims(comment,decision_id,OWNER,owner_expected(frozen,policy),now)
    require(auth.same_json(comment,client.get('issues/comments/'+str(decision_id))), 'owner decision changed during verification')
    return comment,body_sha,claims

def verify_preflight(data, decision_id, context, *, _client=None, _environment=None, _now=None):
    """Engine-free contract inspection. No original evidence slot is created."""
    now=_now or datetime.now(timezone.utc); client=_client or auth.LiveGitHub()
    frozen=freeze_intent(data)
    protection=synthetic_protection(_environment,'PREPUBLICATION_AND_PREFLIGHT',now)
    policy=protection['policy']
    owner,body_sha,claims=owner_readback(client,frozen,decision_id,policy,now)
    auth.check_history(auth.live_history(client),owner,owner,now)
    run=client.get('actions/runs/'+str(context['runId']))
    commit=client.get('git/commits/'+frozen['intent']['source']['commit'])
    verify_provider(frozen,run,context,commit)
    verify_original_history(run_history(client,run['workflow_id']),run)
    hold=dict(contract='ARGENTUM_ONE_SHOT_ENGINE_FREE_HOLD_V1',origin=SYNTHETIC,
              intentSha256=frozen['intentSha256'],run=context,ownerDecisionId=decision_id,
              ownerBodySha256=body_sha,environment=policy,observedAt=now.strftime('%Y-%m-%dT%H:%M:%SZ'))
    return denied('ENGINE_FREE_HOLD',origin=SYNTHETIC,hold=hold,holdSha256=digest(canonical(hold)),
                  claims=claims,provenance='Synthetic contract only; not a real provider/owner/host approval')

def verify_final(data, owner_id, release_id, context, hold, *, _client=None, _environment=None, _now=None):
    """Separate run-specific release readback immediately before a FUTURE launcher.
    This candidate has no launcher and always emits engineAllowed=false.
    """
    now=_now or datetime.now(timezone.utc); client=_client or auth.LiveGitHub()
    frozen=freeze_intent(data)
    exact(hold, {'contract','origin','intentSha256','run','ownerDecisionId','ownerBodySha256','environment','observedAt'}, 'complete hold required')
    require(hold['contract']=='ARGENTUM_ONE_SHOT_ENGINE_FREE_HOLD_V1' and hold['origin']==SYNTHETIC and
            hold['intentSha256']==frozen['intentSha256'] and hold['ownerDecisionId']==owner_id and
            auth.same_json(hold['run'],context), 'substituted/cross-bound hold')
    require(0<=(now-auth.utc(hold['observedAt'])).total_seconds()<=60, 'stale/future/interrupted hold')
    protection=synthetic_protection(_environment,'POST_OWNER_REVIEW_FINAL_BOUNDARY',now)
    policy=protection['policy'];require(auth.same_json(policy,hold['environment']), 'Environment policy changed during hold')
    owner,body_sha,_=owner_readback(client,frozen,owner_id,policy,now)
    require(body_sha==hold['ownerBodySha256'], 'owner decision edited/substituted during hold')
    integer(release_id, 'separate run-specific owner release missing')
    require(release_id!=owner_id, 'release cannot authenticate itself as prelaunch decision')
    release=client.get('issues/comments/'+str(release_id))
    _,release_sha=auth.decision_claims(release,release_id,RELEASE,
        release_expected(frozen,policy,owner_id,context,digest(canonical(hold))),now)
    require(auth.utc(release['created_at'])>=auth.utc(hold['observedAt']), 'release predates hold/provider run')
    # Re-read complete provider history and protection following the owner hold.
    run=client.get('actions/runs/'+str(context['runId']))
    verify_provider(frozen,run,context,client.get('git/commits/'+frozen['intent']['source']['commit']))
    verify_original_history(run_history(client,run['workflow_id']),run)
    auth.check_history(auth.live_history(client),owner,release,now)
    require(auth.same_json(release,client.get('issues/comments/'+str(release_id))), 'release changed during verification')
    require(auth.same_json(owner,client.get('issues/comments/'+str(owner_id))), 'owner decision changed at final boundary')
    return denied('FINAL_RELEASE_CONTRACT_CORRESPONDENCE_ONLY',origin=SYNTHETIC,
                  intentSha256=frozen['intentSha256'],releaseBodySha256=release_sha,
                  persistentCrossWorkflowSingleUse=False,physicalLaunchOrderingQualified=False)

def inspect_inactive(config):
    exact(config, {'contract','active','publicationAuthorized','gameplayAuthorized','baseline','destinationRef',
                  'workflowPath','sourcePins','protectedEnvironment','ownerDecision','caseIntent','permittedInputs','launcher'},
          'inactive configuration schema')
    require(config['contract']=='ARGENTUM_ONE_SHOT_INACTIVE_CANDIDATE_V1' and
            all(config[k] is False for k in ('active','publicationAuthorized','gameplayAuthorized')), 'candidate must remain inactive')
    require(config['baseline']=={'commit':PARENT,'tree':PARENT_TREE} and
            config['destinationRef']==REF and config['workflowPath']==WORKFLOW, 'inactive baseline/workflow mismatch')
    require(all(config[k] is None for k in ('sourcePins','protectedEnvironment','ownerDecision','caseIntent','permittedInputs','launcher')),
            'future authorization/Environment/case/inputs/launcher must remain unfilled')
    return denied('INACTIVE_LOCAL_REVIEW_CANDIDATE',remainingGates=[
        'independent source/workflow review','actual existing protected Environment verification before any publication',
        'exact external source/workflow pins and zero prior run history','distinct source-bound publication decision',
        'immutable case/permitted inputs and positive owner decision','separate run-specific owner release',
        'reviewed launcher and last controllable initialization boundary'])

def inventory(root):
    """Bounded append-only manifest snapshots; no overwriting previous evidence."""
    root=Path(root);require(root.is_dir() and not root.is_symlink(),'evidence directory required')
    entries=[];total=0
    for p in sorted(root.rglob('*')):
        require(not p.is_symlink(),'evidence symlink refused')
        if p.is_dir():continue
        require(p.is_file(),'nonregular evidence payload')
        require(p.stat().st_size<=16*1024*1024,'bounded evidence payload exceeded')
        data=p.read_bytes()
        # Only our exact reserved snapshot form is excluded, after checking its
        # schema/content identity. An original named manifest-failure.json is
        # ordinary evidence, never silently omitted by a broad filename prefix.
        if re.fullmatch(r'manifest-[0-9a-f]{64}\.json',p.name):
            previous=auth.decode(data)
            exact(previous, {'stage','engineAllowed','gameplayAuthorized','executableActivation',
                'productionQualified','authenticatedProductionOrigin','payloads','timestamps'},'invalid prior inventory snapshot')
            require(previous['stage']=='ORIGINAL_EVIDENCE_INVENTORY_ONLY' and
                    type(previous['payloads']) is list and
                    all(previous[k] is False for k in ('engineAllowed','gameplayAuthorized','executableActivation',
                        'productionQualified','authenticatedProductionOrigin')) and
                    p.name=='manifest-'+digest(canonical(previous['payloads']))+'.json',
                    'altered prior inventory snapshot')
            continue
        total+=len(data)
        require(len(entries)<256 and len(data)<=16*1024*1024 and total<=64*1024*1024,'bounded evidence inventory exceeded')
        entries.append(dict(path=p.relative_to(root).as_posix(),bytes=len(data),sha256=digest(data)))
    path=root/('manifest-'+digest(canonical(entries))+'.json')
    value=denied('ORIGINAL_EVIDENCE_INVENTORY_ONLY',payloads=entries,
                 timestamps='owner/provider controlled; no independent time attestation')
    data=canonical(value)+b'\n'
    if path.exists():require(path.read_bytes()==data,'manifest identity conflict')
    else:
        with path.open('xb') as f:f.write(data)
    return path

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('operation',choices=['inspect-inactive','preflight','final-boundary','inventory'])
    parser.add_argument('--root',type=Path,default=Path.cwd())
    parser.add_argument('--evidence',type=Path)
    args=parser.parse_args()
    if args.operation=='inventory':
        require(args.evidence is not None,'evidence root required');print(inventory(args.evidence));return 0
    state=inspect_inactive(auth.decode((args.root/CONFIG).read_bytes()))
    print(json.dumps(state,sort_keys=True))
    if args.operation!='inspect-inactive':
        print('REFUSED: frozen inactive candidate; no qualified Environment/backend/case/launcher or execution authority',file=sys.stderr)
        return 78
    return 0

if __name__=='__main__':
    raise SystemExit(main())
