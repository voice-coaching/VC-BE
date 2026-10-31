import argparse
import contextlib
import hashlib
import io
import json
import os
from pathlib import Path
import shutil
import stat
import subprocess
import sys
import tempfile
import types
import unittest
from unittest.mock import MagicMock, Mock, patch

try:
    import fcntl
    HAS_FLOCK = True
except ImportError:
    # Windows verifies decision logic; Linux CI also exercises actual flock below.
    fcntl = types.ModuleType('fcntl')
    fcntl.LOCK_SH, fcntl.LOCK_EX, fcntl.LOCK_NB = 1, 2, 4
    fcntl.flock = Mock()
    sys.modules['fcntl'] = fcntl
    HAS_FLOCK = False

import release
import release_snapshot as snapshot


class ReleaseGuardsTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name)
        self.root.chmod(0o755)
        # Windows has no POSIX mode bits. Isolate that pre-existing ROOT check;
        # filesystem/snapshot logic uses actual temporary files in both systems.
        self.guarded_root=MagicMock(wraps=self.root)
        self.guarded_root.stat.return_value=types.SimpleNamespace(st_mode=0o755)
        self.guarded_root.__truediv__.side_effect=self.root.__truediv__
        self.live_id='a'*40
        self.jar=self.root/(self.live_id+'.jar')
        self.jar.write_bytes(b'fixture JAR')
        self.sha=hashlib.sha256(self.jar.read_bytes()).hexdigest()
        self.state={'releaseId':self.live_id,'jarSha256':self.sha,'stage':'COMPLETE'}
        (self.root/'current.json').write_text(json.dumps(self.state),encoding='utf-8')
        (self.root/'deployment.lock').touch()
        self.hold=self.root/'hold'
        self.live=Mock()
        self.live.resolve.return_value=self.jar
        for name,value in [('ROOT',self.root),('HOLD',self.hold),('LIVE',self.live),('RELEASES',self.root)]:
            self.keep_patch(patch.object(snapshot,name,value))
        self.keep_patch(patch.object(snapshot,'trusted',side_effect=lambda p:Path(p)))
        self.keep_patch(patch.object(snapshot,'trusted_release_jar',side_effect=lambda p:Path(p)))

    def keep_patch(self, patcher):
        value=patcher.start()
        self.addCleanup(patcher.stop)
        return value

    def test_snapshot_checks_committed_identity_and_actual_jar_digest(self):
        self.assertEqual(snapshot.current_snapshot(),{'releaseId':self.live_id,'jarSha256':self.sha})
        self.jar.write_bytes(b'changed JAR')
        with self.assertRaisesRegex(RuntimeError,'CURRENT_RELEASE_DRIFT'):
            snapshot.current_snapshot()

    def test_snapshot_rejects_hold_wrong_target_and_incomplete_state(self):
        self.hold.touch()
        with self.assertRaisesRegex(RuntimeError,'DEPLOYMENT_ALREADY_PENDING_INSPECT'):
            snapshot.current_snapshot()
        self.hold.unlink()
        self.live.resolve.return_value=self.root/'other.jar'
        with self.assertRaisesRegex(RuntimeError,'CURRENT_RELEASE_DRIFT'):
            snapshot.current_snapshot()
        self.live.resolve.return_value=self.jar
        self.state['stage']='ACTIVATED'
        (self.root/'current.json').write_text(json.dumps(self.state),encoding='utf-8')
        with self.assertRaisesRegex(RuntimeError,'CURRENT_RELEASE_NOT_COMPLETE'):
            snapshot.current_snapshot()

    def test_snapshot_rejects_missing_malformed_and_invalid_state(self):
        state=self.root/'current.json'
        for body in ['not JSON',json.dumps({'releaseId':'bad','jarSha256':self.sha}),
                     json.dumps({'releaseId':self.live_id,'jarSha256':'bad'})]:
            state.write_text(body,encoding='utf-8')
            with self.assertRaises((ValueError,RuntimeError)):
                snapshot.current_snapshot()
        state.unlink()
        with self.assertRaises(OSError):snapshot.current_snapshot()

    def test_snapshot_cli_requires_root_and_sanitizes_error(self):
        with patch.object(snapshot.os,'geteuid',return_value=1,create=True),contextlib.redirect_stdout(io.StringIO()) as output:
            self.assertEqual(snapshot.main(),1)
        self.assertEqual(json.loads(output.getvalue()),{'error':'ROOT_REQUIRED'})
        with patch.object(snapshot.os,'geteuid',return_value=0,create=True),patch.object(snapshot,'current_snapshot',side_effect=OSError('private details')),contextlib.redirect_stdout(io.StringIO()) as output:
            self.assertEqual(snapshot.main(),1)
        self.assertEqual(json.loads(output.getvalue()),{'error':'LIVE_SNAPSHOT_UNAVAILABLE'})

    @unittest.skipUnless(HAS_FLOCK,'Actual flock requires Linux; covered by Linux CI')
    def test_snapshot_refuses_an_active_exclusive_deployment_lock(self):
        with (self.root/'deployment.lock').open('r') as holder:
            fcntl.flock(holder.fileno(),fcntl.LOCK_EX|fcntl.LOCK_NB)
            with patch.object(snapshot.os,'geteuid',return_value=0),contextlib.redirect_stdout(io.StringIO()) as output:
                self.assertEqual(snapshot.main(),1)
            self.assertEqual(json.loads(output.getvalue()),{'error':'LIVE_SNAPSHOT_UNAVAILABLE'})

    def args(self,**changes):
        values=dict(phase='deploy',release='b'*40,jar_sha='c'*64,
                    expected_current_release=self.live_id,expected_current_jar_sha=self.sha)
        return argparse.Namespace(**{**values,**changes})

    def test_changed_live_release_or_fingerprint_stops_before_any_operational_action(self):
        for actual in [{'releaseId':'d'*40,'jarSha256':self.sha},{'releaseId':self.live_id,'jarSha256':'d'*64}]:
            with self.subTest(actual=actual),patch.object(release.os,'geteuid',return_value=0,create=True),patch.object(release,'ROOT',self.guarded_root),patch.object(release,'trusted',side_effect=lambda p:p),patch.object(release,'current_snapshot',return_value=actual),patch.object(release,'environment') as env,patch.object(release,'gate_configured') as gate,patch.object(release,'command') as command:
                with self.assertRaisesRegex(RuntimeError,'CURRENT_RELEASE_CHANGED'):
                    release.run(self.args())
                env.assert_not_called();gate.assert_not_called();command.assert_not_called()
                self.assertFalse(self.hold.exists())
                self.assertEqual(self.jar.read_bytes(),b'fixture JAR')

    def test_matching_snapshot_passes_guard_while_legacy_call_remains_compatible(self):
        for args in [self.args(),self.args(expected_current_release=None,expected_current_jar_sha=None)]:
            with patch.object(release.os,'geteuid',return_value=0,create=True),patch.object(release,'ROOT',self.guarded_root),patch.object(release,'trusted',side_effect=lambda p:p),patch.object(release,'current_snapshot',return_value={'releaseId':self.live_id,'jarSha256':self.sha}),patch.object(release,'environment',side_effect=RuntimeError('AFTER_GUARD')):
                with self.assertRaisesRegex(RuntimeError,'AFTER_GUARD'):
                    release.run(args)

    def test_partial_invalid_or_non_deploy_fingerprints_rejected_before_environment(self):
        for args in [self.args(expected_current_jar_sha=None),self.args(expected_current_release='bad'),self.args(phase='rollback')]:
            with patch.object(release.os,'geteuid',return_value=0,create=True),patch.object(release,'environment') as env:
                with self.assertRaises(RuntimeError):release.run(args)
                env.assert_not_called()

    def test_existing_manual_rollback_checks_versioned_jar_and_restores_prior_identity(self):
        for corrupted in (False,True):
            with self.subTest(corrupted=corrupted):
                self.jar.write_bytes(b'fixture JAR' if not corrupted else b'corrupt JAR')
                args=self.args(phase='rollback',expected_current_release=None,expected_current_jar_sha=None)
                directory=self.root/'releases'/args.release
                directory.mkdir(parents=True,exist_ok=True)
                prior={'releaseId':self.live_id,'jarSha256':self.sha}
                state={'releaseId':args.release,'jarSha256':args.jar_sha,'stage':'ACTIVATED',
                       'previousJar':str(self.jar),'previousJarSha256':self.sha,'previousRelease':prior}
                (directory/'state.json').write_text(json.dumps(state),encoding='utf-8')
                self.hold.write_text(args.release,encoding='utf-8')
                live=Mock();live.resolve.return_value=self.root/(args.release+'.jar')
                record_writer=(lambda path,doc:Path(path).write_text(json.dumps(doc),encoding='utf-8')) if os.name=='nt' else release.record
                with patch.object(release.os,'geteuid',return_value=0,create=True),patch.object(release,'ROOT',self.guarded_root),patch.object(release,'HOLD',self.hold),patch.object(release,'LIVE',live),patch.object(release,'RELEASES',self.root),patch.object(release,'trusted',side_effect=lambda p:p),patch.object(release,'trusted_release_jar',side_effect=lambda p:p),patch.object(release,'environment',return_value={}),patch.object(release,'wait_drained'),patch.object(release,'switch_jar') as switch,patch.object(release,'command') as command,patch.object(release,'wait_ready'),patch.object(release,'record',side_effect=record_writer):
                    if corrupted:
                        with self.assertRaisesRegex(RuntimeError,'JAR_DIGEST_MISMATCH'):release.run(args)
                        switch.assert_not_called();command.assert_not_called()
                        self.assertTrue(self.hold.exists())
                    else:
                        with contextlib.redirect_stdout(io.StringIO()):release.run(args)
                        switch.assert_called_once_with(self.jar)
                        command.assert_called_once_with(['systemctl','restart',release.SERVICE],timeout=60)
                        self.assertFalse(self.hold.exists())
                        self.assertEqual(json.loads((self.root/'current.json').read_bytes()),prior)


