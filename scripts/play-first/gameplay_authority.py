#!/usr/bin/env python3
"""Separate read-only authority gate. No engine, seed allocation or execution.

Records contain claims, never credentials. Both issue and verify obtain live
GitHub facts through the fixed TLS endpoint; a supplied record cannot vouch for
itself. Test transport injection is a trusted-code fixture seam, not a CLI mode.
This gate needs a separately reviewed trusted launcher/source/host. It does not
attest that this process is a GitHub job or guarantee cross-host single use.
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

CONTRACT = 'ARGENTUM_GAMEPLAY_AUTHORITY_V1'
REPOSITORY = 'GodaPupa/argentum-batshit-test'
PR = 231
OWNER_ID = 326515697
OWNER_LOGIN = 'GodaPupa'
SOURCE_PARENT = '7b2b3afc84bdea530bfd69e0fc2691a4c837ca23'
SCOPE = 'NONOFFICIAL_PLAY_FIRST_EXHIBITION_ONLY'
RUN_KEYS = {'runId', 'runAttempt', 'runNumber', 'event', 'ref', 'sourceCommit',
            'sourceParent', 'sourceTree', 'validationCommit', 'validationTree',
            'workflowPath', 'workflowBlob'}
CLAIM_KEYS = {'contract', 'decisionId', 'issuerId', 'issuerLogin', 'caseSha256',
              'blockId', 'gameId', 'scope', 'executionAuthorized', 'originalOnly',
              'maxGames', 'run', 'expiresAt'}
CASE_KEYS = {'blockId', 'gameId', 'pair', 'pestSeat', 'fixtureSeedHex',
             'authorizationComment', 'sourceCommit', 'scope', 'executionAuthorized',
             'intendedBlockGames'}
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

def verify_live(case_bytes, run, *, _client=None, _now=None, purpose='receiving'):
    client = LiveGitHub() if _client is None else _client
    now = datetime.now(timezone.utc) if _now is None else _now
    require(purpose in ('execution', 'receiving'), 'authority purpose')
    case = decode(case_bytes)
    require(type(case) is dict and set(case) == CASE_KEYS and type(run) is dict and set(run) == RUN_KEYS, 'case/run authority schema')
    require(type(case.get('authorizationComment')) is int and case['authorizationComment'] > 0, 'decision ID type')
    require(case.get('scope') == SCOPE and case.get('executionAuthorized') is True, 'case scope declaration')
    require(type(case.get('blockId')) is str and bool(re.fullmatch(r'PLAY_FIRST_[A-Z0-9_]{1,80}', case['blockId'])) and 'B01' not in case['blockId'] and type(case.get('gameId')) is str and bool(re.fullmatch(r'G[0-9]{2,4}', case['gameId'])) and case['gameId'] != 'G08', 'historical B01/G08 cannot be replayed')
    require(type(case['pair']) is int and case['pair'] >= 1 and type(case['pestSeat']) is int and case['pestSeat'] in (0,1) and type(case['intendedBlockGames']) is int and 1 <= case['intendedBlockGames'] <= 100, 'case pair/seat/block declaration')
    seed = case['fixtureSeedHex']
    require(type(seed) is str and bool(re.fullmatch(r'0x[0-7][0-9a-f]{15}', seed)) and int(seed,16) > 0 and seed not in ('0x504c415946495253','0x504c4159424c3031','0x504c4159424c3032','0x504c4159424c3033','0x504c4159424c3034'), 'historical/invalid case seed label; no allocation')
    require(run['runAttempt'] == 1 and type(run['runAttempt']) is int and run['runNumber'] == 1 and type(run['runNumber']) is int and run['event'] == 'push', 'one original push only')
    require(run['sourceParent'] == SOURCE_PARENT and case.get('sourceCommit') == run['sourceCommit'], 'repair source/case identity')
    for key in ('sourceCommit', 'sourceParent', 'sourceTree', 'validationCommit', 'validationTree', 'workflowBlob'):
        require(type(run[key]) is str and bool(re.fullmatch('[0-9a-f]{40}', run[key])), 'authority Git identity: ' + key)
    require(type(run['runId']) is str and bool(re.fullmatch('[1-9][0-9]*', run['runId'])), 'authority run ID')
    require(type(run['ref']) is str and run['ref'].startswith('refs/heads/'), 'authority ref')
    require(type(run['workflowPath']) is str and bool(re.fullmatch(r'\.github/workflows/[A-Za-z0-9_-]+\.ya?ml', run['workflowPath'])), 'authority workflow path')
    owner = client.get('')['owner']
    require(owner.get('id') == OWNER_ID and owner.get('login') == OWNER_LOGIN, 'trusted repository owner changed')
    decision_id = case['authorizationComment']
    decision = client.get('issues/comments/' + str(decision_id))
    require(decision.get('id') == decision_id and decision.get('user', {}).get('id') == OWNER_ID and decision.get('user', {}).get('login') == OWNER_LOGIN and decision.get('author_association') == 'OWNER', 'actual controller issuer mismatch')
    require(decision.get('issue_url') == 'https://api.github.com/repos/' + REPOSITORY + '/issues/' + str(PR), 'decision outside controlling PR')
    url = 'https://github.com/' + REPOSITORY + '/pull/' + str(PR) + '#issuecomment-' + str(decision_id)
    require(decision.get('html_url') == url and not decision.get('minimized'), 'decision URL/minimization')
    body = decision.get('body')
    require(type(body) is str and body.startswith(CONTRACT + '\n'), 'no positive gameplay decision; construction/non-gameplay comment')
    claims = decode(body[len(CONTRACT) + 1:])
    require(type(claims) is dict and set(claims) == CLAIM_KEYS, 'positive decision schema')
    expected = dict(contract=CONTRACT, decisionId=decision_id, issuerId=OWNER_ID, issuerLogin=OWNER_LOGIN,
                    caseSha256=sha(case_bytes), blockId=case['blockId'], gameId=case['gameId'], scope=SCOPE,
                    executionAuthorized=True, originalOnly=True, maxGames=1, run=run, expiresAt=claims['expiresAt'])
    require(same_json(claims, expected) and type(claims['issuerId']) is int and type(claims['decisionId']) is int and type(claims['maxGames']) is int and claims['executionAuthorized'] is True and claims['originalOnly'] is True, 'live approved identities/scope/original restrictions differ')
    require(utc(decision['created_at']) <= now < utc(claims['expiresAt']) and utc(decision['updated_at']) <= now, 'stale/not-yet-valid decision')
    endpoints = ('issues/231/comments', 'pulls/231/comments', 'pulls/231/reviews')
    issue_present = False
    for endpoint in endpoints:
        for item in discussion(client, endpoint):
            if endpoint == endpoints[0] and item.get('id') == decision_id:
                require(item.get('body') == body and item.get('updated_at') == decision['updated_at'], 'decision/timeline contradiction')
                issue_present = True
                continue
            if item.get('user', {}).get('id') == OWNER_ID:
                timestamp = item.get('updated_at') or item.get('submitted_at') or item.get('created_at')
                require(timestamp is not None and utc(timestamp) < utc(decision['updated_at']), 'superseding owner discussion, revocation or consumption; fresh explicit decision required')
    require(issue_present, 'decision missing from complete controlling discussion')
    source = client.get('git/commits/' + run['sourceCommit'])
    validation = client.get('git/commits/' + run['validationCommit'])
    require(source.get('sha') == run['sourceCommit'] and source.get('tree', {}).get('sha') == run['sourceTree'] and [p['sha'] for p in source['parents']] == [SOURCE_PARENT], 'actual source identity/sole parent')
    require(validation.get('sha') == run['validationCommit'] and validation.get('tree', {}).get('sha') == run['validationTree'] and [p['sha'] for p in validation['parents']] == [run['sourceCommit']], 'actual validation identity/sole parent')
    workflow = client.get('contents/' + run['workflowPath'] + '?ref=' + run['validationCommit'])
    require(workflow.get('sha') == run['workflowBlob'], 'actual approved workflow blob')
    live_run = client.get('actions/runs/' + run['runId'])
    require(live_run.get('id') == int(run['runId']) and live_run.get('head_sha') == run['validationCommit'] and live_run.get('head_branch') == run['ref'][len('refs/heads/'):] and live_run.get('event') == 'push' and live_run.get('run_attempt') == live_run.get('run_number') == 1 and live_run.get('path') == run['workflowPath'] and live_run.get('repository', {}).get('full_name') == REPOSITORY and live_run.get('actor', {}).get('id') == OWNER_ID, 'actual original run/source/workflow correspondence')
    if purpose == 'execution':
        require(live_run.get('status') == 'in_progress', 'completed/not-running original cannot initialize a game')
    again = client.get('issues/comments/' + str(decision_id))
    require(again.get('body') == body and again.get('updated_at') == decision['updated_at'], 'decision changed during verification')
    return {'contract': CONTRACT, 'claims': claims, 'decisionBodySha256': sha(body.encode()),
            'provenance': {'decisionUrl': url, 'repository': REPOSITORY, 'pr': PR,
                           'decisionUpdatedAt': decision['updated_at'], 'latestOwnerDecisionId': decision_id,
                           'verifiedAt': now.strftime('%Y-%m-%dT%H:%M:%SZ'),
                           'origin': 'LIVE_GITHUB_TLS_OWNER_FACTS' if type(client) is LiveGitHub else 'SYNTHETIC_TRUSTED_FIXTURE_NOT_AUTHENTICATED'}}

def verify_record(case_bytes, run, record, *, _client=None, _now=None, purpose='receiving'):
    require(type(record) is dict and set(record) == {'contract', 'claims', 'decisionBodySha256', 'provenance'}, 'missing/forged external gameplay approval record')
    now = datetime.now(timezone.utc) if _now is None else _now
    require(record.get('contract') == CONTRACT and type(record.get('provenance')) is dict, 'approval contract/provenance')
    verified = utc(record['provenance']['verifiedAt'])
    require(0 <= (now - verified).total_seconds() <= MAX_RECORD_AGE_SECONDS, 'stale external approval record')
    live = verify_live(case_bytes, run, _client=_client, _now=now, purpose=purpose)
    require(same_json(record['claims'], live['claims']) and record['decisionBodySha256'] == live['decisionBodySha256'], 'record cannot authenticate its own claims')
    require({k:v for k,v in record['provenance'].items() if k != 'verifiedAt'} == {k:v for k,v in live['provenance'].items() if k != 'verifiedAt'}, 'forged approval provenance')
    return live

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
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('operation', choices=('issue', 'verify'))
    parser.add_argument('--case', required=True, type=Path)
    parser.add_argument('--run-context', required=True, type=Path)
    parser.add_argument('--record', type=Path)
    parser.add_argument('--purpose', choices=('execution', 'receiving'), default='receiving')
    args = parser.parse_args()
    try:
        case = args.case.read_bytes(); run = decode(args.run_context.read_bytes())
        if args.purpose == 'execution':
            verify_local_execution_source(run)
        if args.operation == 'issue':
            proof = verify_live(case, run, purpose=args.purpose)
        else:
            proof = verify_record(case, run, decode(args.record.read_bytes()) if args.record else None, purpose=args.purpose)
        print(json.dumps(proof, sort_keys=True))
        return 0
    except Exception as error:
        print(json.dumps({'status':'REFUSED', 'errors':[type(error).__name__ + ': ' + str(error)]}))
        return 1

if __name__ == '__main__':
    raise SystemExit(main())
