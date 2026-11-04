import contextlib
from datetime import datetime, timezone, timedelta
import hashlib
import json
import os
from pathlib import Path
import tempfile
import types
import unittest
from unittest.mock import Mock, patch

import deployment_lock
import jar_retention as retention

POSIX = os.name == 'posix'


class RetentionTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.releases = self.root / 'service' / 'releases'
        self.releases.mkdir(parents=True)
        self.state = self.root / 'state'
        self.state.mkdir()
        (self.state / 'deployment.lock').touch()
        self.live = Mock()
        self.layout = retention.Layout(self.releases, self.live, self.state)
        self.current = None
        self.sha = None
        self.service = {'MainPID': '1234', 'ActiveState': 'active', 'SubState': 'running', 'NRestarts': '0'}
        trust = patch.object(retention, 'trusted', side_effect=lambda path: path)
        trust.start()
        self.addCleanup(trust.stop)

    def version(self, number, state=True):
        name = '%040x' % number
        path = self.releases / (name + '.jar')
        path.write_bytes(('fixture-%d' % number).encode())
        os.utime(path, ns=(number * 10**9, number * 10**9))
        if state:
            self.write_state(name, {'releaseId': name, 'stage': 'ACTIVATED'})
        return path

    def write_state(self, name, value):
        path = self.state / 'releases' / name / 'state.json'
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(value), encoding='utf-8')

    def versions(self, count):
        for number in range(1, count + 1):
            self.version(number)
        self.current = '%040x' % count
        path = self.releases / (self.current + '.jar')
        self.sha = hashlib.sha256(path.read_bytes()).hexdigest()
        self.live.resolve.return_value = path
        (self.state / 'current.json').write_text(json.dumps({'releaseId': self.current, 'jarSha256': self.sha, 'stage': 'ACTIVATED'}), encoding='utf-8')

    def known(self, numbers):
        now = datetime(2026, 11, 3, tzinfo=timezone.utc)
        return {'version': 1, 'releases': [dict(releaseId='%040x' % number, jarSha256='a' * 64,
                                              confirmedAt=(now + timedelta(seconds=index)).isoformat())
                                           for index, number in enumerate(numbers)]}

    def select(self, healthy=None, refs=()):
        return retention.selection(self.layout, retention.inventory(self.layout),
                                   retention.release_state(self.layout, self.current, self.sha),
                                   healthy or {'releases': []}, refs)

    def operational_stubs(self):
        stack = contextlib.ExitStack()
        self.addCleanup(stack.close)
        stack.enter_context(patch.object(retention, 'stable_startup', return_value=self.service))
        stack.enter_context(patch.object(retention, 'serving_jar'))
        stack.enter_context(patch.object(retention, 'backend_identity', return_value=self.service))
        stack.enter_context(patch.object(retention, 'backend_health'))
        stack.enter_context(patch.object(retention, 'system_references', return_value=set()))
        stack.enter_context(patch.object(retention, 'reject_legacy_deployment'))
        stack.enter_context(patch.object(retention, 'trusted', side_effect=lambda path: path))
        return stack

    def test_current_plus_fifteen_bootstrap_rollback_versions(self):
        self.versions(30)
        result = self.select()
        self.assertEqual(len(result['preserved']), 16)
        self.assertEqual(result['candidates'], ['%040x.jar' % number for number in range(1, 15)])
        self.assertEqual(result['bootstrap_rollback_versions'], 15)
        self.assertEqual(result['verified_rollback_versions'], 0)

    def test_boundary_and_fewer_versions_do_not_delete(self):
        for count in [1, 15, 16]:
            with self.subTest(count=count):
                # Each subcase builds an independent inventory.
                for path in self.releases.iterdir():
                    path.unlink()
                self.versions(count)
                self.assertEqual(self.select()['candidates'], [])

    def test_verified_healthy_versions_win_over_newer_unverified_files(self):
        self.versions(30)
        result = self.select(self.known(range(1, 16)))
        self.assertEqual(result['verified_rollback_versions'], 15)
        self.assertEqual(result['bootstrap_rollback_versions'], 0)
        self.assertEqual(result['preserved'], ['%040x.jar' % number for number in [*range(1, 16), 30]])

    def test_only_missing_healthy_slots_use_bootstrap(self):
        self.versions(30)
        result = self.select(self.known([1, 2, 3]))
        self.assertEqual(len(result['preserved']), 16)
        self.assertEqual(result['verified_rollback_versions'], 3)
        self.assertEqual(result['bootstrap_rollback_versions'], 12)

    def test_test_service_special_names_and_direct_rollback_are_protected(self):
        self.versions(30)
        special = self.releases / 'baseline.jar'
        special.write_bytes(b'special recovery')
        current = json.loads((self.state / 'current.json').read_text())
        current['previousJar'] = str(self.releases / ('%040x.jar' % 1))
        (self.state / 'current.json').write_text(json.dumps(current))
        result = self.select(refs=[str(self.releases / ('%040x.jar' % 2))])
        for name in ['baseline.jar', '%040x.jar' % 1, '%040x.jar' % 2]:
            self.assertIn(name, result['preserved'])

    def test_selected_rollback_reference_is_kept_without_retaining_whole_ancient_chain(self):
        self.versions(30)
        self.write_state('%040x' % 15, {'releaseId': '%040x' % 15, 'previousJar': str(self.releases / ('%040x.jar' % 14))})
        self.write_state('%040x' % 14, {'releaseId': '%040x' % 14, 'previousJar': str(self.releases / ('%040x.jar' % 13))})
        result = self.select()
        self.assertIn('%040x.jar' % 14, result['preserved'])
        self.assertIn('%040x.jar' % 13, result['candidates'])

    def test_orphan_without_receipt_is_preserved_for_manual_review(self):
        self.versions(30)
        orphan = self.version(99, state=False)
        os.utime(orphan, ns=(0, 0))
        self.assertIn(orphan.name, self.select()['preserved'])

    def test_previously_removed_archived_predecessor_is_reported_but_current_rollback_is_strict(self):
        self.versions(30)
        missing = self.releases / ('%040x.jar' % 99)
        self.write_state('%040x' % 15, {'releaseId': '%040x' % 15, 'previousJar': str(missing)})
        self.assertEqual(self.select()['missing_archived_predecessors'], [missing.name])
        current = json.loads((self.state / 'current.json').read_text())
        current['previousJar'] = str(missing)
        (self.state / 'current.json').write_text(json.dumps(current))
        with self.assertRaises(FileNotFoundError):
            self.select()

    def test_external_missing_and_relative_rollback_paths_fail_closed(self):
        self.versions(30)
        outside = self.root / 'outside.jar'
        outside.write_bytes(b'never delete')
        for raw in [str(outside), str(self.releases / 'missing.jar'), '../outside.jar']:
            with self.subTest(raw=raw), self.assertRaises((RuntimeError, FileNotFoundError)):
                self.select(refs=[raw])
        self.assertEqual(len(list(self.releases.glob('*.jar'))), 30)

    def test_invalid_or_duplicate_healthy_ledger_fails_before_any_delete(self):
        self.versions(30)
        bad_cases = [{'releases': 'bad'}, self.known([1, 1]), {'releases': [{'releaseId': '../bad'}]},
                     {'releases': [dict(releaseId='a'*40, jarSha256='a'*64, confirmedAt='not-a-time')]},
                     {'releases': [dict(releaseId='a'*40, jarSha256='a'*64, confirmedAt='2026-11-03T12:00:00')]}]
        for value in bad_cases:
            with self.subTest(value=value), self.assertRaises((RuntimeError, ValueError)):
                self.select(value)

    def test_current_identity_hash_stage_and_link_are_required(self):
        self.versions(30)
        with self.assertRaisesRegex(RuntimeError, 'EXPECTATION_CHANGED'):
            retention.release_state(self.layout, 'a'*40, self.sha)
        with self.assertRaisesRegex(RuntimeError, 'EXPECTATION_INVALID'):
            retention.release_state(self.layout, '../bad', self.sha)
        self.live.resolve.return_value = self.releases / ('%040x.jar' % 1)
        with self.assertRaisesRegex(RuntimeError, 'CURRENT_RELEASE_DRIFT'):
            retention.release_state(self.layout, self.current, self.sha)
        self.live.resolve.return_value = self.releases / (self.current + '.jar')
        self.live.resolve.return_value.write_bytes(b'tampered')
        with self.assertRaisesRegex(RuntimeError, 'CURRENT_RELEASE_DRIFT'):
            retention.release_state(self.layout, self.current, self.sha)
        state = json.loads((self.state / 'current.json').read_text())
        state['stage'] = 'ABORTED'
        (self.state / 'current.json').write_text(json.dumps(state))
        with self.assertRaisesRegex(RuntimeError, 'NOT_ACTIVATED'):
            retention.release_state(self.layout, self.current, self.sha)

    @unittest.skipUnless(POSIX, 'Real symlink coverage runs on Linux CI')
    def test_symlink_jar_or_state_is_rejected(self):
        self.versions(30)
        link = self.releases / ('%040x.jar' % 100)
        link.symlink_to(self.releases / ('%040x.jar' % 1))
        with self.assertRaisesRegex(RuntimeError, 'RELEASE_JAR_NOT_REGULAR'):
            retention.inventory(self.layout)
        link.unlink()
        state = self.state / 'current.json'
        original = self.state / 'original.json'
        state.rename(original)
        state.symlink_to(original)
        with self.assertRaisesRegex(RuntimeError, 'RETENTION_STATE_UNTRUSTED'):
            retention.release_state(self.layout, self.current, self.sha)

    def test_dry_run_makes_no_ledger_or_receipt_or_deletion(self):
        self.versions(30)
        self.operational_stubs()
        result = retention.execute(self.layout, self.current, self.sha)
        self.assertEqual(len(result['candidates']), 14)
        self.assertFalse(self.layout.healthy.parent.exists())
        self.assertEqual(len(list(self.releases.glob('*.jar'))), 30)

    def test_unhealthy_or_wrong_running_jar_never_prunes(self):
        self.versions(30)
        self.operational_stubs()
        for name in ['stable_startup', 'serving_jar']:
            with self.subTest(name=name), patch.object(retention, name, side_effect=RuntimeError('NOT_READY')):
                with self.assertRaisesRegex(RuntimeError, 'NOT_READY'):
                    retention.execute(self.layout, self.current, self.sha, apply=True)
                self.assertFalse(self.layout.healthy.exists())
                self.assertEqual(len(list(self.releases.glob('*.jar'))), 30)

    @unittest.skipUnless(POSIX, 'Actual directory-fd unlink runs on Linux CI')
    def test_apply_deletes_only_candidates_and_records_verified_current(self):
        self.versions(30)
        self.operational_stubs()
        protected_bytes = {path.name: path.read_bytes() for path in self.releases.iterdir() if int(path.stem, 16) >= 15}
        result = retention.execute(self.layout, self.current, self.sha, apply=True)
        self.assertEqual(result['status'], 'COMPLETE')
        self.assertEqual(len(result['deleted']), 14)
        self.assertEqual(result['remaining_jar_count'], 16)
        self.assertEqual({path.name: path.read_bytes() for path in self.releases.iterdir()}, protected_bytes)
        ledger = retention.document(self.layout.healthy)
        self.assertEqual(ledger['releases'][0]['releaseId'], self.current)
        self.assertEqual(ledger['releases'][0]['jarSha256'], self.sha)
        self.assertEqual(len(list(self.layout.healthy.parent.glob('cleanup-*.json'))), 1)
        self.assertEqual(retention.execute(self.layout, self.current, self.sha, apply=True)['deleted'], [])

    @unittest.skipUnless(POSIX, 'Actual directory-fd unlink runs on Linux CI')
    def test_changed_reference_state_or_candidate_stops_before_first_unlink(self):
        for change in ['reference', 'state', 'file']:
            with self.subTest(change=change):
                self.versions(30)
                self.operational_stubs()
                if change == 'reference':
                    patcher = patch.object(retention, 'system_references', side_effect=[set(), {str(self.releases / ('%040x.jar' % 1))}])
                elif change == 'state':
                    patcher = patch.object(retention, 'state_fingerprint', side_effect=[[], ['changed']])
                else:
                    original = retention.record
                    def change_file(path, value):
                        original(path, value)
                        if path.name.startswith('cleanup-') and value['status'] == 'PLANNED':
                            (self.releases / ('%040x.jar' % 1)).write_bytes(b'changed')
                    patcher = patch.object(retention, 'record', side_effect=change_file)
                with patcher, self.assertRaises(RuntimeError):
                    retention.execute(self.layout, self.current, self.sha, apply=True)
                self.assertEqual(len(list(self.releases.glob('*.jar'))), 30)

    @unittest.skipUnless(POSIX, 'Actual directory-fd unlink runs on Linux CI')
    def test_unlink_failure_leaves_service_untouched_and_a_pending_receipt(self):
        self.versions(30)
        self.operational_stubs()
        with patch.object(retention.os, 'unlink', side_effect=PermissionError('private path')):
            with self.assertRaises(PermissionError):
                retention.execute(self.layout, self.current, self.sha, apply=True)
        receipt = retention.document(next(self.layout.healthy.parent.glob('cleanup-*.json')))
        self.assertEqual(receipt['status'], 'STOPPED')
        self.assertEqual(receipt['pending_delete'], '%040x.jar' % 1)
        self.assertEqual(receipt['deleted'], [])
        self.assertEqual(len(list(self.releases.glob('*.jar'))), 30)

    def test_stable_startup_requires_three_consecutive_checks_and_times_out(self):
        with patch.object(retention, 'backend_identity', return_value=self.service), patch.object(retention, 'backend_health', side_effect=[RuntimeError(), None, None, None]), patch.object(retention.time, 'sleep'):
            self.assertEqual(retention.stable_startup(), self.service)
        with patch.object(retention, 'backend_identity', side_effect=RuntimeError()), patch.object(retention.time, 'monotonic', side_effect=[0, 61]):
            with self.assertRaisesRegex(RuntimeError, 'HEALTHY_STARTUP_NOT_CONFIRMED'):
                retention.stable_startup()

    def test_legacy_deployment_and_other_recent_upload_stop_cleanup(self):
        fake_proc = self.root / 'proc'
        uploads = self.root / 'uploads'
        fake_proc.mkdir()
        uploads.mkdir()
        original_path = Path
        def path(value, *parts):
            if str(value) == '/proc':
                return fake_proc.joinpath(*parts)
            if str(value) == '/var/tmp':
                return uploads.joinpath(*parts)
            return original_path(value, *parts)
        with patch.object(retention, 'Path', side_effect=path):
            retention.reject_legacy_deployment('a'*40)
            current_upload = uploads / ('alpha-' + 'a'*40 + '.jar')
            current_upload.write_bytes(b'allowed workflow upload')
            retention.reject_legacy_deployment('a'*40)
            other = uploads / ('alpha-' + 'b'*40 + '.jar')
            other.write_bytes(b'other workflow upload')
            with self.assertRaisesRegex(RuntimeError, 'OTHER_RELEASE_UPLOAD_IN_PROGRESS'):
                retention.reject_legacy_deployment('a'*40)
            other.unlink()
            proc = fake_proc / '99999999'
            proc.mkdir()
            (proc / 'cmdline').write_bytes(b'python3\0/var/tmp/alpha/deployment/release.py\0deploy\0')
            with self.assertRaisesRegex(RuntimeError, 'LEGACY_DEPLOYMENT_RUNNING'):
                retention.reject_legacy_deployment('a'*40)

    def test_cli_sanitizes_failure_and_never_exports_private_details(self):
        with patch.object(retention.os, 'geteuid', return_value=0, create=True), patch.object(retention, 'trusted', side_effect=OSError('private credential')), contextlib.redirect_stdout(__import__('io').StringIO()) as output:
            self.assertEqual(retention.main(['--expected-release', 'a'*40, '--expected-jar-sha', 'b'*64]), 1)
        self.assertEqual(json.loads(output.getvalue())['error'], 'RETENTION_UNAVAILABLE')
        self.assertNotIn('private', output.getvalue())

    def test_cli_uses_existing_release_path_guard_and_requires_state_root_trust(self):
        verifier = Mock()
        fake_snapshot = types.SimpleNamespace(trusted_release_jar=verifier)
        with patch.dict('sys.modules', {'release_snapshot': fake_snapshot}), patch.object(retention.os, 'geteuid', return_value=0, create=True), patch.object(retention, 'trusted') as state_guard, patch.object(retention, 'exclusive', return_value=contextlib.nullcontext()), patch.object(retention, 'execute', return_value={'mode': 'DRY_RUN'}) as execute, contextlib.redirect_stdout(__import__('io').StringIO()):
            self.assertEqual(retention.main(['--expected-release', 'a'*40, '--expected-jar-sha', 'b'*64]), 0)
        state_guard.assert_called_once_with(retention.ROOT)
        verifier.assert_called_once_with(Path('/opt/alpha/releases'))
        self.assertFalse(execute.call_args.args[-1])


