#!/usr/bin/env python3
"""Read-only Pest Phase-B v5 sharded source/gate binding. Never runs behavioral comparison."""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import subprocess

def require(value, message):
    if not value:
        raise ValueError(message)

def strict_json(raw):
    def pairs(items):
        out = {}
        for key, value in items:
            require(key not in out, 'duplicate JSON key: ' + key)
            out[key] = value
        return out
    return json.loads(raw, object_pairs_hook=pairs)

def git(root, *args):
    return subprocess.check_output(['git', '--no-replace-objects', '-C', str(root), *args])

def commit(root, ref):
    require(re.fullmatch(r'[0-9a-f]{40}', ref) is not None, 'full SHA required')
    require(git(root, 'rev-parse', ref + '^{commit}').decode().strip() == ref, 'commit mismatch')
    return ref

def parents(root, ref):
    return git(root, 'rev-list', '--parents', '-n', '1', ref).decode().split()[1:]

def entries(root, ref):
    out = {}
    for raw in git(root, 'ls-tree', '-r', '-z', ref).split(b'\0'):
        if raw:
            header, name = raw.split(b'\t', 1)
            mode, kind, sha = header.decode().split()
            out[name.decode()] = (mode, kind, sha)
    return out

def changed_paths(root, before, after):
    a, b = entries(root, before), entries(root, after)
    return {p for p in a.keys() | b.keys() if a.get(p) != b.get(p)}

def exact_added(root, before, after, allowed):
    a, b = entries(root, before), entries(root, after)
    changed = changed_paths(root, before, after)
    require(changed == set(allowed), 'unexpected complete tree delta: ' + repr(sorted(changed)))
    for path in allowed:
        require(path not in a and path in b and b[path][:2] == ('100644', 'blob'),
                'addition/mode required: ' + path)

def read_working(root, path, tree):
    relative = Path(path)
    require(not relative.is_absolute() and '..' not in relative.parts, 'unsafe path')
    p = root / relative
    require(p.resolve(strict=True) == p and p.is_file() and not p.is_symlink(),
            'aliased/non-file input: ' + path)
    mode = '100755' if p.stat().st_mode & stat.S_IXUSR else '100644'
    raw = p.read_bytes()
    sha = hashlib.sha1(b'blob ' + str(len(raw)).encode() + b'\0' + raw).hexdigest()
    require(tree.get(path) == (mode, 'blob', sha), 'working bytes/mode drift: ' + path)
    return raw

def verify_shards(plan):
    shards = plan['shards']
    require(type(shards) is list and len(shards) == 32, 'exactly 32 shards required')
    require([s['index'] for s in shards] == list(range(32)), 'shard index inventory drift')
    require(all(type(s['expected_rows']) is int and s['expected_rows'] > 0 for s in shards),
            'shard row count type')
    require(sum(s['expected_rows'] for s in shards) == plan['planner_identity']['expected_rows'],
            'shard rows do not cover global plan')
    require([s['expected_rows'] for s in shards[:8]] == [1091027] * 8, 'large shard geometry drift')
    require([s['expected_rows'] for s in shards[8:]] == [1091026] * 24, 'small shard geometry drift')
    hashes = [s['plan_sha256'] for s in shards]
    require(all(re.fullmatch('[0-9a-f]{64}', h) for h in hashes), 'shard digest format')
    require(len(set(hashes)) == 32, 'shard digests must be distinct')

def load_json_file(path):
    p = Path(path)
    require(p.is_absolute(), 'run-history path must be absolute')
    require(p.resolve(strict=True) == p and p.is_file() and not p.is_symlink(),
            'aliased/non-file run-history input')
    raw = p.read_bytes()
    require(0 < len(raw) <= 16 * 1024 * 1024, 'run-history JSON size')
    return strict_json(raw)

def load_text_file(path):
    p = Path(path)
    require(p.is_absolute(), 'run-history header path must be absolute')
    require(p.resolve(strict=True) == p and p.is_file() and not p.is_symlink(),
            'aliased/non-file run-history headers')
    raw = p.read_bytes()
    require(len(raw) <= 1024 * 1024, 'run-history headers size')
    return raw.decode('utf-8', errors='strict')

