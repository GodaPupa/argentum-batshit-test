#!/usr/bin/env python3
"""Prospective source/workflow checks and GET-only claim-receipt fixtures. No live mutations."""
import argparse
import base64
import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
from unittest.mock import patch

SOURCE = 'ed35c3035d41a740aac1657708b2023b624cfc47'
SOURCE_TREE = '34406e5a26aad6d6ebf64e9b1a1977e1b719b095'
BASE = 'dc22d47efa7b67442a6120b3a2aa8802c75d5a5a'
REPAIR = 'daf13184d7121e6177f6da1fce34c0bc103a5d9b'
A2 = '7f5524d78cdf90311df02a89ab81e470ba8ce043'
ACTIVATION = '570751227244d555b6fc45793cc3c32917374089'
PUBLICATION = '4816825fb6a4d6535f0365554d204bc964b7ad73'
MAIN_BASE = 'fb6c97fbb76ba5a63fb61ae60905e5b85b5cce07'
FUTURE_BRANCH = 'pest-control/tier1-monster-tron-r1-repaired-official-smoke'
OLD_BRANCH = 'pest-control/tier1-monster-tron-r1-official-smoke'
TEST = 'gym/src/test/kotlin/com/wingedsheep/gym/PestControlTierOneMonsterTronOneShotBoundaryTest.kt'
WORKFLOW = '.github/workflows/pest-control-tier-one-monster-tron-r1-official-smoke.yml'
HELPER = 'scripts/pest-monster-tron-r1-one-shot-claim.py'

def git(root, *args):
    return subprocess.check_output(['git', '-C', str(root), *args])

def text(root, *args):
    return git(root, *args).decode().strip()

def sha(raw):
    return hashlib.sha256(raw).hexdigest()

def check_surface(repo, checkout):
    assert text(checkout, 'rev-parse', 'HEAD') == SOURCE
    assert text(checkout, 'rev-parse', 'HEAD^{tree}') == SOURCE_TREE
    assert text(checkout, 'status', '--porcelain', '--untracked-files=all') == ''
    assert text(repo, 'rev-parse', SOURCE+'^') == BASE
    assert text(repo, 'diff', '--name-only', BASE, SOURCE).splitlines() == [TEST]
    accepted = git(repo, 'show', REPAIR+':'+TEST)
    assert git(repo, 'show', SOURCE+':'+TEST) == accepted == (checkout/TEST).read_bytes()
    assert sha(accepted) == 'fd823637bdfae442ba7f68d91d49d056c0ac18624fc2ef24a9460ff5ed326826'
    assert text(repo, 'rev-parse', SOURCE+':'+TEST) == '40cf4f00284b77c16abc7818cca9ba42a74ea0dc'
    # No qualifier baggage, production mutation, input change or unrelated source delta.
    assert text(repo, 'rev-parse', SOURCE+':.github/workflows') == text(repo, 'rev-parse', BASE+':.github/workflows')
    old = git(repo, 'show', A2+':'+WORKFLOW)
    new = git(repo, 'show', ACTIVATION+':'+WORKFLOW)
    assert old.count(BASE.encode()) == 2
    predicate_old = "github.ref == 'refs/heads/"+OLD_BRANCH+"'"
    predicate_new = "github.ref == 'refs/heads/"+FUTURE_BRANCH+"'"
    assert old.decode().count(predicate_old) == 1
    expected = old.decode().replace(BASE, SOURCE).replace(predicate_old, predicate_new).encode()
    assert new == expected
    assert sha(new) == '814cf66d558fa38eec5cac53458588824a8268419339b3b0f7391e6e97f244fb'
    assert git(repo, 'show', PUBLICATION+':'+WORKFLOW) == new
    assert git(repo, 'show', MAIN_BASE+':'+WORKFLOW) == old
    for parent, head in [(A2, ACTIVATION), (MAIN_BASE, PUBLICATION)]:
        assert text(repo, 'rev-parse', head+'^') == parent
        assert text(repo, 'diff', '--name-only', parent, head).splitlines() == [WORKFLOW]
    # Exact allowed expression, then exhaustive event/attempt/ref matrix for its conjunction.
    expression = "github.event_name == 'workflow_dispatch' && github.run_attempt == 1 && "+predicate_new
    assert ('    if: '+expression+'\n') in new.decode()
    evaluated = 0
    for event in ['push', 'pull_request', 'workflow_dispatch']:
        for attempt in [1, 2]:
            for ref in ['refs/heads/main', 'refs/heads/'+OLD_BRANCH, 'refs/heads/'+FUTURE_BRANCH,
                        'refs/heads/pest/monster-tron-r1-repaired-activation-candidate-20261007']:
                expr = expression.replace('github.event_name', repr(event)).replace('github.run_attempt', str(attempt)).replace('github.ref', repr(ref)).replace('&&', 'and')
                got = eval(expr, {'__builtins__': {}}, {})
                assert got == (event == 'workflow_dispatch' and attempt == 1 and ref == 'refs/heads/'+FUTURE_BRANCH)
                evaluated += 1
    for commit in [BASE, SOURCE, A2, ACTIVATION]:
        assert text(repo, 'rev-parse', commit+':'+HELPER) == '6ff7fcfa39c0f349d4a8bf7f189188be3ea29724'
    assert (checkout/HELPER).read_bytes() == git(repo, 'show', BASE+':'+HELPER)
    return {'execution_source': SOURCE, 'execution_tree': SOURCE_TREE,
            'activation': ACTIVATION, 'publication': PUBLICATION,
            'workflow_blob': text(repo, 'rev-parse', ACTIVATION+':'+WORKFLOW),
            'workflow_sha256': sha(new), 'predicate_cases': evaluated,
            'source_delta': [TEST], 'activation_delta': [WORKFLOW], 'publication_delta': [WORKFLOW]}

