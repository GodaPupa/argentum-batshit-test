#!/usr/bin/env python3
"""Create the one permitted Monster Tron block claim. No retries; no gameplay."""
from __future__ import annotations

import argparse
import base64
from dataclasses import dataclass
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
from urllib.error import HTTPError
from urllib.parse import quote
from urllib.request import Request, urlopen

REPOSITORY = 'GodaPupa/argentum-batshit-test'
WORKFLOW_PATH = '.github/workflows/pest-control-tier-one-monster-tron-official-smoke.yml'
BLOCK_ID = 'PEST_CONTROL_V10_VS_MEHANSKE_MONSTER_TRON_2026_09_21_PREBOARD_V1_NONEXPERIMENTAL_SMOKE_4'
CLAIM_REF = 'refs/heads/pest-control/official-attempts/monster-tron-smoke-v1'
CLAIM_PATH = 'docs/experiments/pest-control/official-attempts/monster-tron-smoke-v1/claim.json'
ENGINE_BASELINE = 'a224ef0008a2b85e2c2c106df9959678782e765d'
VECTOR = '1cace17d62bf9133bd834ac0ef7df3bbd29de141716f631764d465867975ab31'
ARCHIVE = '70b9a665fbb154342e2789c1b6b2c2fd912579431a6ae1f9ab289984d5e7801c'
ASSIGNMENTS = '3e8c61d729d219261eb485600039b150d66e2e73e90f0f2e1450c7b48a57e098'
FREEZE_SOURCE = '4230ccaef2760fef54604f64b260dc897d618250'


class ClaimError(RuntimeError):
    pass


class ApiError(ClaimError):
    def __init__(self, status: int):
        self.status = status
        super().__init__(f'GitHub returned HTTP {status}; no mutation will be retried')


def canonical(value: dict) -> bytes:
    return (json.dumps(value, sort_keys=True, separators=(',', ':')) + '\n').encode('utf-8')


def git_blob_sha(raw: bytes) -> str:
    return hashlib.sha1(f'blob {len(raw)}\0'.encode() + raw).hexdigest()


def full_sha(value: str) -> str:
    if not re.fullmatch(r'[0-9a-f]{40}', value):
        raise ClaimError('Expected an immutable, full lowercase Git SHA')
    return value


@dataclass(frozen=True)
class Context:
    source_sha: str
    workflow_sha: str
    run_id: int
    run_attempt: int
    repository: str = REPOSITORY


class GitHub:
    def __init__(self, token: str):
        self.token = token

    def request(self, method: str, path: str, payload: dict | None = None) -> dict:
        request = Request(
            f'https://api.github.com/repos/{REPOSITORY}{path}',
            data=canonical(payload) if payload is not None else None,
            headers={'Authorization': f'Bearer {self.token}', 'Accept': 'application/vnd.github+json',
                     'Content-Type': 'application/json', 'User-Agent': 'Argentum-One-Shot-Claim'},
            method=method,
        )
        try:
            # urllib has no application-level retry policy. An ambiguous write fails closed.
            with urlopen(request, timeout=40) as response:
                return json.load(response)
        except HTTPError as error:
            raise ApiError(error.code) from None


