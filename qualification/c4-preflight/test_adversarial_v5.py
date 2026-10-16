"""Offline only. Never execute ELF, V4 supervisor or privileged syscalls."""
import copy
import json
from pathlib import Path
import unittest
from unittest.mock import patch
import adversarial_probe as a

class V5Tests(unittest.TestCase):
    def receipt(self):
        r={'elf_sha256':a.ELF_SHA256,'helper_sha256':a.HELPER_SHA256,'source_commit':'a'*40,
           'run_id':'synthetic-only','counter_id':[1,2],'boot':'boot',
           'parent':dict(path=a.PROPOSED_PARENT,exact_parent_approved=True,exclusive_delegation=True,
                         root_protected=True,memory_already_enabled=True,supervisor_outside=True,
                         held_identity_verified=True,upstream_mutations=[],systemd_managed=False),
           'events':['creation','preexec','exec','reparent','persistent','kill','drained','final','logs','remove','manifest'],
           'helper_rows':[dict(case=n,ret=-1,errno=1) for n in a.DENIALS]+[dict(case=n,ret=0,errno=0) for n in a.OBSERVATIONS]}
        for key in ('pid_start_verified preexec_membership postexec_membership descendant_membership reparent_verified privileges_sealed namespaces_verified scratch_mounts_verified fd_closure_verified pidfd_bound group_fd_bound counter_fd_bound root_fd_bound image_fd_bound persistent_before_kill kill_written populated_zero same_fd_final counter_before_remove removed logs_complete manifest_complete no_external_ipc_observed').split():r[key]=True
        r['samples']=[dict(counter_id=[1,2],boot='boot',scope='v2-hierarchical',run_id=r['run_id'],seq=i,oom=0,oom_kill=0,phase=phase) for i,phase in enumerate(['creation','post_drain'])]
        return r
    def status(self,r):return a.evaluate_synthetic_receipt(r)['status']
    def test_complete_synthetic_is_not_qualification(self):
        out=a.evaluate_synthetic_receipt(self.receipt());self.assertEqual(out['status'],'NEGATIVE_CONTROL_OBSERVED');self.assertFalse(out['qualifying']);self.assertFalse(out['compiler_enabled'])
    def test_each_lifecycle_gate_required(self):
        for key,value in self.receipt().items():
            if value is True:
                r=self.receipt();r[key]=False
                with self.subTest(key=key):self.assertEqual(self.status(r),'INCOMPLETE_RUNTIME')
    def test_safe_parent_all_conditions_required(self):
        for key in self.receipt()['parent']:
            r=self.receipt();del r['parent'][key]
            with self.subTest(key=key):self.assertEqual(self.status(r),'BLOCKED_HOST_PARENT')
    def test_systemd_no_fallback(self):
        r=self.receipt();r['parent']['systemd_managed']=True;self.assertEqual(self.status(r),'BLOCKED_HOST_PARENT')
        for path in ['/sys/fs/cgroup/system.slice','/sys/fs/cgroup/system.slice/hosted-compute-agent.service','/sys/fs/cgroup']:
            r=self.receipt();r['parent']['path']=path;self.assertEqual(self.status(r),'BLOCKED_HOST_PARENT')
    def test_wrong_image_and_source(self):
        for key in ('elf_sha256','helper_sha256','source_commit','run_id'):
            r=self.receipt();r[key]='wrong';self.assertEqual(self.status(r),'INCOMPLETE_RUNTIME') if key!='run_id' else self.assertNotEqual(self.status(r),'NEGATIVE_CONTROL_OBSERVED')
    def test_missing_interval(self):
        r=self.receipt();r['samples'].insert(1,None);self.assertEqual(self.status(r),'INCOMPLETE_RUNTIME')
    def test_positive_survives_missing_and_failed_cleanup(self):
        r=self.receipt();r['samples'].insert(1,None);r['samples'][-1]['oom']=1;r['removed']=False;self.assertEqual(self.status(r),'FAIL')
    def test_positive_before_reset(self):
        r=self.receipt();r['samples'][0]['oom_kill']=2;self.assertEqual(self.status(r),'FAIL')
    def test_unknown_counter_identity_cannot_supply_zero(self):
        for key,v in [('counter_id',[1,3]),('boot','other'),('scope','v1-kills-only'),('oom','0'),('oom',-1),('seq',False)]:
            r=self.receipt();r['samples'][-1][key]=v;self.assertEqual(self.status(r),'INCOMPLETE_RUNTIME')
    def test_missing_final_and_phase(self):
        r=self.receipt();r['samples'].pop();self.assertEqual(self.status(r),'INCOMPLETE_RUNTIME')
        r=self.receipt();r['samples'][-1]['phase']='pre_kill';self.assertEqual(self.status(r),'INCOMPLETE_RUNTIME')
    def test_ordering(self):
        r=self.receipt();r['events'][5:7]=['drained','kill'];self.assertEqual(self.status(r),'INCOMPLETE_RUNTIME')
    def test_each_denial_missing_or_ambiguous(self):
        for name in a.DENIALS:
            r=self.receipt();row=next(x for x in r['helper_rows'] if x['case']==name);row['errno']=2;self.assertEqual(self.status(r),'INCOMPLETE_RUNTIME')
    def test_partial_and_duplicate_output(self):
        r=self.receipt();r['helper_rows'].pop();self.assertEqual(self.status(r),'INCOMPLETE_RUNTIME')
        r=self.receipt();r['helper_rows'][-1]=r['helper_rows'][0];self.assertEqual(self.status(r),'INCOMPLETE_RUNTIME')
    def test_positive_escape_and_fatal(self):
        for key in ['demonstrated_escape','source_bound_fatal']:
            r=self.receipt();r[key]=True;r['logs_complete']=False;self.assertEqual(self.status(r),'FAIL')
    def test_entry_never_dispatches(self):
        with patch('kernel_supervisor.supervise_probe',side_effect=AssertionError('no dispatch')):
            with self.assertRaisesRegex(a.Refusal,'BLOCKED_HOST_PARENT'):a.supervise_fixed_adversarial_v5(Path('/unused/evidence'))
    def test_evidence_path_required(self):
        for value in [None,'/tmp/evidence',Path('relative'),Path('/a/../b')]:
            with self.assertRaises(a.Refusal):a.supervise_fixed_adversarial_v5(value)
    def test_fixed_image_and_build_identity(self):
        actual=a.fixed_image_identity();manifest=json.loads((Path(a.__file__).parent/'helper-build.json').read_text());self.assertEqual(actual['elf_sha256'],manifest['elf_sha256']);self.assertEqual(actual['helper_sha256'],manifest['source_sha256']);self.assertTrue(manifest['rebuild_identical'])
    def test_image_tamper(self):
        original=Path.read_bytes
        def fake(p):return b'bad' if p.name.endswith('.elf') else original(p)
        with patch.object(Path,'read_bytes',fake):
            with self.assertRaisesRegex(a.Refusal,'IMAGE_NOT_FROZEN'):a.fixed_image_identity()
    def test_matrix_all_twelve_preserved(self):
        data=json.loads((Path(a.__file__).parent/'V5_NEGATIVE_CASES.json').read_text());self.assertEqual(len(data['cases']),12)

    def test_successful_forbidden_syscall_is_failure(self):
        for name in a.DENIALS:
            r=self.receipt();next(x for x in r['helper_rows'] if x['case']==name)['ret']=0
            r['logs_complete']=False;self.assertEqual(self.status(r),'FAIL')