def check_read_only_receipt(checkout):
    spec = importlib.util.spec_from_file_location('_pest_repaired_claim_readonly', checkout/HELPER)
    claim = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = claim
    spec.loader.exec_module(claim)
    context = claim.Context(SOURCE, ACTIVATION, 999999, 1)
    # Entirely synthetic durable objects: construct bytes directly; never call create_claim.
    payload = {
        'schema':'pest-monster-tron-exclusive-claim-v1', 'block_id':claim.BLOCK_ID,
        'claim_ref':claim.CLAIM_REF, 'execution_source_sha':SOURCE,
        'execution_source_tree_sha':SOURCE_TREE, 'engine_baseline_sha':claim.ENGINE_BASELINE,
        'workflow_source_sha':ACTIVATION, 'workflow_branch':FUTURE_BRANCH,
        'workflow_run_id':999999, 'workflow_run_attempt':1, 'reserved_games':4, 'attempt_limit':1,
        'vector_sha256':claim.VECTOR, 'archive_sha256':claim.ARCHIVE,
        'assignments_sha256':claim.ASSIGNMENTS, 'freeze_source_sha':claim.FREEZE_SOURCE,
        'state':'CLAIMED_ALL_FOUR_RESERVED_NO_RETRY', 'official_games_initialized':0,
        'actions_submitted':0, 'outcome_exposure':0, 'execution_allowed':False,
    }
    raw = claim.canonical(payload)
    blob = claim.git_blob_sha(raw)
    fake_commit, fake_tree = '3'*40, '4'*40
    receipt = dict(payload, schema='pest-monster-tron-exclusive-claim-receipt-v1',
                   claim_commit_sha=fake_commit, claim_tree_sha=fake_tree, claim_blob_sha=blob,
                   claim_payload_sha256=sha(raw), claim_confirmed=True)
    responses = {
        '/actions/runs/999999':{'id':999999, 'run_attempt':1, 'head_sha':ACTIVATION,
                              'status':'in_progress', 'conclusion':None, 'path':WORKFLOW, 'head_branch':FUTURE_BRANCH},
        '/git/ref/heads/'+FUTURE_BRANCH:{'object':{'sha':ACTIVATION}},
        '/git/ref/'+claim.CLAIM_REF.removeprefix('refs/'):{'ref':claim.CLAIM_REF,'object':{'sha':fake_commit}},
        '/git/commits/'+fake_commit:{'sha':fake_commit,'tree':{'sha':fake_tree},'parents':[{'sha':SOURCE}]},
        '/git/commits/'+SOURCE:{'sha':SOURCE,'tree':{'sha':SOURCE_TREE}},
        '/contents/'+claim.CLAIM_PATH+'?ref='+fake_commit:{'encoding':'base64','sha':blob,'path':claim.CLAIM_PATH,'content':base64.b64encode(raw).decode()},
    }
    calls = []
    class GetOnly:
        def request(self, method, path, payload=None):
            assert method == 'GET' and payload is None, 'Mutation forbidden'
            calls.append((method, path))
            return responses[path]
    with patch.object(claim, 'urlopen', side_effect=AssertionError('Network forbidden')):
        result = claim.verify_receipt(GetOnly(), context, receipt)
        assert result['execution_source_sha'] == SOURCE and result['execution_allowed'] is False
        for field, bad in [('execution_source_sha',BASE), ('execution_source_tree_sha','0'*40),
                           ('workflow_source_sha',A2), ('workflow_run_attempt',2)]:
            altered = dict(receipt, **{field:bad})
            try:
                claim.verify_receipt(GetOnly(), context, altered)
            except claim.ClaimError:
                pass
            else:
                raise AssertionError('Accepted wrong '+field)
        parent = responses['/git/commits/'+fake_commit]['parents']
        responses['/git/commits/'+fake_commit]['parents'] = [{'sha':BASE}]
        try:
            claim.verify_receipt(GetOnly(), context, receipt)
        except claim.ClaimError:
            pass
        else:
            raise AssertionError('Accepted historical source parent')
        responses['/git/commits/'+fake_commit]['parents'] = parent
    return {'valid_current_source_receipt':True, 'rejected_binding_mutations':5,
            'fixture_get_calls':len(calls), 'network_calls':0, 'claim_creation_calls':0}

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--repo', type=Path, required=True)
    parser.add_argument('--source', type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps({'surface':check_surface(args.repo.resolve(),args.source.resolve()),
                      'read_only_receipt':check_read_only_receipt(args.source.resolve()),
                      'authority':'PREPARATION_ONLY__NO_DISPATCH__NO_CLAIM__NO_GAMEPLAY'},sort_keys=True))
