"""Excluded synthetic child-process tests. No engine imports, games, or historic inputs."""
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import tempfile
import time
import unittest

SPEC = importlib.util.spec_from_file_location('isolated_case', Path(__file__).with_name('isolated_case.py'))
MOD = importlib.util.module_from_spec(SPEC); SPEC.loader.exec_module(MOD)


class IsolationTests(unittest.TestCase):
    def setUp(self):
        configured = os.environ.get('PLAY_FIRST_PROCESS_FIXTURE_ROOT')
        self.parent = Path(configured).resolve() / self.id().split('.')[-1] if configured else Path(tempfile.mkdtemp())
        self.parent.mkdir(exist_ok=True)
        self.root = self.parent / 'case'

    def run_fixture(self, body, seconds=3):
        return MOD.supervise([sys.executable, '-S', '-c', body], self.parent, self.root,
                             seconds, grace=0.15, identity='EXCLUDED_PROCESS_FIXTURE')

    def test_zero_exit_is_not_a_game(self):
        result = self.run_fixture("print('synthetic process')")
        self.assertEqual(result['processStatus'], 'EXITED_ZERO')
        self.assertEqual(MOD.inspect_case(self.root, {})['status'], 'INCOMPLETE')
        self.assertFalse((self.root/'original.jsonl').exists())

    def test_failure_preserves_stdout(self):
        result = self.run_fixture("print('prefix',flush=True);raise SystemExit(7)")
        self.assertEqual(result['returnCode'], 7)
        self.assertEqual(result['processStatus'], 'EXITED_NONZERO')
        self.assertIn('prefix', (self.root/'child.log').read_text())

    def test_timeout_preserves_partial_bytes(self):
        result = self.run_fixture("import os,time,pathlib;pathlib.Path(os.environ['PLAY_FIRST_ISOLATED_EVIDENCE_ROOT'],'partial.txt').write_text('original');time.sleep(10)", 1.0)
        self.assertEqual(result['processStatus'], 'TIMEOUT')
        self.assertEqual((self.root/'partial.txt').read_text(), 'original')
        self.assertLess(result['elapsedSeconds'], 3)

    def test_duplicate_original_is_refused(self):
        self.run_fixture("print('original')")
        before = (self.root/'child.log').read_bytes()
        with self.assertRaises(FileExistsError):
            self.run_fixture("print('replacement')")
        self.assertEqual((self.root/'child.log').read_bytes(), before)

    def test_bad_budgets_do_not_create_slots(self):
        for seconds in (0, -1, float('nan'), float('inf'), 99999):
            with self.assertRaises(ValueError):
                self.run_fixture('pass', seconds)
        self.assertFalse(self.root.exists())

    def test_spawn_failure_is_preserved(self):
        result = MOD.supervise(['/EXCLUDED/NO_SUCH_EXECUTABLE'], self.parent, self.root, 1)
        self.assertEqual(result['processStatus'], 'SUPERVISOR_ERROR')
        self.assertTrue((self.root/'process-result.json').exists())

    def test_manifest_covers_every_frozen_payload(self):
        self.run_fixture("print('manifest fixture')")
        entries = json.loads((self.root/'supervisor-manifest.json').read_text())
        self.assertEqual({x['path'] for x in entries}, {p.name for p in self.root.iterdir()} - {'supervisor-manifest.json'})
        for entry in entries:
            b=(self.root/entry['path']).read_bytes()
            self.assertEqual(entry['bytes'],len(b));self.assertEqual(entry['sha256'],hashlib.sha256(b).hexdigest())

    def test_timeout_does_not_consume_another_case_budget(self):
        self.run_fixture('import time;time.sleep(10)', 0.4)
        result = MOD.supervise([sys.executable,'-S','-c',"print('next original')"],self.parent,self.parent/'case2',3,grace=0.15)
        self.assertEqual(result['processStatus'],'EXITED_ZERO')

    def test_old_optin_is_not_inherited(self):
        old = os.environ.get('PLAY_FIRST_B01_ACK')
        os.environ['PLAY_FIRST_B01_ACK']='must-not-leak'
        try:
            result=self.run_fixture("import os;assert 'PLAY_FIRST_B01_ACK' not in os.environ")
            self.assertEqual(result['processStatus'],'EXITED_ZERO')
        finally:
            if old is None: os.environ.pop('PLAY_FIRST_B01_ACK',None)
            else: os.environ['PLAY_FIRST_B01_ACK']=old

    def test_term_ignoring_child_is_killed(self):
        result=self.run_fixture("import signal,time;signal.signal(signal.SIGTERM,signal.SIG_IGN);print('ready',flush=True);time.sleep(10)",1.0)
        self.assertEqual(result['processStatus'],'TIMEOUT')
        self.assertEqual(result['returnCode'],-signal.SIGKILL)

    def test_unfinished_selection_remains_visible(self):
        body="import pathlib,os,time; p=pathlib.Path(os.environ['PLAY_FIRST_ISOLATED_EVIDENCE_ROOT'],'timing.jsonl');p.write_text('{\"recordType\":\"CALL_START\",\"phase\":\"CHOOSE_ACTION\"}\\n');time.sleep(10)"
        result=self.run_fixture(body,1.0)
        self.assertEqual(result['processStatus'],'TIMEOUT')
        rows=(self.root/'timing.jsonl').read_text().splitlines()
        self.assertEqual(len(rows),1);self.assertEqual(json.loads(rows[0])['phase'],'CHOOSE_ACTION')

    def test_parent_exit_does_not_leave_child_running(self):
        body="import subprocess,sys;subprocess.Popen([sys.executable,'-S','-c','import time;time.sleep(10)']);print('parent done')"
        result=self.run_fixture(body)
        self.assertEqual(result['processStatus'],'EXITED_WITH_RESIDUAL_GROUP')
        self.assertIn('RESIDUAL_GROUP_KILLED',(self.root/'process.jsonl').read_text())


if __name__=='__main__':
    unittest.main(verbosity=2)