def verify_run_history(plan, current, inventory, headers, expected_run_id, expected_head_sha):
    policy = plan['global_one_original']
    branch = policy['execution_branch']
    workflow_path = policy['workflow_path']
    require(policy['event'] == 'push', 'run-history event policy drift')
    require(policy['required_run_attempt'] == 1, 'run-attempt policy drift')
    require(policy['required_run_number'] == 1, 'run-number policy drift')
    require(policy['maximum_matching_runs'] == 1, 'run-count policy drift')
    require(policy['inventory_per_page'] == 100, 'inventory page-size policy drift')
    require(policy['cancel_in_progress'] is False, 'concurrency cancellation policy drift')
    require(type(policy['concurrency_group']) is str and policy['concurrency_group'],
            'concurrency group policy drift')

    require(type(current) is dict, 'current run object')
    require(str(current['id']) == str(expected_run_id), 'current run id drift')
    require(type(current['workflow_id']) is int and current['workflow_id'] > 0, 'workflow id')
    require(current['path'] == workflow_path, 'execution workflow path drift')
    require(current['event'] == policy['event'], 'current run event drift')
    require(current['head_branch'] == branch, 'current run branch drift')
    require(current['head_sha'] == expected_head_sha, 'current run head drift')
    require(current['run_attempt'] == policy['required_run_attempt'], 'current run attempt drift')
    require(current['run_number'] == policy['required_run_number'], 'current run number proves prior run')
    require(current['status'] in ('queued', 'in_progress'), 'current run state drift')
    require(current.get('conclusion') is None, 'current run already concluded')
    require(current['repository']['full_name'] == plan['repository'], 'current run repository drift')
    require(current['head_repository']['full_name'] == plan['repository'], 'current head repository drift')

    require(type(inventory) is dict, 'run inventory object')
    require(type(inventory['total_count']) is int, 'run inventory count type')
    runs = inventory['workflow_runs']
    require(type(runs) is list, 'run inventory list type')
    require(inventory['total_count'] == len(runs), 'incomplete run inventory page')
    require(inventory['total_count'] == policy['maximum_matching_runs'],
            'prior or competing execution workflow run exists')
    require('rel="next"' not in headers.lower(), 'paginated run inventory is ambiguous')
    require(len(runs) == 1, 'exactly one matching execution run required')
    observed = runs[0]
    require(observed['id'] == current['id'], 'inventory current-run mismatch')
    require(observed['workflow_id'] == current['workflow_id'], 'inventory workflow mismatch')
    require(observed['path'] == workflow_path, 'inventory workflow path drift')
    require(observed['event'] == policy['event'], 'inventory event drift')
    require(observed['head_branch'] == branch, 'inventory branch drift')
    require(observed['head_sha'] == expected_head_sha, 'inventory head drift')
    require(observed['run_attempt'] == policy['required_run_attempt'], 'inventory attempt drift')
    require(observed['run_number'] == policy['required_run_number'], 'inventory run-number drift')
    require(observed['repository']['full_name'] == plan['repository'], 'inventory repository drift')
    require(observed['head_repository']['full_name'] == plan['repository'], 'inventory head repository drift')
    return {
        'status': 'SOLE_GLOBAL_EXECUTION_RUN_CONFIRMED_BEFORE_BEHAVIOR',
        'run_id': current['id'],
        'workflow_id': current['workflow_id'],
        'run_number': current['run_number'],
        'run_attempt': current['run_attempt'],
        'head_sha': current['head_sha'],
    }

