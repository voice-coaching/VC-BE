"""One non-blocking lock shared by release mutations and JAR retention."""
from contextlib import contextmanager
import os
from pathlib import Path
import stat
from common import ROOT, require, trusted


@contextmanager
def exclusive(path=ROOT / 'deployment.lock', create=False):
    import fcntl
    path = Path(path)
    trusted(path.parent)
    flags = os.O_RDWR | os.O_NOFOLLOW
    if create:
        flags |= os.O_CREAT
    fd = os.open(path, flags, 0o600)
    try:
        info = os.fstat(fd)
        require(stat.S_ISREG(info.st_mode) and info.st_uid == 0 and not info.st_mode & 0o022,
                'DEPLOYMENT_LOCK_UNTRUSTED')
        fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
        yield
    finally:
        os.close(fd)
