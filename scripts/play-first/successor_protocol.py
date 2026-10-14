"""Successor V2 lineage and allocation checks. No allocation, writes or engine.

Provider numbers below identify ONLY the permanently consumed predecessor.
No future provider ID, game ID or seed is selected by this module.
"""
import gameplay_authority as auth
import one_shot_actions as shot

PROTOCOL = 'ARGENTUM_SUCCESSOR_PROTOCOL_V2'
ALLOCATION = 'ARGENTUM_SUCCESSOR_OWNER_ALLOCATION_V2'
ACCEPTED_REPAIR = dict(commit='54e0fedbcd6c9c7e719ae4e8720f24fb2633fd2a',
                       tree='3792b023fd430030ad2d46937c0b1a8de1454f68')
PREDECESSOR = dict(
    runId=38584391993, workflowId=384448991, runNumber=1, runAttempt=1,
    source='05c8b42fd1c1b3288c5df46524a344382e519de6',
    tree='528c375ad1d8d9936e700c9952bc8ec5871c442a',
    parent='0d1ee8e3ca4c0a65e4213320970a754d5805cec3',
    ref='refs/heads/play-first/one-shot-nonofficial-20261013',
    workflow='.github/workflows/play-first-one-shot-nonofficial-20261013.yml',
    preflightArtifactId=11904429000, failureArtifactId=11912278351,
    preflightSha256='0e3abb6da6f4e650918e45ae8dff28a084451b5dc49cb29c17eb8b1f8948ecfd',
    failureSha256='0f713ba449b37dc5f5fcb846d58ad566eed0bc029415e70cc7b80739cf32ff1e',
    holdSha256='582c0fafd9828209eeac7e6052b8402e1e577b7ce1150cbdbc31cfe691f8a35b',
    releaseId=6172929774,
    releaseBodySha256='0488bee1737132af0d9d9a7131ceb1d4e3333d69db6c91e5fd84496639fe4c72',
    classification='FAILURE_BEFORE_GAMEPLAY', permanentlyConsumed=True,
    gameResult=None, formalCounterDelta=0)
OLD_COMMENTS = frozenset((6156932491, 6157033630, 6161238374, 6161265524, 6172929774))
OLD_SEED = '0x3b2031cad7c657c4'
OLD_CASE = 'PLAY_FIRST_ONESHOT_20261013-G09'


def binding(request):
    return dict(protocol=PROTOCOL, predecessor=PREDECESSOR, acceptedRepair=ACCEPTED_REPAIR,
                allocationDecisionId=request['allocationDecisionId'],
                allocationBodySha256=request['allocationBodySha256'])


def validate_request(request, frozen, case):
    ids = [request[k] for k in ('allocationDecisionId', 'ownerDecisionId', 'environmentVerificationId')]
    for ident in ids:
        shot.integer(ident, 'fresh authenticated successor decision required')
        shot.require(ident not in OLD_COMMENTS, 'consumed G09 decision cannot authorize successor')
    shot.require(len(set(ids)) == 3, 'allocation, Environment and execution decisions must be distinct')
    shot.hex_identity(request['allocationBodySha256'], 64, 'allocation body hash required')
    inp = case['case']
    shot.require(inp['gameId'] != 'G09' and inp['blockId'] != 'PLAY_FIRST_ONESHOT_20261013'
                 and frozen['intent']['case']['caseId'] != OLD_CASE,
                 'consumed G09 case cannot be reused')
    shot.require(inp['fixtureSeedHex'] != OLD_SEED, 'consumed G09 seed cannot be reused')
    shot.require(frozen['intent']['source']['commit'] not in
                 (PREDECESSOR['source'], ACCEPTED_REPAIR['commit']), 'fresh successor source required')
    policy = request['environmentPolicy']
    shot.require(policy['id'] != 24201675024 and
                 policy['name'] != 'argentum-one-shot-nonofficial-20261013',
                 'consumed G09 Environment cannot be reused')


def allocation_expected(frozen):
    """Owner selects inputs separately, then binds exact frozen bytes; no future IDs."""
    return dict(contract=ALLOCATION, protocol=PROTOCOL, predecessor=PREDECESSOR,
                acceptedRepair=ACCEPTED_REPAIR, intentSha256=frozen['intentSha256'],
                caseSha256=frozen['intent']['case']['permittedInputsSha256'],
                newCaseAndSeedExplicitlySelected=True, predecessorNotReplaced=True,
                separateNewNonofficialOriginal=True, originalOnly=True, maxGames=1,
                runNumber=1, runAttempt=1, scope=auth.SCOPE,
                publicationAuthorized=False, gameplayAuthorized=False,
                formalAdmissionAuthorized=False)


def verify_predecessor(client):
    """Read-only provider evidence correspondence, not authority inherited from G09.

Artifact expiration/removal is a failure, not permission to replace evidence.
The pinned archives and owner classification establish failure-before-gameplay;
this API check alone does not interpret a gameplay trace.
"""
    p = PREDECESSOR
    run = client.get('actions/runs/' + str(p['runId']))
    expected = dict(id=p['runId'], workflow_id=p['workflowId'], run_number=1, run_attempt=1,
                    head_sha=p['source'], head_branch=p['ref'].removeprefix('refs/heads/'),
                    path=p['workflow'], event='push', status='completed', conclusion='failure')
    for key, value in expected.items():
        shot.require(key in run and auth.same_json(run[key], value), 'predecessor run changed: ' + key)
    for key in ('repository', 'head_repository'):
        shot.require(run.get(key, {}).get('full_name') == auth.REPOSITORY, 'foreign predecessor')
    shot.verify_original_history(shot.run_history(client, p['workflowId']), run)
    commit = client.get('git/commits/' + p['source'])
    shot.require(commit.get('sha') == p['source'] and commit.get('tree', {}).get('sha') == p['tree']
                 and [x.get('sha') for x in commit.get('parents', [])] == [p['parent']],
                 'original source identity changed')
    artifacts = []
    for kind in ('preflight', 'failure'):
        artifact = client.get('actions/artifacts/' + str(p[kind + 'ArtifactId']))
        shot.require(type(artifact.get('id')) is int and artifact['id'] == p[kind + 'ArtifactId']
                     and artifact.get('expired') is False
                     and artifact.get('digest') == 'sha256:' + p[kind + 'Sha256']
                     and artifact.get('workflow_run', {}).get('id') == p['runId']
                     and artifact.get('workflow_run', {}).get('head_sha') == p['source'],
                     'original artifact missing, expired, altered or cross-bound')
        artifacts.append(artifact)
    shot.require(type(run.get('updated_at')) is str, 'predecessor completion observation missing')
    auth.utc(run['updated_at'])
    return dict(run=run, artifacts=artifacts)


def verify_allocation(client, request, frozen, now, decision):
    evidence = verify_predecessor(client)
    record, _, body_sha = decision(client, request['allocationDecisionId'], ALLOCATION,
                                   allocation_expected(frozen), now)
    shot.require(body_sha == request['allocationBodySha256'], 'allocation bytes changed')
    shot.require(auth.utc(record['created_at']) >= auth.utc(evidence['run']['updated_at']),
                 'successor allocation predates consumed original')
    return record, evidence


def reject_previous_context(context, run):
    shot.require(context['runId'] != PREDECESSOR['runId'] and
                 run['workflow_id'] != PREDECESSOR['workflowId'],
                 'consumed G09 provider identity cannot authorize successor')
