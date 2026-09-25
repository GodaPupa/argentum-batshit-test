#!/usr/bin/env python3
"""Read-only audit of already-retired Pest artifacts; never initializes or replays games."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def inspect_game(game, member, member_digest, expected_source):
    provenance = game['provenance']
    if provenance['sourceCommit'] != expected_source:
        raise ValueError('Per-game source does not match the verified official run')
    if provenance.get('executionCommit', expected_source) != expected_source:
        raise ValueError('Per-game execution source mismatch')
    actions = game.get('actions', game.get('priorityActions', []))
    if not actions or any(row['sequence'] != i + 1 for i, row in enumerate(actions)):
        raise ValueError('Missing or nonsequential actions')
    if any(row['selectedAction'].get('playerId') != row['actingPlayerId'] for row in actions):
        raise ValueError('Selected action player disagrees with recorded actor')
    pest = 'e0' if provenance['pestSeat'] == 'SEAT_ZERO' else 'e1'
    opponent = 'e1' if pest == 'e0' else 'e0'
    active = pest if provenance['startingDeck'] == 'PEST_CONTROL' else opponent
    cast_history = {}
    violations = []
    for i, action in enumerate(actions):
        events = action['emittedEvents']
        active = action.get('activePlayerId', active)
        for event in events:
            if event['type'] == 'SpellCastEvent':
                cast_history[event['spellEntityId']] = {
                    'casterId': event['casterId'], 'cardName': event['cardName']}
        resolved = [event for event in events if event['type'] in ('ResolvedEvent', 'AbilityResolvedEvent')]
        following = actions[i + 1] if i + 1 < len(actions) else None
        if action['selectedAction']['type'] == 'PassPriority' and resolved and action['accepted'] and following:
            after = action.get('afterAuditState')
            observed = None
            if after is not None:
                observed = after['priorityPlayerId']
                after_active = after['activePlayerId']
                pending = after['pendingDecisionType'] is not None
            else:
                after_active = active
                pending = any(event['type'] == 'DecisionRequestedEvent' for event in events)
                # Unlike Concede or combat declarations, an accepted PassPriority requires
                # priority and no pending decision in this two-player, non-team engine.
                if (following['turn'] == action['turn'] and
                        following['pendingDecisionType'] is None and
                        following['selectedAction']['type'] == 'PassPriority'):
                    observed = following['actingPlayerId']
            mapped = [dict(event, cast=cast_history[event['entityId']]) for event in resolved
                      if event['type'] == 'ResolvedEvent' and event['entityId'] in cast_history]
            nonactive_spell = any(event['cast']['casterId'] != active for event in mapped)
            # Reduced traces require an actual nonactive-player spell resolution. Full
            # Red snapshots directly supply active/priority state, including ability cases.
            if (observed is not None and observed != after_active and not pending and
                    (after is not None or nonactive_spell) and following['accepted'] and
                    following['pendingDecisionType'] is None):
                violations.append({
                    'sequence': action['sequence'], 'turn': action['turn'],
                    'active_player': after_active, 'observed_next_priority_player': observed,
                    'next_action_sequence': following['sequence'],
                    'next_action_type': following['selectedAction']['type'],
                    'next_action_player': following['actingPlayerId'],
                    'resolution_events': resolved, 'mapped_spell_casts': mapped,
                    'evidence_kind': 'AFTER_AUDIT_STATE' if after is not None else
                                     'NEXT_ACCEPTED_PASS_PRIORITY_WITHOUT_PENDING_DECISION',
                })
        for event in events:
            if event['type'] == 'TurnChangedEvent':
                active = event['activePlayerId']
    return {'game_number': provenance['gameNumber'], 'raw_member': member,
            'raw_member_sha256': member_digest, 'actions': len(actions),
            'confirmed_priority_violations': violations}


def audit(inputs, archive_root):
    findings = []
    for block in inputs['blocks']:
        candidates = list(archive_root.rglob(block['archive_filename']))
        if len(candidates) != 1:
            raise ValueError(f"Expected one archive for {block['name']}, found {len(candidates)}")
        archive = candidates[0]
        if sha256(archive.read_bytes()) != block['archive_sha256']:
            raise ValueError(f"Archive digest mismatch: {block['name']}")
        games = []
        with zipfile.ZipFile(archive) as source:
            if block['name'] == 'red':
                raw = source.read('aggregate-raw.json')
                document = json.loads(raw)
                if len(document['games']) != block['expected_games']:
                    raise ValueError('Red aggregate count mismatch')
                digest = sha256(raw)
                games = [inspect_game(game, 'aggregate-raw.json', digest, block['execution_source_sha']) for game in document['games']]
            else:
                members = [name for name in source.namelist() if name.endswith('.raw') or
                           (name.startswith('game-') and name.endswith('.json'))]
                if len(members) != block['expected_games']:
                    raise ValueError(f"Game count mismatch: {block['name']}")
                for member in members:
                    raw = source.read(member)
                    games.append(inspect_game(json.loads(raw), member, sha256(raw), block['execution_source_sha']))
        games.sort(key=lambda game: game['game_number'])
        if len({game['game_number'] for game in games}) != len(games):
            raise ValueError(f"Duplicate game number: {block['name']}")
        findings.append({
            'block': block['name'], 'archive_sha256': block['archive_sha256'],
            'artifact_id': block['artifact_id'], 'workflow_run_id': block['workflow_run_id'],
            'execution_source_sha': block['execution_source_sha'],
            'games_with_confirmed_violation': sum(bool(game['confirmed_priority_violations']) for game in games),
            'confirmed_priority_violations': sum(len(game['confirmed_priority_violations']) for game in games),
            'games': games,
        })
    return {'schema': 'pest-priority-117-3b-findings-v1',
            'method': 'CONSERVATIVE_LOWER_BOUND_NO_REPLAY_NO_COUNTERFACTUAL_WINNERS',
            'source_handler_blob_sha': inputs['handler_blob_sha'], 'blocks': findings}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('inputs', type=Path)
    parser.add_argument('--archive-root', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    result = audit(json.loads(args.inputs.read_text()), args.archive_root)
    args.output.write_text(json.dumps(result, indent=2) + '\n')
    for block in result['blocks']:
        print(f"{block['block']}: {block['games_with_confirmed_violation']}/{len(block['games'])} "
              f"games; {block['confirmed_priority_violations']} confirmed transitions")


if __name__ == '__main__':
    main()
