import copy
import tempfile
import unittest
from pathlib import Path
from audit_priority_117_3b import inspect_game, audit

SOURCE = '1' * 40


def fixture():
    def action(sequence, player, kind, events):
        return {'sequence': sequence, 'turn': 1, 'actingPlayerId': player,
                'pendingDecisionType': None, 'selectedAction': {'type': kind, 'playerId': player},
                'emittedEvents': events, 'accepted': True}
    return {'provenance': {'sourceCommit': SOURCE, 'pestSeat': 'SEAT_ZERO',
                           'startingDeck': 'PEST_CONTROL', 'gameNumber': 1},
            'actions': [
                action(1, 'e1', 'CastSpell', [{'type': 'SpellCastEvent', 'spellEntityId': 's1',
                                             'casterId': 'e1', 'cardName': 'Thought Scour'}]),
                action(2, 'e0', 'PassPriority', [{'type': 'ResolvedEvent', 'entityId': 's1',
                                                'name': 'Thought Scour'}]),
                action(3, 'e1', 'PassPriority', []),
            ]}


def witnesses(game):
    return inspect_game(game, 'game-1.raw', 'digest', SOURCE)['confirmed_priority_violations']


class PriorityAuditTest(unittest.TestCase):
    def test_actual_nonactive_spell_and_immediate_accepted_pass_is_witness(self):
        self.assertEqual(len(witnesses(fixture())), 1)

    def test_actions_not_requiring_priority_are_never_reduced_witnesses(self):
        for kind in ('Concede', 'DeclareBlockers', 'SubmitDecision', 'DeclareAttackers'):
            with self.subTest(kind=kind):
                game = fixture()
                game['actions'][2]['selectedAction']['type'] = kind
                self.assertEqual(witnesses(game), [])

    def test_next_turn_pending_and_rejected_actions_are_not_witnesses(self):
        changes = [('turn', 2), ('pendingDecisionType', 'SelectCardsDecision'), ('accepted', False)]
        for key, value in changes:
            with self.subTest(key=key):
                game = fixture()
                game['actions'][2][key] = value
                self.assertEqual(witnesses(game), [])
        game = fixture()
        game['actions'][1]['emittedEvents'].append({'type': 'DecisionRequestedEvent'})
        self.assertEqual(witnesses(game), [])
        game = fixture()
        game['actions'][1]['accepted'] = False
        self.assertEqual(witnesses(game), [])

    def test_active_player_receiving_priority_is_not_a_violation(self):
        game = fixture()
        game['actions'][2]['actingPlayerId'] = 'e0'
        game['actions'][2]['selectedAction']['playerId'] = 'e0'
        self.assertEqual(witnesses(game), [])

    def test_unmapped_or_active_player_spell_is_not_reduced_evidence(self):
        game = fixture()
        game['actions'][0]['emittedEvents'] = []
        self.assertEqual(witnesses(game), [])
        game = fixture()
        game['actions'][0]['emittedEvents'][0]['casterId'] = 'e0'
        self.assertEqual(witnesses(game), [])

    def test_actor_source_or_sequence_disagreement_fails_closed(self):
        for mutate in (
            lambda game: game['actions'][2]['selectedAction'].update(playerId='e0'),
            lambda game: game['provenance'].update(sourceCommit='2' * 40),
            lambda game: game['provenance'].update(executionCommit='2' * 40),
            lambda game: game['actions'][2].update(sequence=9),
        ):
            game = fixture()
            mutate(game)
            with self.assertRaises(ValueError):
                witnesses(game)

    def test_archive_tampering_refuses_before_parsing(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'evidence.zip').write_bytes(b'changed bytes, not the admitted ZIP')
            inputs = {'blocks': [{'name': 'test', 'archive_filename': 'evidence.zip',
                                  'archive_sha256': '0' * 64}]}
            with self.assertRaisesRegex(ValueError, 'digest mismatch'):
                audit(inputs, root)

    def test_missing_or_duplicate_archive_refuses(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            inputs = {'blocks': [{'name': 'test', 'archive_filename': 'evidence.zip'}]}
            with self.assertRaisesRegex(ValueError, 'Expected one archive'):
                audit(inputs, root)
            (root / 'a').mkdir()
            (root / 'b').mkdir()
            (root / 'a' / 'evidence.zip').write_bytes(b'a')
            (root / 'b' / 'evidence.zip').write_bytes(b'b')
            with self.assertRaisesRegex(ValueError, 'Expected one archive'):
                audit(inputs, root)


if __name__ == '__main__':
    unittest.main()