def implementation(root, implementation_sha, plan):
    commit(root, implementation_sha)
    base = commit(root, plan['source_commit'])
    require(git(root, 'rev-parse', base + '^{tree}').decode().strip() == plan['source_tree'],
            'source tree drift')
    require(plan['schema'] == 'argentum-prospective-sharded-qualification-gate-v1', 'plan schema')
    require(plan['authority'] == 'PREPARATION_ONLY_NO_EXECUTION_AUTHORITY', 'plan authority')
    require(plan['hold'] is True, 'prospective hold must remain true')
    require(type(plan['maximum_originals']) is int and plan['maximum_originals'] == 1, 'scope drift')
    require(type(plan['official_counters_delta']) is int and plan['official_counters_delta'] == 0, 'counter drift')
    require(plan['planner_identity']['expected_rows'] == 34912840, 'global row drift')
    require(plan['planner_identity']['plan_sha256'] ==
            'bcc577b1596c7b37b7f14f0c70d68fefafcf569de3b74f561a0b8317e6e1cc71',
            'global plan digest drift')
    verify_shards(plan)
    policy = plan['global_one_original']
    require(policy['workflow_path'] == plan['workflow_path'], 'run-history workflow binding')
    require(policy['execution_ref'] == plan['execution_ref'], 'run-history ref binding')
    require(policy['execution_branch'] == plan['execution_ref'].removeprefix('refs/heads/'),
            'run-history branch binding')
    require(policy['event'] == 'push', 'run-history event binding')
    require(policy['required_run_attempt'] == 1 and policy['required_run_number'] == 1,
            'run-history first-run binding')
    require(policy['maximum_matching_runs'] == 1 and policy['inventory_per_page'] == 100,
            'run-history inventory binding')
    require(policy['cancel_in_progress'] is False, 'run-history concurrency binding')
    require(policy['concurrency_group'] == 'pest-phase-b-v5-single-aggregate-original-20261004',
            'run-history concurrency group drift')

    allowed = plan['gate_files']
    require(len(allowed) == len(set(allowed)) == 3, 'exact three gate additions required')
    require(plan['workflow_path'] in allowed and plan['plan_path'] in allowed and plan['guard_path'] in allowed,
            'gate path map')
    require(subprocess.call(['git','--no-replace-objects','-C',str(root),'merge-base','--is-ancestor',
                             base, implementation_sha], stdout=subprocess.DEVNULL) == 0, 'source ancestry')
    require(not git(root, 'rev-list', '--merges', base + '..' + implementation_sha).strip(),
            'implementation merges')
    exact_added(root, base, implementation_sha, allowed)

    source, current = entries(root, base), entries(root, implementation_sha)
    for path, sha in plan['protected_blobs'].items():
        require(source.get(path) == current.get(path), 'protected source changed after freeze: ' + path)
        require(current.get(path, (None, None, None))[2] == sha, 'protected source blob drift: ' + path)
    return current