@unittest.skipUnless(POSIX, 'Actual flock requires Linux')
class SharedLockTest(unittest.TestCase):
    def test_real_lock_excludes_retention_and_release_mutation(self):
        import release
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            path = root / 'deployment.lock'
            path.touch(mode=0o600)
            actual_fstat = os.fstat
            def owner_stat(fd):
                value = actual_fstat(fd)
                return types.SimpleNamespace(st_mode=value.st_mode, st_uid=0)
            with patch.object(deployment_lock, 'trusted', side_effect=lambda p: p), patch.object(deployment_lock.os, 'fstat', side_effect=owner_stat):
                with deployment_lock.exclusive(path):
                    with self.assertRaises(BlockingIOError):
                        with deployment_lock.exclusive(path):
                            self.fail('second lock acquired')
                    with patch.object(release, 'ROOT', root), patch.object(release, 'run_locked') as mutation:
                        with self.assertRaises(BlockingIOError):
                            release.run(types.SimpleNamespace(phase='deploy'))
                        mutation.assert_not_called()
                with patch.object(release, 'ROOT', root), patch.object(release, 'run_locked') as mutation:
                    release.run(types.SimpleNamespace(phase='rollback'))
                    mutation.assert_called_once()


if __name__ == '__main__':
    unittest.main()
