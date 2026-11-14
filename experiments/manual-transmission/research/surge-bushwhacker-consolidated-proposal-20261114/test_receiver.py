"""Synthetic fail-closed receiving controls, never original run evidence."""
from pathlib import Path
import tempfile,unittest,json,hashlib,zipfile
from receive import envelope_check,unpack
from audit_reports import check_rows
class ReceiverControls(unittest.TestCase):
    def setUp(self):
        self.meta={k:k for k in ('event','source','source_tree','validation','validation_tree','workflow_blob','workflow_sha256','candidate_pins_blob','run_id','job_id','artifact_id','artifact_name')}
        self.meta.update(repository='GodaPupa/argentum-batshit-test',branch='manual-transmission/quarantine-recovery-20261106',epoch='manual-quarantine-20261106-01',candidate_index='01',protocol_blob='a2c26c3b31834a7182bbe0b3a6ceb83922443cd4',artifact_digest='sha256:x',artifact_size=1,attempt=1,run_number=1,status='completed',job_status='completed',already_received=False,fresh_reconciliation=True,sole_writer=True,automation_disabled=True)
    def test_valid_envelope(self):envelope_check(self.meta,self.meta.copy())
    def test_wrong_source(self):self.bad('source','wrong')
    def test_wrong_tree(self):self.bad('source_tree','wrong')
    def test_wrong_workflow(self):self.bad('workflow_blob','wrong')
    def test_wrong_protocol(self):self.bad('protocol_blob','wrong')
    def test_wrong_epoch(self):self.bad('epoch','wrong')
    def test_wrong_branch(self):self.bad('branch','wrong')
    def test_wrong_attempt(self):self.bad('attempt',2)
    def test_stale_candidate(self):self.bad('candidate_index','02')
    def test_already_received(self):self.bad('already_received',True)
    def bad(self,k,v):
        actual=self.meta|{k:v}
        with self.assertRaises((ValueError,KeyError)):envelope_check(actual,self.meta)
    def test_wrong_artifact_digest(self):
        with tempfile.TemporaryDirectory() as t:
            p=Path(t)/'a.zip';p.write_bytes(b'not original')
            with self.assertRaises(ValueError):unpack(p,Path(t)/'out',self.meta|{'artifact_size':p.stat().st_size})
    def xml(self,body,expected=None,skips=None,negative=None):
        with tempfile.TemporaryDirectory() as t:
            p=Path(t)/'rules-engine/build/test-results/test/TEST-Synthetic.xml';p.parent.mkdir(parents=True)
            p.write_text(f'<testsuite tests="{body.count("<testcase")}" failures="{body.count("<failure")}" errors="{body.count("<error")}" skipped="{body.count("<skipped")}">'+body+'</testsuite>')
            return check_rows(Path(t),expected or {'rules-engine\tSynthetic\twitness':1},skips,negative)
    def test_missing_raw_xml(self):
        with tempfile.TemporaryDirectory() as t:
            with self.assertRaises(ValueError):check_rows(Path(t),{'a':1})
    def test_duplicate_identity(self):
        row='<testcase classname="Synthetic" name="witness"/>'
        with self.assertRaises(ValueError):self.xml(row+row)
    def test_changed_historical_skip(self):
        with self.assertRaises(ValueError):self.xml('<testcase classname="Synthetic" name="witness"><skipped/></testcase>')
    def test_compile_error_is_not_witness(self):
        with self.assertRaises(ValueError):self.xml('<testcase classname="Synthetic" name="witness"><error type="CompileError"/></testcase>')
    def test_wrong_expected_actual(self):
        negative=dict(witness='rules-engine\tSynthetic\twitness',expected_exception='org.opentest4j.AssertionFailedError',source_file='Synthetic.kt',source_line=5,normal_expected=True,mutant_expected=False)
        row='<testcase classname="Synthetic" name="witness"><failure type="org.opentest4j.AssertionFailedError" message="expected true but was true">Synthetic.kt:5</failure></testcase>'
        with self.assertRaises(ValueError):self.xml(row,negative=negative)
    def test_unexpected_failure(self):
        with self.assertRaises(ValueError):self.xml('<testcase classname="Synthetic" name="witness"><failure/></testcase>')
    def test_missing_restoration(self):
        with tempfile.TemporaryDirectory() as t:
            with self.assertRaises(FileNotFoundError):json.loads((Path(t)/'RESTORATION.json').read_text())
if __name__=='__main__':unittest.main()
