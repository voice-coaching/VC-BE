"""Backend release file operations without deployment policy gates."""
import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import shutil
import subprocess
from common import ROOT, HOLD, SERVICE, record
from release_snapshot import RELEASES

LIVE = Path('/opt/alpha/app.jar')

def switch_jar(target):
    pending=LIVE.with_name('.app.jar.pipeline-pending')
    pending.unlink(missing_ok=True)
    pending.symlink_to(target)
    os.replace(pending,LIVE)

def run(args):
    ROOT.mkdir(parents=True,exist_ok=True)
    RELEASES.mkdir(parents=True,exist_ok=True)
    directory=ROOT/'releases'/args.release
    directory.mkdir(parents=True,exist_ok=True)
    state_path=directory/'state.json'
    state=json.loads(state_path.read_text()) if state_path.exists() else {}
    target=RELEASES/(args.release+'.jar')
    incoming=Path('/var/tmp')/('alpha-'+args.release+'.jar')
    if args.phase=='check':
        print(json.dumps({'stage':'POLICY_CHECKS_REMOVED','release':args.release}));return
    if args.phase in ('deploy','activate'):
        previous=LIVE.resolve()
        if incoming.exists(): shutil.copyfile(incoming,target)
        state.update(releaseId=args.release,jarSha256=args.jar_sha,previousJar=str(previous))
        record(state_path,state)
        switch_jar(target)
        subprocess.run(['systemctl','restart',SERVICE],check=True)
        state['stage']='ACTIVATED'
    elif args.phase=='rollback':
        switch_jar(Path(state['previousJar']))
        subprocess.run(['systemctl','restart',SERVICE],check=True)
        state['stage']='ROLLED_BACK'
    elif args.phase=='abort': state['stage']='ABORTED'
    elif args.phase=='hold': state['stage']='POLICY_CHECKS_REMOVED'
    elif args.phase=='resume': state['stage']='ACTIVATED'
    HOLD.unlink(missing_ok=True)
    state.update(releaseId=args.release,jarSha256=args.jar_sha,updatedAt=datetime.now(timezone.utc).isoformat())
    record(state_path,state)
    if args.phase in ('deploy','activate','resume'): record(ROOT/'current.json',state)
    print(json.dumps({'stage':state['stage'],'release':args.release}))

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('phase',choices=('check','deploy','hold','activate','resume','abort','rollback'))
    parser.add_argument('release');parser.add_argument('jar_sha')
    parser.add_argument('--expected-current-release');parser.add_argument('--expected-current-jar-sha')
    run(parser.parse_args())
