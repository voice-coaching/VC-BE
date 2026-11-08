from contextlib import contextmanager
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import consolidate_runtime_env as op
from runtime_environment import EnvironmentError, serialize, dropin_bytes


@contextmanager
def drained(): yield


class RuntimeEnvActivation(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(); self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.env, self.override = self.root / '.env', self.root / '99-runtime-env.conf'
        self.backup = self.root / 'backup'; self.backup.mkdir(mode=0o700)
        self.candidate = self.backup / 'candidate.env'; self.candidate.write_bytes(serialize({'A': 'fixture-value'}))
        self.before = {'properties': {'User': 'root' if os.getuid() == 0 else __import__('pwd').getpwuid(os.getuid()).pw_name},
                       'values': {'A': 'fixture-value'}, 'files': {}, 'release': 'fixture',
                       'jar': str(Path('/opt/alpha/app.jar').resolve())}
        # Keep all mutation inside a disposable fixture. No fixture invokes a real service restart.
        patches = [patch.object(op, 'ENV_FILE', self.env), patch.object(op, 'DROPIN', self.override),
                   patch.object(op, 'check_snapshot', return_value=self.candidate), patch.object(op, 'healthy', return_value=True),
                   patch.object(op, 'unchanged'), patch.object(op, 'hold_and_drain', drained),
                   patch.object(op, 'read_private', side_effect=lambda p: Path(p).read_bytes()),
                   patch.object(op, 'dropin_bytes', side_effect=lambda: dropin_bytes(self.env)),
                   patch.object(op, 'properties', return_value={'EnvironmentFiles': str(self.env)+' (ignore_errors=no)', 'Environment': ''}),
                   patch.object(op, 'command', return_value=b''), patch.object(op, 'wait_healthy'),
                   patch.object(op, 'backend_env_equal', return_value=True)]
        self.mocks = []
        for item in patches:
            self.mocks.append(item.start()); self.addCleanup(item.stop)
        self.command, self.wait, self.equal = self.mocks[-3:]

    def test_success_publishes_one_env_and_preserves_verified_values(self):
        result = op.apply_snapshot(self.before, self.backup)
        self.assertEqual(result['stage'], 'APPLIED')
        self.assertTrue(result['allValuesPreserved'])
        self.assertEqual(self.env.read_bytes(), self.candidate.read_bytes())
        self.assertEqual(sum(c.args[0][:2] == ['systemctl', 'restart'] for c in self.command.call_args_list), 1)

    def test_existing_env_is_not_overwritten_and_service_is_not_restarted(self):
        self.env.write_bytes(b'preexisting')
        with self.assertRaisesRegex(EnvironmentError, '^ENV_TARGET_ALREADY_EXISTS$'):
            op.apply_snapshot(self.before, self.backup)
        self.assertEqual(self.env.read_bytes(), b'preexisting')
        self.command.assert_not_called()

    def test_failed_health_restores_legacy_inputs_and_removes_only_created_files(self):
        self.wait.side_effect = [EnvironmentError('ENV_BACKEND_HEALTH_FAILED'), None]
        with self.assertRaisesRegex(EnvironmentError, '^ENV_APPLY_FAILED_ROLLED_BACK$'):
            op.apply_snapshot(self.before, self.backup)
        self.assertFalse(self.override.exists()); self.assertFalse(self.env.exists())
        self.assertEqual(sum(c.args[0][:2] == ['systemctl', 'restart'] for c in self.command.call_args_list), 2)

    def test_failed_rollback_is_distinguished_from_successful_recovery(self):
        self.wait.side_effect = EnvironmentError('ENV_BACKEND_HEALTH_FAILED')
        with self.assertRaisesRegex(EnvironmentError, '^ENV_ROLLBACK_REQUIRES_OPERATOR$'):
            op.apply_snapshot(self.before, self.backup)

    def test_concurrent_source_change_aborts_before_activation(self):
        self.mocks[4].side_effect = EnvironmentError('ENV_SOURCE_CHANGED')
        with self.assertRaisesRegex(EnvironmentError, '^ENV_SOURCE_CHANGED$'):
            op.apply_snapshot(self.before, self.backup)
        self.assertFalse(self.env.exists()); self.assertFalse(self.override.exists())
        self.command.assert_not_called()

    def test_modified_override_is_not_silently_deleted_on_rollback(self):
        def failed_health():
            self.override.write_bytes(b'other-operator')
            raise EnvironmentError('ENV_BACKEND_HEALTH_FAILED')
        self.wait.side_effect = failed_health
        with self.assertRaisesRegex(EnvironmentError, '^ENV_ROLLBACK_REQUIRES_OPERATOR$'):
            op.apply_snapshot(self.before, self.backup)
        self.assertEqual(self.override.read_bytes(), b'other-operator')


if __name__ == '__main__': unittest.main()