def create_claim(api: GitHub, context: Context) -> dict:
    if context.repository != REPOSITORY or context.run_attempt != 1 or context.run_id <= 0:
        raise ClaimError('Wrong repository, rerun attempt, or run identity')
    full_sha(context.source_sha)
    full_sha(context.workflow_sha)
    ref_endpoint = '/git/ref/' + CLAIM_REF.removeprefix('refs/')
    try:
        api.request('GET', ref_endpoint)
    except ApiError as error:
        if error.status != 404:
            raise
    else:
        raise ClaimError('The block already has a claim; even a failed attempt is never resumed')

    run = api.request('GET', f'/actions/runs/{context.run_id}')
    if (run.get('id') != context.run_id or run.get('run_attempt') != 1
            or run.get('head_sha') != context.workflow_sha or run.get('status') != 'in_progress'
            or run.get('conclusion') is not None or run.get('path', '').split('@')[0] != WORKFLOW_PATH):
        raise ClaimError('Workflow identity, live state, or approved entry point mismatch')
    branch = run.get('head_branch')
    if not isinstance(branch, str) or not branch:
        raise ClaimError('Missing workflow branch identity')
    branch_endpoint = '/git/ref/heads/' + quote(branch, safe='/')

    def verify_branch() -> None:
        current = api.request('GET', branch_endpoint)
        if current.get('object', {}).get('sha') != context.workflow_sha:
            raise ClaimError('Workflow branch moved; reconcile before claiming the block')

    verify_branch()
    source = api.request('GET', f'/git/commits/{context.source_sha}')
    if source.get('sha') != context.source_sha:
        raise ClaimError('Execution source commit mismatch')
    source_tree = full_sha(source['tree']['sha'])
    payload = {
        'schema': 'pest-monster-tron-exclusive-claim-v1', 'block_id': BLOCK_ID,
        'claim_ref': CLAIM_REF, 'execution_source_sha': context.source_sha,
        'execution_source_tree_sha': source_tree, 'engine_baseline_sha': ENGINE_BASELINE,
        'workflow_source_sha': context.workflow_sha, 'workflow_branch': branch,
        'workflow_run_id': context.run_id, 'workflow_run_attempt': 1,
        'vector_sha256': VECTOR, 'archive_sha256': ARCHIVE, 'assignments_sha256': ASSIGNMENTS,
        'freeze_source_sha': FREEZE_SOURCE, 'reserved_games': 4, 'attempt_limit': 1,
        'state': 'CLAIMED_ALL_FOUR_RESERVED_NO_RETRY', 'official_games_initialized': 0,
        'actions_submitted': 0, 'outcome_exposure': 0, 'execution_allowed': False,
    }
    raw = canonical(payload)
    blob = api.request('POST', '/git/blobs', {'content': raw.decode('utf-8'), 'encoding': 'utf-8'})
    blob_sha = full_sha(blob['sha'])
    if blob_sha != git_blob_sha(raw):
        raise ClaimError('GitHub claim blob does not match the intended exact bytes')
    tree = api.request('POST', '/git/trees', {'base_tree': source_tree, 'tree': [
        {'path': CLAIM_PATH, 'mode': '100644', 'type': 'blob', 'sha': blob_sha}]})
    tree_sha = full_sha(tree['sha'])
    commit = api.request('POST', '/git/commits', {
        'message': 'Reserve the immutable Monster Tron smoke block: one attempt, four assignments',
        'tree': tree_sha, 'parents': [context.source_sha],
    })
    commit_sha = full_sha(commit['sha'])
    verify_branch()
    # The only reservation mutation is CREATE, never PATCH/update/force. Concurrent workers
    # may prepare orphan objects, but exactly one can create this single canonical ref.
    created = api.request('POST', '/git/refs', {'ref': CLAIM_REF, 'sha': commit_sha})
    if created.get('ref') != CLAIM_REF or created.get('object', {}).get('sha') != commit_sha:
        raise ClaimError('Claim response is ambiguous; do not retry or initialize')
    confirmed = api.request('GET', ref_endpoint)
    if confirmed.get('ref') != CLAIM_REF or confirmed.get('object', {}).get('sha') != commit_sha:
        raise ClaimError('Claim confirmation mismatch; do not retry or initialize')
    verify_branch()
    return {
        **payload, 'schema': 'pest-monster-tron-exclusive-claim-receipt-v1',
        'claim_commit_sha': commit_sha, 'claim_tree_sha': tree_sha, 'claim_blob_sha': blob_sha,
        'claim_payload_sha256': hashlib.sha256(raw).hexdigest(), 'claim_confirmed': True,
    }