class DeployWrapperTest(unittest.TestCase):
    def test_wrapper_validates_and_forwards_optional_fingerprints_without_executing_deploy(self):
        bash=Path('C:/Program Files/Git/bin/bash.exe') if os.name=='nt' else Path(shutil.which('bash') or '/missing')
        self.assertTrue(bash.is_file(),'Bash is required for deployment wrapper validation')
        script=Path(__file__).resolve().parents[1]/'deploy.sh'
        with tempfile.TemporaryDirectory() as folder:
            fake=Path(folder)/'python3'
            fake.write_text('#!/bin/sh\nprintf "%s\\n" "$@"\n',encoding='utf-8')
            fake.chmod(0o755)
            env={**os.environ,'PATH':folder+os.pathsep+os.environ['PATH']}
            release_id,jar_sha,old,old_jar='a'*40,'b'*64,'c'*40,'d'*64
            for args in [[release_id,jar_sha],[release_id,jar_sha,old,old_jar]]:
                response=subprocess.run([str(bash),script.as_posix(),*args],env=env,capture_output=True,text=True)
                self.assertEqual(response.returncode,0,response.stderr)
                self.assertIn('deploy\n'+release_id+'\n'+jar_sha+'\n',response.stdout)
                if len(args)==4:
                    self.assertIn('--expected-current-release\n'+old+'\n--expected-current-jar-sha\n'+old_jar,response.stdout)
            for args in [[release_id,jar_sha,old],['bad',jar_sha],[release_id,jar_sha,'bad',old_jar]]:
                response=subprocess.run([str(bash),script.as_posix(),*args],env=env,capture_output=True,text=True)
                self.assertEqual(response.returncode,2)
                self.assertEqual(response.stdout,'')


class ReleasePathTrustTest(unittest.TestCase):
    def test_only_existing_service_directory_may_be_nonroot_owned(self):
        target=Path('/opt/alpha/releases')/('a'*40+'.jar')
        def metadata(path):
            return types.SimpleNamespace(st_uid=1000 if path==snapshot.SERVICE_DIRECTORY else 0,
                                         st_mode=stat.S_IFREG|0o644 if path==target else stat.S_IFDIR|0o755)
        with patch.object(Path,'lstat',autospec=True,side_effect=metadata):
            self.assertEqual(snapshot.trusted_release_jar(target),target)
        for owner,mode in [(1000,stat.S_IFREG|0o644),(0,stat.S_IFREG|0o666),(0,stat.S_IFLNK|0o777)]:
            def changed(path):
                return types.SimpleNamespace(st_uid=owner,st_mode=mode) if path==target else metadata(path)
            with self.subTest(owner=owner,mode=mode),patch.object(Path,'lstat',autospec=True,side_effect=changed):
                with self.assertRaisesRegex(RuntimeError,'UNTRUSTED_RELEASE_PATH'):
                    snapshot.trusted_release_jar(target)


if __name__=='__main__':unittest.main()
