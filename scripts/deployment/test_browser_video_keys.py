import os
from pathlib import Path
import tempfile
import unittest
from browser_video_keys import prepare


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
