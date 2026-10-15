"""Local controller gate tests; subprocess calls are forbidden."""
import importlib.util
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

class GateTests(unittest.TestCase):
    def test_unavailable_and_limited_scope_stop_before_fetch(self):
        for receipt in [dict(available=False, counters=None), dict(available=True, qualifying_scope=False)]:
            with self.subTest(receipt=receipt), tempfile.TemporaryDirectory() as tmp:
                with patch.dict(os.environ, RUNNER_TEMP=tmp, C4_REVISION='R5'):
                    spec=importlib.util.spec_from_file_location('preflight_under_test',Path(__file__).with_name('preflight.py'))
                    module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
                with patch.object(module,'sample',return_value=receipt), patch.object(module,'run',side_effect=AssertionError('subprocess prohibited')) as run:
                    with self.assertRaisesRegex(ValueError,'compile not started'): module.main()
                    run.assert_not_called()
                    self.assertTrue((Path(tmp)/'c4-evidence/memory-discovery.json').exists())
