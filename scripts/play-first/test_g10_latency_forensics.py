#!/usr/bin/env python3
"""Synthetic/no-engine tests for read-only G10 forensic parser and refusal paths."""
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

MODULE = Path(__file__).with_name('g10_latency_forensics.py')
spec = importlib.util.spec_from_file_location('g10_latency_forensics', MODULE)
g = importlib.util.module_from_spec(spec)
spec.loader.exec_module(g)


def call(span, typ, *, phase='CHOOSE_ACTION', elapsed=7, context=None):
    if context is None: context = {'turn':'20', 'seat':'1', 'nextSequence':'491'}
    d = dict(recordType=typ,span=span,phase=phase,context=context,wallTimeUtc='2026-10-14T20:00:00Z',monotonicNanos=100+elapsed if typ!='CALL_START' else 100)
    if typ!='CALL_START': d['elapsedNanos']=elapsed
    return d


def synthetic_zip(*, corrupt_nested=False, add_extra=False):
    inner_data={'case.json':b'case\n','checkout-status.txt':b'', 'hold.json':b'hold\n', 'preflight-verification.json':b'{}\n', 'python-version.txt':b'Python 3.13\n', 'source-identities.txt':b'abc\n', 'verifier.log':b'no engine\n'}
    outer_data={'child.log':b'child\n','original.jsonl':b'{}\n','timing.jsonl':b'{}\n','final-verifier.log':b'final\n'}
    outer_data.update({'preflight-received/'+k:v for k,v in inner_data.items()})
    outer_data['preflight-received/final-verification.json']=b'{}\n'
    outer_data['preflight-received/gate-verification.json']=b'{}\n'
    outer_data['test-results/binary/output-events.bin']=b''
    outer_data['test-results/binary/in-progress-results-generic.bin']=b''
    def inv(data): return {'payloads':[dict(path=k, bytes=len(v),sha256=g.digest(v)) for k,v in sorted(data.items())], 'gameplayAuthorized':False, 'stage':'ORIGINAL_EVIDENCE_INVENTORY_ONLY'}
    nested=inv(inner_data)
    if corrupt_nested: nested['payloads'][0]['sha256']='0'*64
    nested_name='preflight-received/manifest-'+'f'*64+'.json'
    archive_data = dict(outer_data)
    archive_data[nested_name]=(json.dumps(nested)+'\n').encode()
    archive_data['manifest-'+'e'*64+'.json']=(json.dumps(inv(outer_data))+'\n').encode()
    if add_extra: archive_data['evil-extra.txt']=b'not in manifest'
    output=io.BytesIO()
    with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED) as z:
        for k,v in archive_data.items(): z.writestr(k,v)
    return output.getvalue()


class TestForensics(unittest.TestCase):
    def test_actual_original_archive_and_receipt(self):
        path = Path('/mnt/data/github-actions-artifact-11931191541.zip')
        if not path.exists(): self.skipTest('No original ZIP mounted; synthetic tests still run')
        receipt=g.analyze_archive(path)
        self.assertEqual(receipt['game']['accepted_action_count'],688)
        self.assertEqual(receipt['integrity']['payloads_verified'],15)
        self.assertEqual(receipt['integrity']['nested_payloads_verified'],7)
        self.assertEqual(receipt['outer_timing']['unfinished_span']['context']['nextSequence'],'689')

    def test_synthetic_zip_manifests_and_crc(self):
        with zipfile.ZipFile(io.BytesIO(synthetic_zip())) as z:
            r=g.verify_zip_members(z)
        self.assertEqual(r['nested_payloads_verified'],7)
        self.assertEqual(r['payloads_verified'],15)

    def test_nested_digest_tampering_refuses(self):
        with zipfile.ZipFile(io.BytesIO(synthetic_zip(corrupt_nested=True))) as z:
            with self.assertRaisesRegex(g.EvidenceError,'nested payload mismatch'):
                g.verify_zip_members(z)

    def test_unmanifested_member_refuses(self):
        with zipfile.ZipFile(io.BytesIO(synthetic_zip(add_extra=True))) as z:
            with self.assertRaisesRegex(g.EvidenceError,'unmanifested'):
                g.verify_zip_members(z)

    def test_span_pairing_detects_incomplete_call(self):
        rows=[call(1,'CALL_START'),call(1,'CALL_END',elapsed=7000000),call(2,'CALL_START',context={'turn':'26','nextSequence':'689','seat':'1'})]
        good,pending=g.pair_spans(rows)
        self.assertEqual(len(good),1)
        self.assertEqual(good[0]['seconds'],0.007)
        self.assertEqual(len(pending),1)
        self.assertEqual(pending[0]['context']['nextSequence'],'689')

    def test_duplicate_completion_refuses(self):
        rows=[call(1,'CALL_START'),call(1,'CALL_END'),call(1,'CALL_END')]
        with self.assertRaisesRegex(g.EvidenceError,'orphan/duplicate completion'):
            g.pair_spans(rows)

    def test_cross_context_completion_refuses(self):
        rows=[call(1,'CALL_START'),call(1,'CALL_END',context={'turn':'25'})]
        with self.assertRaisesRegex(g.EvidenceError,'identity mismatch'):
            g.pair_spans(rows)

    def test_missing_action_result_refuses(self):
        initial={'recordType':'EXHIBITION_INTENT','gameId':'G10','sourceCommit':g.SOURCE_SHA,'caseInputSha256':g.CASE_SHA256,'scope':'NONOFFICIAL_PLAY_FIRST_EXHIBITION_ONLY'}
        intents=[{'recordType':'ACTION_INTENT','sequence':i} for i in range(1,689)]
        results=[{'recordType':'ACTION_RESULT','sequence':i,'accepted':True,'turnAfter':26,'gameOverAfter':False} for i in range(1,688)]
        with self.assertRaisesRegex(g.EvidenceError,'action result missing'):
            g.audit_actions([initial,{'recordType':'INITIALIZED'}]+intents+results)

    def test_original_full_zip_sha_refuses_wrong_sha(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'fake.zip';p.write_bytes(synthetic_zip())
            with self.assertRaisesRegex(g.EvidenceError,'ZIP SHA-256 differs'):
                g.analyze_archive(p)

    def test_parse_truncated_jsonl_refuses(self):
        with self.assertRaisesRegex(g.EvidenceError,'missing final newline'):
            g.jsonl(b'{"recordType":"CALL_START"}', 'truncated')

if __name__=='__main__':unittest.main()
