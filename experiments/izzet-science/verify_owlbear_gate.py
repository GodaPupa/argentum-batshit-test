#!/usr/bin/env python3
"""Read-only prospective gate binding; never downloads, compiles, dispatches or runs a worker.

Structural validation is NOT authentication of an external review. Before making R/M the
responsible process must inspect the real review record, scope, exact commit and independence.
"""
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
        result = {}
        for key, value in items:
            require(key not in result, 'duplicate JSON key: ' + key)
            result[key] = value
        return result
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


def exact_added(root, before, after, allowed):
    a, b = entries(root, before), entries(root, after)
    changed = {p for p in a.keys() | b.keys() if a.get(p) != b.get(p)}
    require(changed == set(allowed), 'unexpected complete tree delta: ' + repr(sorted(changed)))
    for path in allowed:
        require(path not in a and path in b and b[path][:2] == ('100644', 'blob'), 'addition/mode required: ' + path)


def read_working(root, path, tree):
    relative = Path(path)
    require(not relative.is_absolute() and '..' not in relative.parts, 'unsafe path')
    p = root / relative
    require(p.resolve(strict=True) == p and p.is_file() and not p.is_symlink(), 'aliased/non-file input: ' + path)
    mode = '100755' if p.stat().st_mode & stat.S_IXUSR else '100644'
    data = p.read_bytes()
    sha = hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()
    require(tree.get(path) == (mode, 'blob', sha), 'working bytes/mode drift: ' + path)
    return data


def implementation(root, implementation_sha, plan):
    commit(root, implementation_sha)
    base = commit(root, plan['base_commit'])
    require(plan['schema'] == 'argentum-prospective-qualification-gate-v1', 'plan schema')
    require(plan['authority'] == 'PREPARATION_ONLY_NO_EXECUTION_AUTHORITY', 'plan is not preparation')
    require(type(plan['maximum_originals']) is int and plan['maximum_originals'] == 1 and type(plan['official_counters_delta']) is int and plan['official_counters_delta'] == 0, 'scope drift')
    allowed = plan['gate_files']
    require(len(allowed) == len(set(allowed)) == 3, 'exact three gate additions required')
    require(plan['workflow_path'] in allowed and plan['plan_path'] in allowed and plan['guard_path'] in allowed, 'gate path map')
    require(subprocess.call(['git', '--no-replace-objects', '-C', str(root), 'merge-base', '--is-ancestor', base, implementation_sha], stdout=subprocess.DEVNULL) == 0, 'base ancestry')
    require(not git(root, 'rev-list', '--merges', base + '..' + implementation_sha).strip(), 'implementation merges')
    exact_added(root, base, implementation_sha, allowed)
    old, new = entries(root, base), entries(root, implementation_sha)
    # Full diff above rejects any additional workflow, extensionless support, deletion or mode drift.
    for path, sha in plan['protected_blobs'].items():
        require(old.get(path) == new.get(path) and new.get(path, (None, None, None))[2] == sha, 'protected source drift: ' + path)
    return new


def verify(root, plan_path, mode, implementation_sha=None):
    root = root.resolve(strict=True)
    require(git(root, 'rev-parse', '--show-toplevel').decode().strip() == str(root), 'repository root')
    head = commit(root, git(root, 'rev-parse', 'HEAD').decode().strip())
    head_tree = entries(root, head)
    plan = strict_json(read_working(root, plan_path, head_tree))
    require(plan['plan_path'] == plan_path, 'plan location')
    if mode == 'implementation':
        require(implementation_sha == head, 'inspect the checked-out exact implementation')
        tree = implementation(root, head, plan)
        for path in plan['gate_files'] + list(plan['protected_blobs']):
            read_working(root, path, tree)
        require(not git(root, 'status', '--porcelain', '--untracked-files=all').strip(), 'dirty implementation checkout')
        return {'status': 'STRUCTURE_ONLY_NOT_REVIEW_OR_EXECUTION', 'implementation': head}
    require(mode == 'execution', 'mode')
    require(os.environ.get('GITHUB_REPOSITORY') == plan['repository'], 'repository event')
    require(os.environ.get('GITHUB_EVENT_NAME') == 'push' and os.environ.get('GITHUB_REF') == plan['execution_ref'], 'execution event/ref')
    require(os.environ.get('GITHUB_SHA') == head and os.environ.get('GITHUB_RUN_ATTEMPT') == '1', 'event head/attempt')
    require(len(parents(root, head)) == 1, 'marker must have one parent')
    r_sha = parents(root, head)[0]
    require(len(parents(root, r_sha)) == 1, 'receipt must have one parent')
    i_sha = parents(root, r_sha)[0]
    tree = implementation(root, i_sha, plan)
    exact_added(root, i_sha, r_sha, [plan['receipt_path']])
    exact_added(root, r_sha, head, [plan['marker_path']])
    receipt_raw = read_working(root, plan['receipt_path'], head_tree)
    marker = strict_json(read_working(root, plan['marker_path'], head_tree))
    receipt = strict_json(receipt_raw)
    for record in (receipt, marker):
        require(record['reviewed_implementation_commit'] == i_sha, 'reviewed head drift')
        require(record['base_commit'] == plan['base_commit'] and record['gate_id'] == plan['gate_id'], 'gate/base drift')
        require(type(record['maximum_originals']) is int and record['maximum_originals'] == 1 and type(record['official_counters_delta']) is int and record['official_counters_delta'] == 0, 'authority drift')
    require(receipt['decision'] == plan['required_decision'], 'no exact gate acceptance')
    require(marker['receipt_commit'] == r_sha, 'receipt commit binding')
    require(marker['receipt_blob'] == head_tree[plan['receipt_path']][2], 'receipt blob binding')
    require(receipt['reviewed_tree'] == git(root, 'rev-parse', i_sha + '^{tree}').decode().strip(), 'reviewed tree')
    paths = set(plan['gate_files']) | set(plan['protected_blobs'])
    expected_map = {p: tree[p][2] for p in sorted(paths)}
    require(receipt['reviewed_blobs'] == expected_map, 'complete reviewed blob map')
    # This digest is only a binding. Authenticity of those external review bytes is a separate gate.
    require(re.fullmatch('[0-9a-f]{64}', receipt['external_review_record_sha256']) is not None, 'review record binding')
    require(marker['external_review_record_sha256'] == receipt['external_review_record_sha256'], 'review record mismatch')
    require(receipt['external_review_record_sha256'] != plan.get('component_review_sha256'), 'component findings are not gate acceptance')
    require(re.fullmatch('[0-9a-f]{64}', marker['execution_authorization_record_sha256']) is not None, 'separate execution authorization binding')
    for p in paths:
        require(head_tree[p] == tree[p], 'post-review drift: ' + p)
        read_working(root, p, head_tree)
    require(not git(root, 'status', '--porcelain', '--untracked-files=all').strip(), 'dirty execution checkout')
    return {'status': 'STRUCTURALLY_BOUND_EXTERNAL_REVIEW_REQUIRED', 'implementation': i_sha, 'receipt': r_sha, 'marker': head}


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--plan', required=True)
    parser.add_argument('--mode', required=True, choices=['implementation', 'execution'])
    parser.add_argument('--implementation')
    args = parser.parse_args()
    print(json.dumps(verify(Path.cwd(), args.plan, args.mode, args.implementation), sort_keys=True))
