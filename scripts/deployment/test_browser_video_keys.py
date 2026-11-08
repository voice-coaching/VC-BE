import os
from pathlib import Path
import tempfile
import unittest
from browser_video_keys import prepare
from runtime_environment import new_file, serialize, dropin_bytes


class BrowserVideoKeys(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup)
        self.base=Path(self.tmp.name)

    def prepare(self):
        return prepare(self.base/'keys',self.base/'dropin'/'40-browser-video.conf',
                       os.getuid(),os.getgid(),owner_uid=os.getuid(),owner_gid=os.getgid())

    def test_first_setup_is_private_and_repeat_keeps_the_same_key(self):
        first=self.prepare();private=(self.base/'keys/private.pem').read_bytes()
        self.assertFalse(first['privateKeyExported'])
        self.assertEqual((self.base/'keys/private.pem').stat().st_mode&0o777,0o600)
        self.assertEqual(first,self.prepare())
        self.assertEqual(private,(self.base/'keys/private.pem').read_bytes())

    def test_wrong_existing_configuration_is_preserved_and_rejected(self):
        self.prepare();path=self.base/'keys/runtime.env';path.write_text('existing-settings\n')
        with self.assertRaises(RuntimeError):self.prepare()
        self.assertEqual(path.read_text(),'existing-settings\n')

    def test_symlink_private_key_is_rejected(self):
        root=self.base/'keys';root.mkdir(mode=0o750)
        (self.base/'original').write_text('original')
        (root/'private.pem').symlink_to(self.base/'original')
        with self.assertRaises(RuntimeError):self.prepare()
        self.assertEqual((self.base/'original').read_text(),'original')

    def managed(self, change=None):
        self.prepare()
        root = self.base/'keys'
        values = dict(line.split('=', 1) for line in (root/'runtime.env').read_text().splitlines())
        values.update(AI_BROWSER_VIDEO_ENABLED='false', AI_BROWSER_VIDEO_KEY_ID='operator-key-id')
        if change: change(values)
        env, override = self.base/'.env', self.base/'99-runtime-env.conf'
        new_file(env, serialize(values))
        new_file(override, dropin_bytes(env), 0o644)
        return env, override

    def managed_prepare(self, env, override):
        return prepare(self.base/'keys', self.base/'dropin'/'40-browser-video.conf',
                       os.getuid(), os.getgid(), owner_uid=os.getuid(), owner_gid=os.getgid(),
                       runtime_env=env, runtime_dropin=override)

    def test_managed_deploy_preserves_operator_settings_and_legacy_files(self):
        env, override = self.managed()
        paths = [env, override, self.base/'keys/private.pem', self.base/'keys/runtime.env',
                 self.base/'dropin/40-browser-video.conf']
        before = {p: p.read_bytes() for p in paths}
        result = self.managed_prepare(env, override)
        self.assertEqual(result['environmentMode'], 'consolidated')
        self.assertEqual(result['keyId'], 'operator-key-id')
        self.assertEqual({p: p.read_bytes() for p in paths}, before)

    def test_managed_missing_private_key_is_rejected_without_regeneration(self):
        env, override = self.managed()
        private = self.base/'keys/private.pem'; private.unlink()
        with self.assertRaises(RuntimeError): self.managed_prepare(env, override)
        self.assertFalse(private.exists())

    def test_managed_incomplete_settings_are_rejected_without_legacy_fallback(self):
        env, override = self.managed(lambda values: values.pop('AI_BROWSER_VIDEO_ISSUER'))
        before = env.read_bytes()
        with self.assertRaises(RuntimeError): self.managed_prepare(env, override)
        self.assertEqual(env.read_bytes(), before)
