"""Dedicated TTS supervisor; does not manage any analysis process."""
import fcntl
import os
from pathlib import Path
import signal
import subprocess
import time

root = Path('/workspace/voice-coaching-tts')
run = Path('/run/voice-coaching-tts')
run.mkdir(mode=0o700, exist_ok=True)
lock = (run / 'manager.lock').open('w')
fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
(run / 'manager.pid').write_text(str(os.getpid()))
env = os.environ.copy()
env.update(HF_HOME=str(root / 'hf'), NLTK_DATA=str(root / 'nltk'),
           HF_HUB_OFFLINE='1', TRANSFORMERS_OFFLINE='1', OMP_NUM_THREADS='2',
           TTS_TOKEN_FILE=str(root / 'secrets/token'), TTS_DEVICE='cuda:0')
stopping = False
child = None


def stop(*args):
    global stopping
    stopping = True
    if child and child.poll() is None:
        child.terminate()


signal.signal(signal.SIGTERM, stop)
signal.signal(signal.SIGINT, stop)
with (run / 'server.log').open('ab', buffering=0) as log:
    while not stopping:
        child = subprocess.Popen([
            '/opt/voice-coaching-tts/venv/bin/python', '-m', 'uvicorn', 'server:app',
            '--host', '127.0.0.1', '--port', '8082', '--no-access-log', '--log-level', 'warning',
        ], cwd=root / 'service', env=env, stdout=log, stderr=log)
        (run / 'server.pid').write_text(str(child.pid))
        child.wait()
        if not stopping:
            time.sleep(5)
