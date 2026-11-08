"""Private application env serialization and filesystem checks. Never executes shell input."""
import os
from pathlib import Path
import re
import shlex
import stat

ENV_FILE = Path('/opt/alpha/.env')
DROPIN = Path('/etc/systemd/system/alpha-backend.service.d/99-runtime-env.conf')
KEY = re.compile(r'[A-Za-z_][A-Za-z0-9_]*\Z')


class EnvironmentError(RuntimeError):
    """Fixed public error code; never include a value or subprocess stderr."""


def require(ok, code):
    if not ok:
        raise EnvironmentError(code)


def serialize(values):
    rows = ['# Private backend environment. Keep this file out of Git.\n']
    for key, value in sorted(values.items()):
        require(isinstance(key, str) and KEY.fullmatch(key), 'ENV_NAME_INVALID')
        require(isinstance(value, str) and '\0' not in value, 'ENV_VALUE_INVALID')
        # EnvironmentFile has no variable/command expansion. Literal newlines stay inside quotes.
        escaped = value.replace('\\', '\\\\').replace('"', '\\"')
        rows.append(key + '="' + escaped + '"\n')
    return ''.join(rows).encode('utf-8')


def parse(raw):
    """Read our quoted format for deployment validation; systemd is the authoritative parser."""
    try:
        lexer = shlex.shlex(raw.decode('utf-8'), posix=True)
        lexer.whitespace_split = True
        lexer.commenters = '#;'
        values = {}
        for field in lexer:
            key, sep, value = field.partition('=')
            require(sep and KEY.fullmatch(key), 'ENV_FORMAT_INVALID')
            values[key] = value
        return values
    except (ValueError, UnicodeError):
        raise EnvironmentError('ENV_FORMAT_INVALID') from None


def read_private(path, owner_uid=0):
    path = Path(path)
    try:
        fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW)
        try:
            info = os.fstat(fd)
            require(stat.S_ISREG(info.st_mode) and info.st_uid == owner_uid
                    and stat.S_IMODE(info.st_mode) == 0o600 and info.st_nlink == 1,
                    'ENV_FILE_PERMISSIONS_INVALID')
            with os.fdopen(fd, 'rb', closefd=False) as stream:
                raw = stream.read(1024 * 1024 + 1)
            require(len(raw) <= 1024 * 1024, 'ENV_FILE_TOO_LARGE')
            return raw
        finally:
            os.close(fd)
    except OSError:
        raise EnvironmentError('ENV_FILE_UNREADABLE') from None


def new_file(path, raw, mode=0o600):
    """Publish a complete file without overwriting an existing path, including a symlink."""
    import uuid
    path = Path(path)
    pending = path.with_name('.' + path.name + '.pending-' + uuid.uuid4().hex)
    fd = os.open(pending, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, mode)
    published = False
    try:
        os.fchmod(fd, mode)
        with os.fdopen(fd, 'wb', closefd=False) as stream:
            stream.write(raw)
            stream.flush()
            os.fsync(stream.fileno())
        os.link(pending, path, follow_symlinks=False)
        published = True
        parent = os.open(path.parent, os.O_DIRECTORY)
        try:
            os.fsync(parent)
        finally:
            os.close(parent)
    except Exception:
        if published:
            try:
                if path.lstat().st_ino == os.fstat(fd).st_ino and not path.is_symlink():
                    path.unlink()
            except FileNotFoundError:
                pass
        raise
    finally:
        os.close(fd)
        pending.unlink(missing_ok=True)


def dropin_bytes(env_file=ENV_FILE):
    # Reset both inherited lists. An older deploy may still create legacy drop-ins;
    # this final override keeps them inactive and preserves compatibility with old JARs.
    require(Path(env_file).is_absolute() and '\n' not in str(env_file) and ' ' not in str(env_file),
            'ENV_PATH_INVALID')
    return ('[Service]\nEnvironment=\nEnvironmentFile=\nEnvironmentFile=' + str(env_file) + '\n').encode()


def validate_managed(env_file=ENV_FILE, dropin=DROPIN, owner_uid=0):
    env_file, dropin = Path(env_file), Path(dropin)
    require(env_file.is_absolute() and dropin.is_absolute(), 'ENV_PATH_INVALID')
    require(not dropin.is_symlink() and dropin.is_file(), 'ENV_OVERRIDE_MISSING')
    info = dropin.stat()
    require(info.st_uid == owner_uid and not info.st_mode & 0o022
            and dropin.read_bytes() == dropin_bytes(env_file), 'ENV_OVERRIDE_INVALID')
    return parse(read_private(env_file, owner_uid))
