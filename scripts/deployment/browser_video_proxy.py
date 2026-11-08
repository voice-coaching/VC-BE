"""Add only the browser-video upload location to the existing AI TLS proxy."""
import argparse
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import stat
import subprocess

MARKER='# voice-coaching browser-video-grant-v1'
BLOCK='''    # voice-coaching browser-video-grant-v1
    location ^~ /browser/v1/video-analysis-jobs {
        client_max_body_size 256m;
        client_body_timeout 125s;
        proxy_pass http://127.0.0.1:28080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header Authorization $http_authorization;
        proxy_set_header Connection "";
        proxy_request_buffering off;
        proxy_buffering off;
        proxy_cache off;
        proxy_next_upstream off;
        proxy_connect_timeout 15s;
        proxy_send_timeout 125s;
        proxy_read_timeout 930s;
    }

'''


def require(ok):
    if not ok:raise RuntimeError('BROWSER_VIDEO_PROXY_CONFIGURATION_REQUIRES_REVIEW')


def candidate(source):
    require(re.search(r'(?m)^\s*server_name\s+ai\.voice-coaching\.site\s*;',source) is not None
            and 'proxy_pass http://127.0.0.1:28080;' in source)
    if MARKER in source:
        require(source.count(BLOCK)==1)
        return source
    require('/browser/v1/video-analysis-jobs' not in source)
    matches=list(re.finditer(r'(?m)^[ \t]*location\s+/\s*\{',source))
    require(len(matches)==1)
    i=matches[0].start()
    return source[:i]+BLOCK+source[i:]


def command(args):
    r=subprocess.run(args,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,timeout=20)
    require(r.returncode==0)
    return r.stdout


def atomic(path,raw,mode,uid=0,gid=0):
    tmp=path.with_name('.'+path.name+'.browser-'+secrets.token_hex(8))
    fd=os.open(tmp,os.O_CREAT|os.O_EXCL|os.O_WRONLY|os.O_NOFOLLOW,mode)
    try:
        os.fchown(fd,uid,gid)
        with os.fdopen(fd,'wb') as f:f.write(raw);f.flush();os.fsync(f.fileno())
        os.replace(tmp,path)
        p=os.open(path.parent,os.O_DIRECTORY)
        try:os.fsync(p)
        finally:os.close(p)
    finally:tmp.unlink(missing_ok=True)


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--apply',action='store_true');a=p.parse_args()
    require(a.apply and os.geteuid()==0)
    fd=os.open('/var/lock/voice-coaching-browser-video-proxy.lock',os.O_CREAT|os.O_RDWR|os.O_NOFOLLOW,0o600)
    with os.fdopen(fd,'a') as lock:
        require(os.fstat(lock.fileno()).st_uid==0 and not os.fstat(lock.fileno()).st_mode&0o077)
        fcntl.flock(lock,fcntl.LOCK_EX)
        config=command(['/usr/sbin/nginx','-T']).decode()
        headers=list(re.finditer(r'(?m)^# configuration file ([^\n]+):\n',config))
        sources=[]
        for i,h in enumerate(headers):
            part=config[h.end():headers[i+1].start() if i+1<len(headers) else len(config)]
            if (re.search(r'(?m)^\s*server_name\s+ai\.voice-coaching\.site\s*;',part)
                    and 'proxy_pass http://127.0.0.1:28080;' in part):sources.append(Path(h[1]).resolve())
        sources=list(set(sources));require(len(sources)==1)
        path=sources[0];s=path.lstat()
        require(path.is_relative_to('/etc/nginx') and stat.S_ISREG(s.st_mode) and s.st_uid==0
                and not s.st_mode&0o022 and s.st_size<1024**2)
        original=path.read_bytes();updated=candidate(original.decode()).encode()
        if original!=updated:
            backup=Path('/var/lib/alpha-deploy/browser-video-proxy')
            backup.mkdir(mode=0o700,parents=True,exist_ok=True)
            require(not backup.is_symlink() and backup.stat().st_uid==0 and not backup.stat().st_mode&0o077)
            pin=hashlib.sha256(original).hexdigest()
            saved=backup/(pin+'.conf')
            if not saved.exists():atomic(saved,original,0o600)
            require(saved.read_bytes()==original and path.read_bytes()==original)
            atomic(path,updated,stat.S_IMODE(s.st_mode),s.st_uid,s.st_gid)
            try:
                command(['/usr/sbin/nginx','-t'])
                command(['systemctl','reload','nginx'])
            except Exception:
                if path.read_bytes()==updated:
                    atomic(path,original,stat.S_IMODE(s.st_mode),s.st_uid,s.st_gid)
                    command(['/usr/sbin/nginx','-t']);command(['systemctl','reload','nginx'])
                raise
        print(json.dumps(dict(browserVideoProxyReady=True,maximumBodyBytes=256*1024**2,
                              existingRoutesChanged=False,configurationChanged=original!=updated)))


if __name__=='__main__':
    try:main()
    except Exception:
        print(json.dumps(dict(errorCode='BROWSER_VIDEO_PROXY_SETUP_FAILED')))
        raise SystemExit(1)
