"""Independent, single-model Korean TTS service. Never logs request text or tokens."""
import asyncio
import hashlib
import hmac
import json
import multiprocessing as mp
import os
import shutil
from pathlib import Path
import tempfile
import time
import uuid
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse, Response

ROOT = Path(os.environ.get("TTS_ROOT", "/workspace/voice-coaching-tts"))
REVISION = os.environ.get("TTS_REVISION", "melo-ko-practice-r1")


def model_child(connection):
    import subprocess
    import torch
    import soundfile as sf
    from melo.api import TTS
    torch.set_num_threads(2)
    model = TTS(language="KR", device=os.environ.get("TTS_DEVICE", "cuda:0"))
    speaker = model.hps.data.spk2id["KR"]
    temporary = Path('/run/voice-coaching-tts') / ('child-' + str(os.getpid()))
    temporary.mkdir(mode=0o700, exist_ok=True)

    def synthesize(text):
        torch.manual_seed(20260919)
        with tempfile.TemporaryDirectory(prefix="request-", dir=temporary) as directory:
            wav, mp3 = Path(directory) / "audio.wav", Path(directory) / "audio.mp3"
            model.tts_to_file(text, speaker, str(wav), speed=0.92, quiet=True)
            info = sf.info(str(wav))
            subprocess.run(["ffmpeg", "-nostdin", "-v", "error", "-y", "-i", str(wav),
                            "-ac", "1", "-codec:a", "libmp3lame", "-b:a", "96k", str(mp3)],
                           check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            probe = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration",
                                    "-of", "default=nw=1:nk=1", str(mp3)], capture_output=True,
                                   text=True, check=True)
            return mp3.read_bytes(), round(float(probe.stdout) * 1000)

    synthesize("안녕하세요.")
    connection.send(("ready",))
    while True:
        text = connection.recv()
        try:
            connection.send(("ok", *synthesize(text)))
        except Exception:
            connection.send(("error",))


class Engine:
    def __init__(self):
        self.process = None
        self.ready = False
        self.lock = asyncio.Lock()
        self.pending = 0
        self.results = {}
        self.loading = False

    async def start(self):
        self.loading = True
        try:
            await self.initialize()
        finally:
            self.loading = False

    async def initialize(self):
        context = mp.get_context("spawn")
        self.connection, child = context.Pipe()
        self.process = context.Process(target=model_child, args=(child,), daemon=True)
        self.process.start()
        child.close()
        for _ in range(1200):
            if self.connection.poll():
                self.ready = self.connection.recv()[0] == "ready"
                return
            if not self.process.is_alive():
                return
            await asyncio.sleep(0.1)
        self.stop()

    def stop(self):
        self.ready = False
        if self.process:
            self.process.terminate()
            self.process.join(3)
            if self.process.is_alive():
                self.process.kill()
                self.process.join(3)
            temporary = Path('/run/voice-coaching-tts') / ('child-' + str(self.process.pid))
            if temporary.is_dir():
                shutil.rmtree(temporary)
        if hasattr(self, "connection"):
            self.connection.close()

    async def recover(self):
        self.stop()
        await self.start()

    async def watch(self):
        while True:
            await asyncio.sleep(5)
            if not self.loading and self.process and not self.process.is_alive():
                await self.recover()


engine = Engine()


@asynccontextmanager
async def lifespan(app):
    task = asyncio.create_task(engine.start())
    watcher = asyncio.create_task(engine.watch())
    yield
    task.cancel()
    watcher.cancel()
    engine.stop()


app = FastAPI(lifespan=lifespan, docs_url=None, redoc_url=None, openapi_url=None)


def error(status, code, request_id=None):
    return JSONResponse({"code": code, "requestId": request_id,
                         "retryable": status in (429, 503, 504)}, status_code=status)


@app.get("/health/live")
async def live(request: Request):
    return {"status": "alive", "revision": REVISION}


@app.get("/health/ready")
async def ready(request: Request):
    if not engine.ready or not engine.process.is_alive():
        return error(503, "NOT_READY")
    return {"status": "ready", "revision": REVISION}


@app.post("/v1/tts/synthesize")
async def synthesize(request: Request):
    try:
        data = await request.json()
        text = data["text"]
    except (ValueError, TypeError, KeyError, UnicodeError):
        return error(400, "INVALID_JSON")
    request_id = data.get("requestId") or str(uuid.uuid4())
    # One IPC pipe carries request/response pairs. The lock preserves that pairing;
    # requests wait without an admission limit, timeout, revision gate or deduplication cache.
    async with engine.lock:
        try:
            engine.connection.send(text)
            while not engine.connection.poll():
                if not engine.process.is_alive():
                    raise BrokenPipeError()
                await asyncio.sleep(0.01)
            result = engine.connection.recv()
            if result[0] != "ok":
                return error(503, "SYNTHESIS_FAILED", request_id)
            _, audio, duration = result
            return Response(audio, media_type="audio/mpeg", headers={
                "X-Request-Id": request_id, "X-TTS-Revision": REVISION,
                "X-Audio-SHA256": hashlib.sha256(audio).hexdigest(), "X-Audio-Duration-Ms": str(duration)})
        except (BrokenPipeError, EOFError, OSError, AttributeError):
            engine.ready = False
            asyncio.create_task(engine.recover())
            return error(503, "MODEL_UNAVAILABLE", request_id)
