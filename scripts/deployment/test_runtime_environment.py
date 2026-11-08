import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import runtime_environment as runtime
import consolidate_runtime_env as operation


class RuntimeEnvironment(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)

    def test_round_trip_preserves_quotes_backslashes_unicode_newlines_and_empty_values(self):
        values = {'EMPTY': '', 'SPACES': ' space\t ', 'QUOTES': '\'"\\', 'LITERAL': '$HOME `id` # ; =',
                  'MULTILINE': 'first\nsecond\r\nlast', 'UNICODE': '\uac00\ub098\ub2e4', 'BACKSLASH_DOLLAR': '\\$X'}
        raw = runtime.serialize(values)
        self.assertEqual(runtime.parse(raw), values)
        self.assertNotIn(b'export ', raw)

    def test_invalid_names_and_nul_values_are_rejected_without_echoing_the_value(self):
        for values in [{'A B': 'fixture-private-value'}, {'A': 'fixture\0private-value'}]:
            with self.assertRaises(runtime.EnvironmentError) as error:
                runtime.serialize(values)
            self.assertNotIn('fixture', str(error.exception))

    def test_publish_is_private_and_never_clobbers_existing_files_or_links(self):
        target = self.root / '.env'
        runtime.new_file(target, b'original')
        self.assertEqual(target.stat().st_mode & 0o777, 0o600)
        with self.assertRaises(FileExistsError): runtime.new_file(target, b'new')
        self.assertEqual(target.read_bytes(), b'original')
        link = self.root / 'link'
        link.symlink_to(target)
        with self.assertRaises(FileExistsError): runtime.new_file(link, b'new')
        self.assertEqual(target.read_bytes(), b'original')
        self.assertEqual(list(self.root.glob('*.pending-*')), [])

    def test_private_read_rejects_public_permissions_and_symlinks(self):
        target = self.root / '.env'
        runtime.new_file(target, b'A="fixture"\n')
        self.assertEqual(runtime.read_private(target, os.getuid()), target.read_bytes())
        target.chmod(0o644)
        with self.assertRaises(runtime.EnvironmentError): runtime.read_private(target, os.getuid())
        target.chmod(0o600)
        link = self.root / 'link'
        link.symlink_to(target)
        with self.assertRaises(runtime.EnvironmentError): runtime.read_private(link, os.getuid())

    def test_publish_failure_after_link_removes_only_its_own_file(self):
        target = self.root / '.env'
        with patch.object(runtime.os, 'fsync', side_effect=[None, OSError('fixture')]):
            with self.assertRaises(OSError): runtime.new_file(target, b'fixture')
        self.assertFalse(target.exists())
        self.assertEqual(list(self.root.iterdir()), [])

    def test_override_resets_both_environment_sources_and_validates_exact_target(self):
        env, override = self.root / '.env', self.root / '99-runtime-env.conf'
        runtime.new_file(env, runtime.serialize({'A': 'fixture'}))
        runtime.new_file(override, runtime.dropin_bytes(env), 0o644)
        self.assertEqual(runtime.validate_managed(env, override, os.getuid()), {'A': 'fixture'})
        self.assertIn(b'Environment=\nEnvironmentFile=\n', override.read_bytes())
        override.write_bytes(b'[Service]\nEnvironmentFile=/unexpected\n')
        with self.assertRaises(runtime.EnvironmentError): runtime.validate_managed(env, override, os.getuid())

    def test_snapshot_change_aborts_before_any_runtime_write(self):
        with patch.object(operation, 'snapshot', return_value={'properties': {'MainPID': '2'}, 'jar': 'new', 'values': {}, 'files': {}}):
            with self.assertRaisesRegex(runtime.EnvironmentError, '^ENV_SOURCE_CHANGED$'):
                operation.unchanged({'properties': {'MainPID': '1'}, 'jar': 'old', 'values': {}, 'files': {}})

    def test_systemd_check_does_not_put_private_values_in_arguments(self):
        expected = {'A': 'fixture-private-value'}
        with patch.object(operation, 'command', return_value=b'{"equal":true,"count":1}') as command:
            operation.systemd_compare(self.root, [self.root / 'candidate.env'], expected)
        arguments = command.call_args.args[0]
        self.assertNotIn('fixture-private-value', repr(arguments))
        self.assertIn('PrivateNetwork=yes', arguments)
        self.assertIn('NoNewPrivileges=yes', arguments)
        for path in self.root.iterdir(): self.assertEqual(path.stat().st_mode & 0o777, 0o600)

    def test_hold_drains_and_releases_only_its_own_marker(self):
        hold = self.root / 'hold'
        with patch.object(operation, 'HOLD', hold), patch.object(operation, 'environment', return_value={}), \
                patch.object(operation, 'drain', side_effect=[{'analyses': 1}, {'analyses': 0}]), patch.object(operation.time, 'sleep'):
            with operation.hold_and_drain(): self.assertTrue(hold.exists())
        self.assertFalse(hold.exists())

    def test_preexisting_hold_is_not_removed(self):
        hold = self.root / 'hold'; hold.write_bytes(b'other-operator')
        with patch.object(operation, 'HOLD', hold):
            with self.assertRaisesRegex(runtime.EnvironmentError, '^ENV_ADMISSION_ALREADY_HELD$'):
                with operation.hold_and_drain(): self.fail('must not enter')
        self.assertEqual(hold.read_bytes(), b'other-operator')

    def test_hold_preserves_the_gate_when_rollback_requires_operator_recovery(self):
        hold = self.root / 'hold'
        with patch.object(operation, 'HOLD', hold), patch.object(operation, 'environment', return_value={}), \
                patch.object(operation, 'drain', return_value={'analyses': 0}):
            with self.assertRaises(runtime.EnvironmentError):
                with operation.hold_and_drain(): raise runtime.EnvironmentError('ENV_ROLLBACK_REQUIRES_OPERATOR')
        self.assertTrue(hold.exists())

    @unittest.skipUnless(os.geteuid() == 0 and Path('/run/systemd/system').exists(), 'requires root and a running systemd')
    def test_real_systemd_parser_preserves_all_synthetic_values(self):
        values = {'ENV_FIXTURE_A': 'quote" slash\\ money$ backtick` #;', 'ENV_FIXTURE_B': 'first\nsecond\r\nlast',
                  'ENV_FIXTURE_C': '', 'ENV_FIXTURE_D': ' \t spaced ', 'ENV_FIXTURE_E': '\uac00\ub098'}
        candidate = self.root / 'candidate.env'
        runtime.new_file(candidate, runtime.serialize(values))
        operation.systemd_compare(self.root, [candidate], values)


if __name__ == '__main__': unittest.main()
