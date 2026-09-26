"""Adversarial checks against empty or misdirected Pest qualification output."""
import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

MODULE = Path(__file__).resolve().parents[1] / 'audit-pest-postboard-support-closure.py'
SPEC = importlib.util.spec_from_file_location('pest_support_closure', MODULE)
audit = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(audit)


class PestSupportClosureAuditTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.paths = {}
        for name, (era, count) in audit.SCENARIOS.items():
            self.write_xml(name, f'mtg-sets/{era}/tests/build/test-results/test', count,
                           'com.wingedsheep.engine.scenarios')
        self.write_xml('PestControlTierOnePostboardSupportAuditTest',
                       'gym/build/test-results/test', 1, 'com.wingedsheep.gym')
        self.inventory = self.root / 'inventory.txt'
        self.inventory.write_text(''.join(deck + '=\n' for deck in audit.DECKS))

    def write_xml(self, name, directory, count, package):
        path = self.root / directory / f'TEST-{package}.{name}.xml'
        path.parent.mkdir(parents=True, exist_ok=True)
        cases = ''.join(f'<testcase name="case {i}" classname="{package}.{name}"/>'
                        for i in range(count))
        path.write_text(f'<testsuite tests="{count}" failures="0" errors="0" skipped="0">'
                        f'{cases}</testsuite>')
        self.paths[name] = path

    def result(self):
        with patch.object(audit.subprocess, 'check_output', side_effect=lambda command, **kwargs: '' if 'diff' in command else 'fixture-source\n'):
            return audit.inspect(self.root, self.inventory, False)

    def test_real_exact_classes_and_empty_inventory_pass(self):
        self.assertEqual('PASS', self.result()['status'])

    def test_no_source_success_without_xml_fails(self):
        self.paths['FaerieMacabreScenarioTest'].unlink()
        self.assertEqual('FAIL_CLOSED', self.result()['status'])

    def test_zero_cases_fails(self):
        self.paths['KaerveksTorchScenarioTest'].write_text('<testsuite tests="0"/>')
        self.assertEqual('FAIL_CLOSED', self.result()['status'])

    def test_skipped_case_fails_even_if_suite_counts_claim_success(self):
        p = self.paths['RelicOfProgenitusScenarioTest']
        p.write_text(p.read_text().replace('/>', '><skipped/></testcase>', 1))
        self.assertEqual('FAIL_CLOSED', self.result()['status'])

    def test_wrong_class_cannot_supply_required_cases(self):
        p = self.paths['SpreadingSeasScenarioTest']
        p.write_text(p.read_text().replace('SpreadingSeasScenarioTest', 'DifferentScenarioTest'))
        self.assertEqual('FAIL_CLOSED', self.result()['status'])

    def test_unresolved_inventory_blocks_closure(self):
        self.inventory.write_text(self.inventory.read_text().replace('spy_combo=', 'spy_combo=Faerie Macabre:2'))
        self.assertEqual('FAIL_CLOSED', self.result()['status'])


if __name__ == '__main__':
    unittest.main()
