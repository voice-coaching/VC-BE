"""One-time browser-grant setup on the existing Backend host. Never exports private keys."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import pwd
import stat
import subprocess

KEY_ROOT=Path('/etc/alpha-browser-video')
DROPIN=Path('/etc/systemd/system/alpha-backend.service.d/40-browser-video.conf')
KEY_ID='browser-video-20261108-v1'


def require(ok):
    if not ok: raise RuntimeError('BROWSER_VIDEO_KEY_SETUP_FAILED')


def command(args):
    r=subprocess.run(args,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,timeout=15,
                     env={'PATH':'/usr/bin:/bin','LANG':'C.UTF-8'})
    require(r.returncode==0)
    return r.stdout


def new_file(path,raw,mode,uid,gid):
    fd=os.open(path,os.O_WRONLY|os.O_CREAT|os.O_EXCL|os.O_NOFOLLOW,mode)
    try:
        os.fchown(fd,uid,gid)
        with os.fdopen(fd,'wb',closefd=False) as f:
            f.write(raw);f.flush();os.fsync(f.fileno())
    finally:os.close(fd)
    parent=os.open(path.parent,os.O_DIRECTORY)
    try:os.fsync(parent)
    finally:os.close(parent)


def fixed_file(path,raw,mode,uid,gid):
    if path.exists() or path.is_symlink():
        s=path.lstat()
        require(stat.S_ISREG(s.st_mode) and s.st_uid==uid and s.st_gid==gid
                and stat.S_IMODE(s.st_mode)==mode and path.read_bytes()==raw)
    else:new_file(path,raw,mode,uid,gid)


def prepare(root,dropin,uid,gid,*,owner_uid=0,owner_gid=0):
    require(root.is_absolute() and dropin.is_absolute())
    root.mkdir(mode=0o750,parents=False,exist_ok=True)
    require(not root.is_symlink() and root.stat().st_uid==owner_uid
            and not root.stat().st_mode&0o022)
    os.chown(root,owner_uid,gid)
    private=root/'private.pem'
    if not private.exists() and not private.is_symlink():
        raw=command(['/usr/bin/openssl','genpkey','-algorithm','ED25519'])
        new_file(private,raw,0o600,uid,gid)
    s=private.lstat()
    require(stat.S_ISREG(s.st_mode) and s.st_uid==uid and s.st_gid==gid and stat.S_IMODE(s.st_mode)==0o600)
    public=command(['/usr/bin/openssl','pkey','-in',str(private),'-pubout'])
    fixed_file(root/'public.pem',public,0o644,owner_uid,gid)
    values={
        'AI_BROWSER_VIDEO_ENABLED':'true',
        'AI_BROWSER_VIDEO_ISSUER':'voice-coaching-backend',
        'AI_BROWSER_VIDEO_AUDIENCE':'voice-coaching-browser-video-ai',
        'AI_BROWSER_VIDEO_AI_BASE_URL':'https://ai.voice-coaching.site',
        'AI_BROWSER_VIDEO_ALLOWED_ORIGINS':'https://vc-fe.vercel.app',
        'AI_BROWSER_VIDEO_KEY_ID':KEY_ID,
        'AI_BROWSER_VIDEO_PRIVATE_KEY_FILE':str(private),
        'AI_BROWSER_VIDEO_PUBLIC_KEY_FILE':str(root/'public.pem')}
    raw=''.join(k+'='+v+'\n' for k,v in sorted(values.items())).encode()
    fixed_file(root/'runtime.env',raw,0o600,owner_uid,owner_gid)
    dropin.parent.mkdir(mode=0o755,parents=True,exist_ok=True)
    require(not dropin.parent.is_symlink() and dropin.parent.stat().st_uid==owner_uid
            and not dropin.parent.stat().st_mode&0o022)
    fixed_file(dropin,('[Service]\nEnvironmentFile='+str(root/'runtime.env')+'\n').encode(),
               0o644,owner_uid,owner_gid)
    return dict(keyId=KEY_ID,publicKeySha256=hashlib.sha256(public).hexdigest(),
                privateKeyExported=False,configurationPrepared=True)


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--apply',action='store_true');a=p.parse_args()
    require(a.apply and os.geteuid()==0)
    user=command(['systemctl','show','alpha-backend.service','-p','User','--value']).decode().strip() or 'root'
    account=pwd.getpwnam(user)
    result=prepare(KEY_ROOT,DROPIN,account.pw_uid,account.pw_gid)
    command(['systemctl','daemon-reload'])
    print(json.dumps(result))


if __name__=='__main__':
    try:main()
    except Exception:
        print(json.dumps({'errorCode':'BROWSER_VIDEO_KEY_SETUP_FAILED'}))
        raise SystemExit(1)