def verify_receipt(api: GitHub, context: Context, receipt: dict) -> dict:
    """Authenticate the receipt against durable GitHub objects without making a mutation."""
    if context.repository != REPOSITORY or context.run_attempt != 1 or context.run_id <= 0:
        raise ClaimError('Wrong repository, rerun attempt, or run identity')
    full_sha(context.source_sha)
    full_sha(context.workflow_sha)
    required = {
        'schema': 'pest-monster-tron-exclusive-claim-receipt-v1', 'block_id': BLOCK_ID,
        'claim_ref': CLAIM_REF, 'execution_source_sha': context.source_sha,
        'engine_baseline_sha': ENGINE_BASELINE, 'workflow_source_sha': context.workflow_sha,
        'workflow_run_id': context.run_id, 'workflow_run_attempt': 1,
        'vector_sha256': VECTOR, 'archive_sha256': ARCHIVE, 'assignments_sha256': ASSIGNMENTS,
        'freeze_source_sha': FREEZE_SOURCE, 'reserved_games': 4, 'attempt_limit': 1,
        'state': 'CLAIMED_ALL_FOUR_RESERVED_NO_RETRY', 'official_games_initialized': 0,
        'actions_submitted': 0, 'outcome_exposure': 0, 'execution_allowed': False,
        'claim_confirmed': True,
    }
    for key, value in required.items():
        if type(receipt.get(key)) is not type(value) or receipt.get(key) != value:
            raise ClaimError(f'Receipt identity mismatch: {key}')
    run = api.request('GET', f'/actions/runs/{context.run_id}')
    if (run.get('id') != context.run_id or run.get('run_attempt') != 1
            or run.get('head_sha') != context.workflow_sha or run.get('status') != 'in_progress'
            or run.get('conclusion') is not None or run.get('path', '').split('@')[0] != WORKFLOW_PATH
            or run.get('head_branch') != receipt.get('workflow_branch')):
        raise ClaimError('Live workflow no longer matches the reservation')
    branch = api.request('GET', '/git/ref/heads/' + quote(receipt['workflow_branch'], safe='/'))
    if branch.get('object', {}).get('sha') != context.workflow_sha:
        raise ClaimError('Workflow branch moved after reservation')
    commit_sha = full_sha(receipt['claim_commit_sha'])
    tree_sha = full_sha(receipt['claim_tree_sha'])
    blob_sha = full_sha(receipt['claim_blob_sha'])
    reference = api.request('GET', '/git/ref/' + CLAIM_REF.removeprefix('refs/'))
    if reference.get('ref') != CLAIM_REF or reference.get('object', {}).get('sha') != commit_sha:
        raise ClaimError('Canonical reservation ref mismatch')
    commit = api.request('GET', f'/git/commits/{commit_sha}')
    if (commit.get('sha') != commit_sha or commit.get('tree', {}).get('sha') != tree_sha
            or [p['sha'] for p in commit.get('parents', [])] != [context.source_sha]):
        raise ClaimError('Reservation commit or parent mismatch')
    source = api.request('GET', f'/git/commits/{context.source_sha}')
    if (source.get('sha') != context.source_sha
            or source.get('tree', {}).get('sha') != receipt.get('execution_source_tree_sha')):
        raise ClaimError('Execution source tree mismatch')
    content = api.request('GET', f'/contents/{CLAIM_PATH}?ref={commit_sha}')
    if content.get('encoding') != 'base64' or content.get('sha') != blob_sha or content.get('path') != CLAIM_PATH:
        raise ClaimError('Reservation file identity mismatch')
    raw = base64.b64decode(''.join(content['content'].split()), validate=True)
    if (git_blob_sha(raw) != blob_sha
            or hashlib.sha256(raw).hexdigest() != receipt.get('claim_payload_sha256')):
        raise ClaimError('Reservation bytes mismatch')
    expected_payload = dict(receipt)
    for key in ('claim_commit_sha', 'claim_tree_sha', 'claim_blob_sha', 'claim_payload_sha256', 'claim_confirmed'):
        expected_payload.pop(key)
    expected_payload['schema'] = 'pest-monster-tron-exclusive-claim-v1'
    if canonical(expected_payload) != raw:
        raise ClaimError('Receipt differs from the exact committed reservation')
    return {'status': 'DURABLE_CLAIM_VERIFIED_NOT_GAMEPLAY_AUTHORIZATION',
            'claim_commit_sha': commit_sha, 'execution_source_sha': context.source_sha,
            'workflow_run_id': context.run_id, 'execution_allowed': False}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source-sha', required=True)
    parser.add_argument('--run-id', type=int, required=True)
    parser.add_argument('--run-attempt', type=int, required=True)
    operation = parser.add_mutually_exclusive_group(required=True)
    operation.add_argument('--receipt', type=Path)
    operation.add_argument('--verify-receipt', type=Path)
    args = parser.parse_args()
    if (os.environ.get('GITHUB_REPOSITORY') != REPOSITORY
            or os.environ.get('GITHUB_RUN_ID') != str(args.run_id)
            or os.environ.get('GITHUB_RUN_ATTEMPT') != str(args.run_attempt)
            or not os.environ.get('GITHUB_TOKEN')):
        raise ClaimError('Missing or mismatched authenticated GitHub workflow context')
    checkout = subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip()
    if checkout != args.source_sha:
        raise ClaimError('Local checkout differs from immutable execution source')
    for command in (['git', 'diff', '--quiet'], ['git', 'diff', '--cached', '--quiet']):
        if subprocess.call(command) != 0:
            raise ClaimError('Execution checkout has tracked modifications')
    api = GitHub(os.environ['GITHUB_TOKEN'])
    context = Context(args.source_sha, os.environ.get('GITHUB_SHA', ''), args.run_id, args.run_attempt)
    if args.verify_receipt:
        verified = verify_receipt(api, context, json.loads(args.verify_receipt.read_bytes()))
        print(canonical(verified).decode('utf-8'), end='')
        return
    if args.receipt.exists():
        raise ClaimError('Receipt already exists; refusing to overwrite or retry')
    args.receipt.parent.mkdir(parents=True, exist_ok=True)
    receipt = create_claim(api, context)
    # A write failure after reservation is terminal: the remote claim remains and blocks reuse.
    with args.receipt.open('xb') as output:
        output.write(canonical(receipt))
        output.flush()
        os.fsync(output.fileno())
    print(canonical(receipt).decode('utf-8'), end='')


if __name__ == '__main__':
    main()
