"""One-off operator recovery: release only a failed J406 loopback forward."""
import json
import os
from pathlib import Path
import re
import signal
import subprocess
import time
import urllib.request

def check(value):
    if not value:
        raise ValueError('TUNNEL_SESSION_IDENTITY_CHANGED')

def healthy():
    try:
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
        with opener.open('http://127.0.0.1:28080/health', timeout=3) as response:
            return response.status == 200
    except Exception:
        return False

check(os.geteuid() == 0)
if healthy():
    print(json.dumps({'status':'ALREADY_HEALTHY'}))
    raise SystemExit(0)
rows = subprocess.check_output(['ss','-H','-ltnp'], text=True).splitlines()
owners = set()
for row in rows:
    endpoint = row.split()[3]
    if endpoint.rsplit(':',1)[-1] == '28080':
        check(endpoint.startswith(('127.0.0.1:', '[::1]:')))
        owners.update(int(value) for value in re.findall(r'pid=(\d+)',row))
check(len(owners) == 1)
pid = owners.pop()
process = Path('/proc')/str(pid)
comm = (process/'comm').read_text().strip()
check(comm.startswith('sshd') and pid > 1)
cmd = (process/'cmdline').read_bytes().replace(b'\0',b' ').decode()
print(json.dumps({'listenerPid':pid,'comm':comm,'session':cmd.strip(),'uid':process.stat().st_uid}),flush=True)
check('ec2-user' in cmd and ('notty' in cmd or '[priv]' in cmd))
ports = set()
for row in rows:
    if pid in {int(value) for value in re.findall(r'pid=(\d+)',row)}:
        endpoint = row.split()[3]
        check(endpoint.startswith(('127.0.0.1:', '[::1]:')))
        ports.add(int(endpoint.rsplit(':',1)[-1]))
check(28080 in ports and ports <= {28022,28080,28083})
start = (process/'stat').read_text().rsplit(')',1)[1].split()[19]
check(not healthy())
check((process/'stat').read_text().rsplit(')',1)[1].split()[19] == start)
os.kill(pid,signal.SIGTERM)
print(json.dumps({'status':'STALE_SESSION_TERMINATED','pid':pid,'ports':sorted(ports)}),flush=True)
for _ in range(45):
    if healthy():
        print(json.dumps({'status':'FORWARD_HEALTHY'}),flush=True)
        raise SystemExit(0)
    time.sleep(2)
raise ValueError('TUNNEL_RECONNECT_TIMEOUT')