def verify(root, plan_path, mode, implementation_sha=None, current_run_path=None,
           run_inventory_path=None, inventory_headers_path=None):
    root = root.resolve(strict=True)
    require(git(root, 'rev-parse', '--show-toplevel').decode().strip() == str(root), 'repository root')
    head = commit(root, git(root, 'rev-parse', 'HEAD').decode().strip())
    head_tree = entries(root, head)
    plan = strict_json(read_working(root, plan_path, head_tree))
    require(plan['plan_path'] == plan_path, 'plan location')

    if mode == 'run-history':
        require(os.environ.get('GITHUB_REPOSITORY') == plan['repository'], 'repository event')
        require(os.environ.get('GITHUB_EVENT_NAME') == 'push' and
                os.environ.get('GITHUB_REF') == plan['execution_ref'], 'run-history event/ref')
        require(os.environ.get('GITHUB_RUN_ATTEMPT') == '1', 'run-history attempt')
        require(os.environ.get('GITHUB_SHA') == head, 'run-history head')
        require(current_run_path and run_inventory_path and inventory_headers_path,
                'run-history evidence paths required')
        current = load_json_file(current_run_path)
        inventory = load_json_file(run_inventory_path)
        headers = load_text_file(inventory_headers_path)
        return verify_run_history(
            plan, current, inventory, headers,
            os.environ.get('GITHUB_RUN_ID'), head,
        )

    if mode == 'implementation':
        require(implementation_sha == head, 'inspect checked-out exact implementation')
        tree = implementation(root, head, plan)
        for path in plan['gate_files'] + list(plan['protected_blobs']):
            read_working(root, path, tree)
        require(not git(root, 'status', '--porcelain', '--untracked-files=all').strip(),
                'dirty implementation checkout')
        return {'status':'STRUCTURE_ONLY_NOT_REVIEW_OR_EXECUTION','implementation':head}

    require(mode == 'execution', 'mode')
    require(os.environ.get('GITHUB_REPOSITORY') == plan['repository'], 'repository event')
    require(os.environ.get('GITHUB_EVENT_NAME') == 'push' and os.environ.get('GITHUB_REF') == plan['execution_ref'],
            'execution event/ref')
    require(os.environ.get('GITHUB_SHA') == head and os.environ.get('GITHUB_RUN_ATTEMPT') == '1',
            'event head/attempt')
    require(len(parents(root, head)) == 1, 'marker must have one parent')
    receipt_sha = parents(root, head)[0]
    require(len(parents(root, receipt_sha)) == 1, 'receipt must have one parent')
    implementation_sha = parents(root, receipt_sha)[0]
    tree = implementation(root, implementation_sha, plan)

    exact_added(root, implementation_sha, receipt_sha, [plan['receipt_path']])
    exact_added(root, receipt_sha, head, [plan['marker_path']])

    receipt = strict_json(read_working(root, plan['receipt_path'], head_tree))
    marker = strict_json(read_working(root, plan['marker_path'], head_tree))
    for record in (receipt, marker):
        require(record['reviewed_implementation_commit'] == implementation_sha, 'reviewed head drift')
        require(record['source_commit'] == plan['source_commit'] and record['gate_id'] == plan['gate_id'],
                'gate/source drift')
        require(type(record['maximum_originals']) is int and record['maximum_originals'] == 1,
                'authority drift')
        require(type(record['official_counters_delta']) is int and record['official_counters_delta'] == 0,
                'counter drift')

    require(receipt['decision'] == plan['required_decision'], 'no exact gate acceptance')
    require(receipt['external_review_disposition'] == plan['required_review_disposition'],
            'review disposition drift')
    require(receipt['planner_identity'] == plan['planner_identity'], 'reviewed global plan drift')
    require(receipt['shards'] == plan['shards'], 'reviewed shard inventory drift')
    require(marker['receipt_commit'] == receipt_sha, 'receipt commit binding')
    require(marker['receipt_blob'] == head_tree[plan['receipt_path']][2], 'receipt blob binding')
    require(receipt['reviewed_tree'] == git(root, 'rev-parse', implementation_sha + '^{tree}').decode().strip(),
            'reviewed tree')

    paths = set(plan['gate_files']) | set(plan['protected_blobs'])
    expected_map = {p: tree[p][2] for p in sorted(paths)}
    require(receipt['reviewed_blobs'] == expected_map, 'complete reviewed blob map')

    review = receipt['external_review_record_sha256']
    authorization = marker['execution_authorization_record_sha256']
    require(re.fullmatch('[0-9a-f]{64}', review) is not None, 'review digest binding')
    require(marker['external_review_record_sha256'] == review, 'review record mismatch')
    require(re.fullmatch('[0-9a-f]{64}', authorization) is not None, 'authorization digest binding')
    require(review != authorization, 'review and authorization must be distinct')

    forbidden = set(plan['forbidden_record_sha256'])
    require(review not in forbidden, 'stale/consumed record cannot approve v5')
    require(authorization not in forbidden, 'stale/consumed record cannot authorize v5')

    require(marker['aggregate_originals'] == 1, 'aggregate-original scope drift')
    require(marker['shard_count'] == 32, 'shard-count drift')
    require(marker['partition_rule'] == plan['partition_rule'], 'partition-rule drift')

    for p in paths:
        require(head_tree[p] == tree[p], 'post-review drift: ' + p)
        read_working(root, p, head_tree)
    require(not git(root, 'status', '--porcelain', '--untracked-files=all').strip(),
            'dirty execution checkout')

    shard = os.environ.get('PEST_PHASE_B_SHARD_INDEX')
    if shard is not None:
        require(re.fullmatch(r'\d+', shard) is not None, 'shard index syntax')
        index = int(shard)
        require(0 <= index < 32, 'shard index range')
        expected = plan['shards'][index]
        require(str(expected['expected_rows']) == os.environ.get('PEST_PHASE_B_EXPECTED_SHARD_ROWS'),
                'shard row env drift')
        require(expected['plan_sha256'] == os.environ.get('PEST_PHASE_B_EXPECTED_SHARD_PLAN_SHA256'),
                'shard digest env drift')

    return {
        'status':'STRUCTURALLY_BOUND_FRESH_V5_REVIEW_AND_AUTHORIZATION_REQUIRED',
        'implementation':implementation_sha,
        'receipt':receipt_sha,
        'marker':head,
    }

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--plan', required=True)
    parser.add_argument('--mode', required=True, choices=['implementation','execution','run-history'])
    parser.add_argument('--implementation')
    parser.add_argument('--current-run')
    parser.add_argument('--run-inventory')
    parser.add_argument('--inventory-headers')
    args = parser.parse_args()
    print(json.dumps(verify(
        Path.cwd(), args.plan, args.mode, args.implementation,
        args.current_run, args.run_inventory, args.inventory_headers,
    ), sort_keys=True))