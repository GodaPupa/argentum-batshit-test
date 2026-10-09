"""Synthetic receiving records only; none are observations of an initialized engine."""
import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest

SPEC=importlib.util.spec_from_file_location('isolated_case',Path(__file__).with_name('isolated_case.py'))
MOD=importlib.util.module_from_spec(SPEC);SPEC.loader.exec_module(MOD)


class ReceivingTests(unittest.TestCase):
    def setUp(self):
        configured=os.environ.get('PLAY_FIRST_RECEIVING_FIXTURE_ROOT')
        self.root=Path(configured).resolve()/self.id().split('.')[-1] if configured else Path(tempfile.mkdtemp())
        self.root.mkdir(exist_ok=True)
        self.expected=dict(blockId='EXCLUDED_RECEIVING_ONLY',gameId='EXCLUDED_CASE',pair=1,pestSeat=0,
                           fixtureSeedHex='NO_SEED',sourceCommit='EXCLUDED_SOURCE',authorizationComment=0)
        self.rows=[
            dict(recordType='EXHIBITION_INTENT',**self.expected,formalSeedAllocation=False,officialExecutionAuthorized=False),
            dict(recordType='INITIALIZED',gameOver=False,player0='fixture0',player1='fixture1'),
            dict(recordType='MULLIGAN_COMPLETE'),
            dict(recordType='ACTION_INTENT',sequence=1),
            dict(recordType='ACTION_RESULT',sequence=1,accepted=True,gameOverAfter=True,lifeAfter=[1,0],
                 emittedEvents=[dict(type='GameEndedEvent',winnerId='fixture0')]),
            dict(recordType='EXHIBITION_TERMINAL',gameId='EXCLUDED_CASE',pestSeat=0,gameOver=True,actions=1,
                 formalExperimentCount=0,lifeBySeat=[1,0],winnerId='fixture0',winnerSeat=0),
        ]
        common=dict(span=1,phase='EXCLUDED_OPERATION',context={},identity=dict(gameId='EXCLUDED_CASE',sourceCommit='EXCLUDED_SOURCE'))
        self.timing=[dict(recordType='CALL_START',**common),dict(recordType='CALL_END',elapsedNanos=1,**common)]
        self.process=dict(processStatus='EXITED_ZERO')

    def inspect(self):
        (self.root/'original.jsonl').write_text(''.join(json.dumps(r)+'\n' for r in self.rows))
        (self.root/'timing.jsonl').write_text(''.join(json.dumps(r)+'\n' for r in self.timing))
        (self.root/'process-result.json').write_text(json.dumps(self.process))
        return MOD.inspect_case(self.root,self.expected)

    def test_synthetic_record_correspondence_only(self):
        self.assertEqual(self.inspect()['status'],'RECORDED_ENGINE_TERMINAL')

    def test_unfinished_timing_is_incomplete(self):
        self.timing.pop(); self.assertEqual(self.inspect()['status'],'INCOMPLETE')

    def test_exit_zero_without_terminal_is_incomplete(self):
        self.rows.pop(); self.assertEqual(self.inspect()['status'],'INCOMPLETE')

    def test_wrong_winner_is_incomplete(self):
        self.rows[-1]['winnerId']='other';self.assertEqual(self.inspect()['status'],'INCOMPLETE')

    def test_timeout_never_becomes_a_clean_result(self):
        self.process['processStatus']='TIMEOUT'
        result=self.inspect();self.assertEqual(result['status'],'INCOMPLETE')
        self.assertTrue(result['engineTerminalObserved'])

    def test_reused_span_is_incomplete(self):
        self.timing=self.timing*2;self.assertEqual(self.inspect()['status'],'INCOMPLETE')


if __name__=='__main__': unittest.main(verbosity=2)
