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
TOKEN = Path(os.environ.get("TTS_TOKEN_FILE", "/run/voice-coaching-tts/token")).read_text().strip()
if len(TOKEN) < 32:
    raise RuntimeError("TTS token is missing or too short")


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
            if not 0 < info.duration <= 120:
                raise ValueError("INVALID_DURATION")
            subprocess.run(["ffmpeg", "-nostdin", "-v", "error", "-y", "-i", str(wav),
                            "-ac", "1", "-codec:a", "libmp3lame", "-b:a", "96k", str(mp3)],
                           check=True, timeout=2, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            if not 3 <= mp3.stat().st_size <= 2000000:
                raise ValueError("INVALID_SIZE")
            subprocess.run(["ffmpeg", "-nostdin", "-v", "error", "-xerror", "-i", str(mp3),
                            "-f", "null", "-"], check=True, timeout=2,
                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            probe = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration",
                                    "-of", "default=nw=1:nk=1", str(mp3)], capture_output=True,
                                   text=True, check=True, timeout=2)
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


def authenticated(request):
    return hmac.compare_digest(request.headers.get("authorization", ""), "Bearer " + TOKEN)


@app.get("/health/live")
async def live(request: Request):
    if not authenticated(request):
        return error(401, "UNAUTHORIZED")
    return {"status": "alive", "revision": REVISION}


@app.get("/health/ready")
async def ready(request: Request):
    if not authenticated(request):
        return error(401, "UNAUTHORIZED")
    if not engine.ready or not engine.process.is_alive():
        return error(503, "NOT_READY")
    return {"status": "ready", "revision": REVISION}


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("DUPLICATE_KEY")
        result[key] = value
    return result


@app.post("/v1/tts/synthesize")
async def synthesize(request: Request):
    if not authenticated(request):
        return error(401, "UNAUTHORIZED")
    if request.headers.get("content-type", "").split(";")[0] != "application/json":
        return error(415, "CONTENT_TYPE")
    body = bytearray()
    async for chunk in request.stream():
        body.extend(chunk)
        if len(body) > 32768:
            return error(413, "BODY_TOO_LARGE")
    try:
        data = json.loads(body.decode("utf-8"), object_pairs_hook=unique_object,
                          parse_constant=lambda value: (_ for _ in ()).throw(ValueError(value)))
    except (ValueError, UnicodeError):
        return error(400, "INVALID_JSON")
    fields = {"schemaVersion", "requestId", "synthesisRevision", "text", "language", "voice", "speakingRate", "format"}
    if not isinstance(data, dict) or set(data) != fields:
        return error(422, "INVALID_INPUT")
    request_id = data.get("requestId")
    try:
        if str(uuid.UUID(request_id)) != request_id:
            raise ValueError()
        if (data["schemaVersion"] != "voice-coaching.tts-request.v1" or data["language"] != "ko-KR"
                or data["voice"] != "ko-KR-practice-v1" or data["format"] != "mp3"
                or type(data["speakingRate"]) not in (int, float) or data["speakingRate"] != 0.92
                or not isinstance(data["text"], str) or not data["text"].strip()
                or len(data["text"].encode("utf-8")) > 4500
                or not isinstance(data["synthesisRevision"], str)):
            raise ValueError()
    except (ValueError, TypeError, AttributeError):
        return error(422, "INVALID_INPUT")
    if data["synthesisRevision"] != REVISION:
        return error(409, "REVISION_MISMATCH", request_id)
    digest = hashlib.sha256(json.dumps(data, sort_keys=True, ensure_ascii=False).encode()).hexdigest()
    now = time.monotonic()
    engine.results = {key: value for key, value in engine.results.items() if value[0] > now}
    if request_id in engine.results:
        _, previous, response = engine.results[request_id]
        return response if previous == digest else error(409, "REQUEST_ID_CONFLICT", request_id)
    if not engine.ready or not engine.process.is_alive():
        return error(503, "NOT_READY", request_id)
    if engine.pending >= 2:
        return error(429, "QUEUE_FULL", request_id)
    engine.pending += 1
    acquired = False
    try:
        await asyncio.wait_for(engine.lock.acquire(), timeout=2)
        acquired = True
        if request_id in engine.results:
            _, previous, response = engine.results[request_id]
            return response if previous == digest else error(409, "REQUEST_ID_CONFLICT", request_id)
        if not engine.ready:
            return error(503, "NOT_READY", request_id)
        start = time.monotonic()
        engine.connection.send(data["text"])
        while not engine.connection.poll():
            if time.monotonic() - start > 22 or not engine.process.is_alive():
                engine.ready = False
                asyncio.create_task(engine.recover())
                return error(504, "SYNTHESIS_TIMEOUT", request_id)
            await asyncio.sleep(0.01)
        result = engine.connection.recv()
        if result[0] != "ok":
            return error(503, "SYNTHESIS_FAILED", request_id)
        _, audio, duration = result
        response = Response(audio, media_type="audio/mpeg", headers={
            "X-Request-Id": request_id, "X-TTS-Revision": REVISION,
            "X-Audio-SHA256": hashlib.sha256(audio).hexdigest(), "X-Audio-Duration-Ms": str(duration)})
        if len(engine.results) >= 8:
            engine.results.pop(next(iter(engine.results)))
        engine.results[request_id] = (time.monotonic() + 120, digest, response)
        return response
    except asyncio.TimeoutError:
        return error(429, "QUEUE_TIMEOUT", request_id)
    except (BrokenPipeError, EOFError, OSError):
        engine.ready = False
        asyncio.create_task(engine.recover())
        return error(503, "MODEL_UNAVAILABLE", request_id)
    except asyncio.CancelledError:
        if acquired:
            engine.ready = False
            asyncio.create_task(engine.recover())
        raise
    finally:
        if acquired:
            engine.lock.release()
        engine.pending -= 1
