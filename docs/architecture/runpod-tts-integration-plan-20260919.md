> 정책 변경(2026-09-19): 사용자 지시로 **2인 사전 검수 승인 조건을 폐지**한다. 아래 과거 검수·승인 요구는 더 이상 목표 정책이 아니다. [전체 문장 TTS 수정계획](all-practice-content-tts-plan-20260919.md)을 우선 적용한다. 제한 제거 코드는 로컬 수정 상태이며 운영 배포 전에는 기존 동작이 유지된다.

# RunPod 한국어 예시 발음 TTS 연동 계획

> 후속 실행 기록: [DB 예문 전환·TTS 설치 진행 기록](db-example-tts-deployment-20260919.md). 프론트 운영 배포·DB 예문 5개 등록·RunPod TTS 독립 설치는 수행됐으며, 2026-09-19 16:46 KST에 승인된 AWS 토큰 전달·nginx 경로·자동 생성 워커 활성화까지 완료했다. 예문 5건이 생성·저장됐으며 공개 청취 검수는 대기 중이다. [최신 운영 설정](example-tts-operations-20260919.md)을 따른다. 아래 계획 작성 시점의 미설치 설명과 구분한다.

작성·재검토: 2026-09-19 KST. **TTS 설치·배포·합성은 아직 수행하지 않았다.** 후속 사용자 요청으로 프론트 예문·연습 데이터를 DB API로 연결하는 로컬 변경을 진행했다. 신규 예문 자동 생성의 최신 설계는 [DB 예문 → TTS 자동 생성 계획](db-example-tts-automation-plan-20260919.md)을 따른다. 아래 운영 수치는 이전 관측이며 새 측정값이 아니다.

후속 설계에서 달라진 점: 수동 사전 생성 시작을 **예문 INSERT와 함께 기록되는 outbox + AWS 전용 워커**로 대체한다. 새 예문마다 자동 생성하며 검수 결과는 배포별 승인 manifest 대신 **DB 승인 테이블**에 저장한다. 생성 자동화와 공개 검수는 별개다. 아래 수동 관리 도구·고정 승인 manifest·구 FE route 유지 설명은 이전 전환안이며 이 후속 설계가 우선한다.

## 1. 권장 결정

**현재 RunPod 안의 독립 TTS 프로세스 → AWS의 기존 예문 음성 API → 프론트 MP3 재생**을 유지한다. CLOVA 종료로 GPU 여유가 생겼으므로 **GPU 합성을 우선 성능 후보로 평가하고 CPU를 비교 기준으로 측정**한다. Python 환경·포트·동시성·자원 예산은 기존 분석과 분리한다. 운영 장치는 품질·지연·분석 간섭 측정 후 명시적으로 고정하며 `device=auto`로 두지 않는다. 별도 팟은 동거 부하 검증에 실패할 때 선택한다.

**1차 공개 범위는 검수·승인된 고정 코스 예문의 캐시 재생이다.** 임의 문장 합성, 내 문장 TTS, 일반 연습 기준 음성 교체는 포함하지 않는다. 캐시 미스에서 생성한 새 바이트는 검수 전 사용자에게 제공하지 않는다. 서버 간 합성 API는 사전 생성·검수용으로 먼저 연결한다.

**2026-09-18 16:06 UTC / 2026-09-19 01:06 KST 이후** 메모리 상세를 재확인했다. 최초 문서의 “RAM 여유 부족으로 별도 팟 필요” 판단은 파일 캐시 포함 사용량을 근거로 삼아 부정확했다. 아래 재검토 수치로 정정한다.

첫 평가 엔진은 **MeloTTS Korean**으로 정한다. 한국어와 CPU 실행을 공식 지원하고 라이브러리/한국어 모델 카드에 MIT가 표시돼 있다. CPU 실시간 성능은 공급자의 설명이며 우리 문장·자원에서 측정한 결과가 아니다. 실제 발음 품질·지연을 통과해야 운영 엔진으로 확정한다. [공식 저장소](https://github.com/myshell-ai/MeloTTS), [한국어 모델 카드](https://huggingface.co/myshell-ai/MeloTTS-Korean)

TTS는 예시를 듣기 위한 음성 생성이다. Seungun 검출, 영상 기준값, CLOVA/GPT 피드백, 승급 점수를 거치지 않는다. 합성 음성을 독립 검증 없이 발음 채점 정답이나 입술 참조로 등록하지 않는다.

## 2. 확인한 현재 구조

로컬 FE `dev@b59135afcd38d9b2842ccb26f0b5fa1f37c864a2`, BE `4d6d2c0c0f57db652cf1a63a426e7ca4f9a9d19c`를 읽었다. 이 SHA를 현재 Vercel 배포 SHA로 단정하지 않는다.

| 화면/계층 | 실제 구현 | 반환/사용 방식 |
| --- | --- | --- |
| 코스 예문 | FE `src/components/tts-practice-player.tsx::play` | `GET /api/tts?exampleId=...` → `response.blob()` → object URL → audio 재생 |
| FE TTS 서버 | `src/app/api/tts/route.ts::GET/synthesize` | Google Chirp 호출, 성공 `200 audio/mpeg` 바이너리. 실패 `503 {code,message}` |
| FE 예문 원본 | `src/lib/practice-examples.ts`, `course-lesson.tsx` | 카테고리별 정적 문장과 `consonant-1` 같은 ID 사용 |
| BE 예문 목록 | `PracticeExampleController.list` | `GET /api/courses/{courseId}/steps/{stepId}/practice-examples`, DB 발행 예문 ID·revision·practiceContentId 반환 |
| BE 예문 음성 | `PracticeExampleController.audio` | `GET /api/practice-examples/{exampleId}/audio`, 사용자 인증, MP3 또는 ETag 304 |
| BE 합성 | `ExampleAudioService.audio` → `ExampleSpeechSynthesizer` → `GoogleChirpSynthesizer` | 현재 Google provider 하나. 기본 voice/rate가 Chirp/0.92로 고정됨 |
| 일반 연습 기준 발음 | FE `reference-player.tsx::toggle` | 기준 음성 목록 → 선택한 음성의 playback URL → audio 재생 |
| 내 문장 듣기 | FE `my-script-screen.tsx` | 브라우저 `speechSynthesis`; 이번 1차 코스 예문 이관과 별개 |

**중요: 성공 응답은 JSON이 아니라 MP3 바이트다.** `{audioUrl:...}` JSON을 현재 `/api/tts`에 보내면 프론트가 JSON을 음성 Blob으로 처리해 재생에 실패한다.

BE는 이미 DB 음성 캐시, 중복 생성 lease, 사용자당 분당 20회·전역 분당 100회 캐시 미스 제한을 갖는다. 외부 호출은 DB 트랜잭션 밖에서 수행한다. 동일 항목 생성 중에는 현재 503, 실패 후 5초 대기, lease 만료는 5분이다.

기존 FE API client는 기본 20초, JSON 응답 처리 중심이다. 인증과 refresh를 재사용하는 **바이너리 요청 함수**가 필요하다. Vercel backend proxy는 body stream을 전달하지만 Content-Length를 제거하고 Cache-Control을 no-store로 덮어쓴다. MP3 전달은 가능하나 BE의 private 캐시 헤더가 브라우저까지 그대로 유지된다고 가정하면 안 된다.

일반 기준 발음 reader는 현재 DB `audioUrl`을 그대로 반환하면서 `expiresAt=현재+10분`을 붙인다. 실제 URL 서명을 생성하는 코드가 아니다. TTS 기준 음성을 비공개 객체로 보관할 때 이 경로도 정식 서명 URL 발급으로 바꿔야 한다.

## 3. 운영 환경 관측과 별도 CLOVA 종료 기록

초기 관측: 2026-09-19 00:51 KST 전후. 아래 표의 CLOVA 점유량은 **종료 전 기록**이며, 최신 종료 관측은 표 아래에 구분했다.

| 대상 | 관측 | 계획에 반영 |
| --- | --- | --- |
| 현재 분석 RunPod | A40 46,068MiB 중 38,808MiB 사용 | 잔류 CLOVA PID 20312가 37,324MiB, scorer PID 41669가 1,470MiB 점유. GPT 전환 자체가 CLOVA VRAM을 해제하지는 않음 |
| 컨테이너 RAM 재확인 | 한도 약 46.6GiB, 총 사용 약 44.1GiB. 익명 RSS 약 2.4GiB, cache 약 41.1GiB | 캐시를 프로세스 필수 RAM으로 간주한 최초 판단 정정. CPU TTS 동거 평가 가능 |
| 파일 캐시 상세 | inactive_file 약 16.8GiB, active_file 약 24.3GiB, shmem 약 24MiB, writeback 0 | 상당 부분은 회수 가능한 파일 캐시. 전체 캐시를 즉시 사용 가능한 free RAM이라고도 단정하지 않음. drop_caches 실행 안 함 |
| CPU / 디스크 | 7.65 CPU quota, overlay 여유 약 28GB | TTS thread·동시성 제한 후 분석 지연과 함께 평가. overlay를 영구 저장소로 사용하지 않음 |
| 실행 환경 | api/g2pk/hyperclova/scorer, API 8080, CLOVA 8000 | 공용 Python/Torch 변경 금지 |
| TTS 흔적 | CosyVoice 연구용 worker/학습 스크립트 존재 | 서비스 설치·모델 준비·사용자 API 운영의 증거는 아님 |
| AWS backend | active, PID 36860, 관측 JAR 파일명 `d42d357640645c96d31274379e01c9ffb99a5464.jar` | JAR에 ExampleAudioService/GoogleChirpSynthesizer 포함 확인. 파일명만으로 실행 소스 일치를 확정하지 않음 |
| AWS 프로세스 설정 | CHIRP_TTS_ENABLED 미설정, GOOGLE_APPLICATION_CREDENTIALS 미설정 | 로컬 코드 기본 enabled=false. 다른 설정 공급원/ADC 자격증명까지 부재라고 단정하지 않음 |

Vercel 실제 배포 revision·Google 자격증명·DB 예문 행 및 음성 캐시 수는 이번에 조회하지 않았다. 현재 UI의 실패 원인을 실제 호출로 재현한 계획은 아니다.

01:06 KST 이후 조회에서는 CLOVA가 상주했고 `/run/intelligentai/analysis-http.env`에 `AI_ANALYSIS_CLOVA_ENABLED=true`, `AI_ANALYSIS_CLOVA_SCORING_ENABLED=true`가 남아 있었다. 워커 `/proc/.../environ` 읽기가 제한돼 실행 중 공급자 선택을 확정하지 못했다. 사용자 설명의 GPT 전환, 파일 설정, 실제 요청 경로를 구분한다.

**별도 승인된 CLOVA 종료 — 2026-09-19 01:14 KST 이후 확인:**

- CLOVA PID 20312에 SIGTERM을 보내 종료했다. 8000 포트 닫힘, GPU 목록에서 해당 프로세스 제거를 확인했다.
- GPU 전체 사용량은 38,808MiB에서 약 1,479~1,502MiB로 줄었고, 보고된 여유는 약 43,987~44,010MiB였다. 총량과 used/free 차이는 장치 예약량 등을 포함할 수 있으므로 단순 뺄셈으로 대체하지 않는다.
- 분석 API PID 41664와 scorer PID 41669는 유지됐다. `/health`는 200 `alive`였다. **이 확인은 GPT 피드백·채점 또는 전체 분석 E2E 성공을 입증하지 않는다.** 인증 없는 `/health/services`는 401이었고 인증된 준비 상태는 종료 후 확인하지 않았다.
- 재실행을 막기 위해 `start_hyperclova.py`가 `/opt/intelligentai/clova.disabled` 존재 시 모델 로드 전에 종료하도록 했다. 원본은 `start_hyperclova.py.bak.stop-clova.20260918T161419Z`로 보존했다.
- supervisor에는 CLOVA 항목이 남아 재시도를 계속하며 `backoff`로 표시된다. 이는 **임시 재실행 차단**이지 정식 서비스 제거가 아니다. `/opt` 변경과 marker가 팟 재생성·배포 후에도 복원된다는 보장은 없다.

### 3.1 TTS 구현 전 운영 정리 조건

1. 실제 분석 작업의 provider 선택, 피드백·채점·readiness의 CLOVA 의존성을 소스와 비밀값을 제외한 설정으로 확인한다. GPT 전환이 미완료면 기존 분석 복구/전환을 별도 변경으로 처리하고 TTS 공개를 보류한다. TTS 때문에 CLOVA 플래그를 일괄 false로 바꾸지 않는다.
2. 비활성 CLOVA를 지속 배포 설정에서 제거하거나 명시적 비활성 상태로 관리해 backoff를 없앤다. 분석 의존성이 남았는데 health만 성공하도록 바꾸지 않는다. 임시 marker와 launcher 변경은 정식 배포와 대조해 정리한다.
3. 현재 `supervise_services.py`는 서비스 이름을 `clova/api/analysis-worker`로 제한하고 설정을 시작 시 한 번만 읽는다. `tts` JSON 항목 추가만으로는 실행되지 않으며 supervisor 재시작은 다른 자식 서비스에도 영향을 준다.
4. 따라서 TTS는 **독립 관리 프로세스·설정·lock·상태 파일·로그**로 분리한다. 기존 supervisor를 그대로 두 개 실행하면 전역 lock/상태 파일이 충돌하므로 금지한다. 별도 관리 구현과 재부팅 복원을 준비하고, 최초 부팅 경로 등록에 분석 재시작이 필요하면 작업 drain 후 점검 시간에 적용한다. 이후 TTS 재시작은 TTS 자식만 대상으로 한다.
5. 동일 GPU의 별도 프로세스는 GPU 연산량의 강제 격리가 아니다. 평상시 scorer 1,470MiB를 최대 사용량으로 간주하지 않고 실제 음성·영상 분석 peak와 지연을 측정한다. 종료 후 RAM도 재측정한다.

## 4. 목표 호출 흐름과 공개 응답

```text
코스 화면 → AWS 발행 예문 목록 조회 → 선택된 서버 exampleId
    → GET /api/backend/api/practice-examples/{exampleId}/audio
    → AWS: JWT/사용자 상태/발행 문장 확인
    ├─ 승인 manifest와 일치하는 캐시 적중
    │    → 200 audio/mpeg → 프론트 Blob → HTMLAudioElement
    └─ 캐시 미스/승인 불일치 → 503 TTS_UNAVAILABLE (1차 운영)
```

공개 API 경로와 성공 형식은 기존 BE 계약을 유지한다. 접수형 202, 분석 status polling, SSE, 분석 결과 콜백을 도입하지 않는다. **현재 코드는 캐시 미스 시 즉시 합성한다. 새 RunPod provider의 공개 동작은 승인된 캐시만 제공하도록 의도적으로 변경한다.** 장애 응답으로 백그라운드 합성 접수를 가장하지 않는다.

사전 생성 경로: 발행 예문 snapshot → 전용 관리 도구 → quota/lease → RunPod 동기 합성 → MP3 전체 decode 검증 → 비공개 후보 캐시 저장·lease 완료 → 2인 청취 검수 → 바이트 digest 승인 → 승인 manifest 배포. 사람의 검수 동안 5분 lease를 붙잡지 않는다. 후보 바이트가 DB에 있어도 승인 manifest가 없으면 공개 reader는 제공하지 않는다. 관리 도구는 공개 사용자 API가 아니며 일반 JWT나 임의 userId=0으로 quota를 우회하지 않는다. 전용 운영 실행 권한·배치 동시성 1·중단/재개 기록을 갖는다.

성공 예시 — **아래 헤더는 목표 예시이며 실측 응답이 아니다.**

```http
HTTP/1.1 200 OK
Content-Type: audio/mpeg
Content-Length: <실제 MP3 바이트 수>
ETag: "<실제 MP3 SHA-256>"
Cache-Control: private, max-age=86400
Vary: Authorization
X-Content-Type-Options: nosniff

<MP3 binary bytes>
```

Vercel proxy를 통한 응답은 우선 현행 no-store 정책을 유지하고 Content-Length가 생략될 수 있음을 허용한다. BE 캐시와 프론트 object URL 캐시로 중복 생성·다운로드를 줄인다. 인증 응답을 CDN public 캐시로 바꾸지 않는다. ETag 304는 저장된 bytes가 있을 때만 요청/처리하고 빈 304를 Blob으로 재생하지 않는다.

TTS 장애는 기존 BE envelope와 code를 유지한다:

```json
{
  "result": false,
  "message": "예문 요청을 처리할 수 없습니다.",
  "data": null,
  "code": "TTS_UNAVAILABLE"
}
```

401은 기존 인증 갱신 후 최대 1회 재요청, 403/404는 사용자 상태·문장 접근 확인, 429는 `TTS_RATE_LIMITED`, 503은 `TTS_UNAVAILABLE`로 안내한다. 내부 timeout·대기열 포화·잘못된 오디오의 상세 사유는 request ID 로그로 구분한다. 해제 시각을 아는 일시적 제한에만 bounded Retry-After를 추가한다. 검수 미완료·설정 누락에는 재시도 시각을 약속하지 않으며 프론트 무한 자동 재시도는 금지한다.

`voice` 생략은 새 설정의 기본 음성을 사용하게 바꾼다. 새 공개 별칭은 제안값 `ko-KR-practice-v1`이다. controller의 annotation 기본값과 service의 단일 voice 검사 모두 수정 대상이다. 1차는 **선택된 provider 하나**만 활성화한다. RunPod 선택 상태에서 기존 `ko-KR-Chirp3-HD-Aoede`를 명시하면 현행 잘못된 voice 계약인 400 `VALIDATION_ERROR`로 거절하고 자동 치환하지 않는다. Google 선택 상태는 기존 voice 계약을 유지한다. 다중 provider 라우팅은 이번 범위에서 제외한다.

## 5. RunPod 내부 TTS 계약 — 신규 제안

다음은 현재 존재하는 endpoint가 아니라 구현할 계약이다. TTS 전용 HTTPS 경로/포트를 BE만 호출하며 서비스 전용 Bearer 토큰을 쓴다. 분석 callback 토큰·사용자 JWT를 재사용하지 않는다.

`POST /v1/tts/synthesize`, Content-Type `application/json`:

```json
{
  "schemaVersion": "voice-coaching.tts-request.v1",
  "requestId": "11111111-1111-4111-8111-111111111111",
  "synthesisRevision": "melo-ko-practice-r1",
  "text": "쌀과 콩을 깨끗한 그릇에 담아요.",
  "language": "ko-KR",
  "voice": "ko-KR-practice-v1",
  "speakingRate": 0.92,
  "format": "mp3"
}
```

| 필드 | 타입 / 규칙 |
| --- | --- |
| schemaVersion | 고정 문자열, 필수 |
| requestId | UUID, 필수. 캐시 키와 별개인 추적 ID |
| synthesisRevision | 필수 non-null 문자열. 예시는 임시 이름이며 실제 코드·모델·전처리·encoder manifest를 식별하는 불변 revision으로 확정 |
| text | 필수 문자열. 빈 문자열 금지, UTF-8 최대 4,500 bytes. 공개 클라이언트가 아닌 BE의 발행 문장에서 가져옴 |
| language / voice / format | 필수 allowlist. 1차 ko-KR / 지정 voice / mp3만 허용 |
| speakingRate | 필수 number. 0.92는 초기 평가 후보이며 아직 검증되지 않음. 1차 공개는 검수한 값 하나만 허용. Melo `speed`로 명시적으로 매핑하고 Google과 같은 체감 속도라고 가정하지 않음 |

추가 필드·SSML·외부 audio URL·사용자 reference voice 입력은 받지 않는다. 요청 JSON 전체 상한 32KiB, 합성 시간/출력 duration/메모리/응답 bytes에 별도 한도를 둔다.

200은 MP3 바이너리. `X-Request-Id`, `X-TTS-Revision`, `X-Audio-SHA256`, `X-Audio-Duration-Ms`를 내부 응답 헤더로 제공한다. BE는 request ID 일치, 요청한 revision 일치, 바이트로 직접 계산한 SHA-256 일치와 전체 decode를 검사한다. 길이 헤더만 신뢰하지 않고 실제 decoded duration도 대조한다. 다른 revision 요청은 합성 전 409 `REVISION_MISMATCH`로 거절한다. 200 text/html/JSON/WAV, 빈 응답, 깨진 MP3는 성공 캐시에 넣지 않는다.

모든 요청 필드는 필수·non-null이며 추가 필드, 중복 JSON 키, 잘못된 UTF-8, NaN/Infinity를 거절한다. 인증은 본문 처리·대기열 진입 전에 검사한다. 같은 requestId의 동일 canonical body는 실행 중 작업에 합류하거나 짧은 TTL의 완료 결과를 재사용하고, 다른 body는 409 `REQUEST_ID_CONFLICT`다. 이 중복 방지는 제한된 메모리/TTL 범위이며 재시작을 넘는 exactly-once 보장이 아니다. BE의 영속 lease가 1차 중복 방지이고, 전송 결과를 모르는 POST는 자동 재시도하지 않는다.

내부 오류는 `{code, requestId, retryable}` JSON. requestId는 인증 전/JSON 파싱 실패 시 null이다. 400 malformed JSON, 401 토큰, 409 revision/중복 내용 충돌, 413 본문 크기, 415 Content-Type, 422 입력, 429 대기열 포화·대기 만료, 503 모델 미준비, 504 생성 마감 초과를 구분한다. 내부 429는 공개 429 `TTS_RATE_LIMITED`, 내부 401/409/5xx·네트워크 실패는 공개 503 `TTS_UNAVAILABLE`로 매핑한다. 내부 인증 실패를 사용자 로그인 실패로 처리하지 않는다. retryable은 자동 재시도 지시가 아니며 외부 공개 오류에는 내부 경로·stack·본문을 복사하지 않는다.

`GET /health/live`, `GET /health/ready`도 **신규 제안**이다. ready는 고정 모델/언어 사전/encoder 로드와 시작 시 1회 자체 합성·decode를 확인하고 상태를 반환한다. 매 health 조회마다 합성하지 않는다. 시작/복구 중에는 live 200, ready 503이고 API가 살아 있어도 child 합성이 중단됐으면 ready를 내린다. loopback health와 원격 서비스 토큰 인증 health를 구분하고 외부에는 최소 상태·revision만 제공한다.

## 6. TTS 런타임과 자원 격리

1. 현재 팟의 별도 환경에서 **GPU 동시성 1**을 우선 성능 후보로 평가하고 같은 문장·모델의 CPU 실행을 비교한다. GPU 여유 약 43GiB는 종료 직후 관측이며 TTS 전용 예약이 아니다. 분석 단독, TTS 단독, 두 서비스 동시 실행의 peak RSS/VRAM·memory pressure·분석 지연·OOM을 비교한다. 자원 간섭을 통제할 수 없으면 CPU 운영 또는 별도 팟을 선택한다. 캐시 강제 삭제는 하지 않는다.
2. Melo 코드 commit, 한국어 checkpoint revision/hash, 언어 전처리 자산, encoder와 라이브러리를 manifest로 고정한다. 요청 시 Hugging Face 다운로드나 pip 설치를 하지 않는다.
3. 공식 API의 `TTS(language='KR', device='cuda:0' 또는 'cpu')`, `speaker_ids['KR']`, `tts_to_file(..., speed=...)`를 adapter에 감싼다. 공식 설치 문서는 Ubuntu 20.04/Python 3.9에서 시험했다고 명시한다. 이를 재현 기준으로 삼되 운영용 Python 버전은 의존성 빌드·보안 지원·GPU 호환 검증 후 고정한다. 근거 없이 Python 3.11이나 기존 scorer 3.12를 필수로 못 박지 않는다. [설치 문서](https://github.com/myshell-ai/MeloTTS/blob/main/docs/install.md), [실제 API](https://github.com/myshell-ai/MeloTTS/blob/main/melo/api.py)
4. 초기 한도는 합성 동시성 1, **대기 슬롯 1·대기 최대 2초**, CPU thread 2다. 이전의 대기 4건은 최대 합성 20초와 짧은 대기 목표에 맞지 않아 축소했다. 대기 중 마감이 지나면 제거하고 합성하지 않는다. GPU 상한은 분석 peak 측정 후 정하며 PyTorch allocator 설정만으로 모든 CUDA 할당·연산 간섭이 격리된다고 주장하지 않는다. CPU thread 설정도 cgroup CPU quota를 대신하지 않는다.
5. 생성 WAV를 모델 고유 sampling rate로 받은 뒤 명시적 MP3 96kbps mono로 변환한다. 영상 분석용 16kHz를 TTS에 무조건 강제하지 않는다. 출력 최대 120초·2,000,000 bytes로 제한하며 상한 초과를 잘라 성공으로 반환하지 않는다. 긴 예문은 발행 전 길이 검증에서 분리한다.
6. TTS API는 신규 후보 포트 8082를 사용하고 적용 직전 비어 있는지 재확인한다. RunPod HTTP 포트 매핑/HTTPS proxy 또는 기존 reverse proxy의 전용 TTS location으로 연결한다. 기존 분석 8080·CLOVA 8000을 교체하지 않는다. loopback 경유와 public proxy 경유 중 실제 Pod 매핑에 맞는 경로를 확정하고 UI/debug 서버는 노출하지 않는다.
7. TTS는 3.1절의 별도 관리 프로세스와 전용 로그·사용자·작업 디렉터리를 갖는다. 같은 RunPod 내부에서 Docker-in-Docker나 systemd를 사용할 수 있다고 가정하지 않는다. 빌드 환경에서 만든 고정 실행 자산을 팟 전용 환경에 배치하는 경로를 우선 설계한다. 모델은 실제 영속 마운트 확인 후 저장하고 overlay 여유 공간은 설치 중 peak를 포함해 관리한다.
8. API 프로세스와 모델을 가진 합성 child를 분리한다. 스레드의 HTTP timeout만으로 CUDA 연산이 중지된다고 가정하지 않는다. 합성 deadline 초과 시 해당 TTS child만 종료·회수하고 임시 파일을 정리한 뒤 재기동·warm-up한다. 복구 중 ready 503, 새 작업 접수 금지다. 종료 후 GPU 할당 해제와 다음 요청 회복까지 검증한다.
9. TLS/서비스 토큰은 AWS→RunPod 경계에서 검증하고 redirect를 따라 다른 호스트로 토큰을 보내지 않는다. 신규 경로만 nginx 검증 후 reload하며 기존 분석 location/Host/SNI는 유지한다. upstream URL·포트가 미확정이면 문서의 후보를 실제 주소처럼 배포 설정에 넣지 않는다.

CosyVoice 연구 코드는 대안으로 보존한다. 현재 연구 설정 `r01_multi_speaker_candidates_v5.yaml`에 특정 prompt에서 종료 토큰을 내지 못했던 관측이 있으므로 단지 파일이 있다는 이유로 사용자용 즉시 합성 서버로 전환하지 않는다. Melo의 한국어 품질이 부족하면 별도 품질/지연 평가로 대안을 선택한다.

## 7. 백엔드 변경

- `ExampleSpeechSynthesizer` 경계를 유지하고 `RunPodTtsSynthesizer`를 추가한다. provider 조건부 bean으로 Google/RunPod가 중복 주입되지 않게 한다.
- 제안 설정: `EXAMPLE_TTS_PROVIDER=disabled|google|runpod`, `EXAMPLE_TTS_BASE_URL`, `EXAMPLE_TTS_API_TOKEN`, `EXAMPLE_TTS_VOICE`, `EXAMPLE_TTS_REVISION`, timeout/최대 bytes, 승인 manifest 위치·digest. 신규 provider 미설정이면 기존 CHIRP enable/disable 설정을 유지한다. 명시적 disabled는 합성을 막고, RunPod의 필수 설정 누락은 해당 TTS 기능만 사용 불가로 처리한다. provider/voice/revision 상태를 비밀 없이 진단 가능하게 한다.
- `ExampleAudioService.DEFAULT_VOICE` 하드코딩을 voice profile 설정으로 분리한다. 합성 fingerprint는 provider·모델 revision·voice·rate·전처리 revision·MP3 codec 설정까지 포함한다.
- RunPod 캐시 키는 SHA-256(canonical JSON `{exampleId, exampleRevision, textSha256, synthesisFingerprint}`)로 정의한다. 문자열 단순 연결의 모호성을 없애고 Google 키와 namespace를 분리한다. Google의 기존 키 조회는 보존한다. RunPod fingerprint에는 모델뿐 아니라 seed/noise 설정·전처리·encoder·검증한 rate·필요 시 실행 장치 차이도 포함한다.
- 기존 BYTEA/64자 키 저장을 재사용하되 **승인 정보가 현재 DB에 없다는 점을 보완**한다. 1차는 root/배포 주체만 갱신 가능한 불변 승인 manifest에 example ID/revision·text hash·fingerprint·MP3 digest·검수 식별자/시각을 기록한다. 읽기 경로에서 manifest와 캐시 바이트 digest가 모두 일치해야 제공한다. 지속적인 대량 발행이 필요하면 별도 approval 테이블 migration으로 전환한다. 지금 단계에서 “승인 기능까지 DB 변경 없이 이미 가능”하다고 해석하지 않는다.
- 기존 외부 호출 밖 트랜잭션·lease 소유자 검증을 유지한다. 관리 배치는 공개 사용자 quota와 별도 정책으로 제한하고 중복 생성 lease는 공유한다. 공개 RunPod 요청은 캐시 미스로 합성을 시작하지 않는다. 검수 대기 503에는 곧 준비된다는 뜻의 짧은 Retry-After를 붙이지 않는다. 재생 요청·관리 합성의 rate limit과 오류를 구분한다.
- 현재 `ExampleAudioService.mp3()`는 ID3/프레임 선두만 검사한다. 신규 adapter는 응답을 최대 2,000,001바이트까지만 읽고 상한을 적용한 뒤 ffmpeg 등 고정 decoder로 **끝까지** 해독해 빈 음성·duration·sample rate·channel을 확인한다. EC2의 기존 decoder 설치를 확인하고 별도 프로세스 timeout/동시성 상한을 둔다. 로그에 원문/임시 내부 경로를 노출하지 않는다.
- 관리 합성의 총 BE deadline은 30초 제안이다. 아래 예산은 하나의 monotonic deadline에서 남은 시간을 차감하며 라이브러리 connect/read timeout을 순서대로 더한 무제한 실행이 되지 않게 한다. 공개 캐시 재생은 합성 없이 더 짧은 경로로 처리한다.
- RunPod enable 전에 대상 예문 전부를 생성·검수·승인하고 실제 캐시를 확인한다. 캐시가 사라지면 503으로 드러내고 승인 manifest의 digest를 만족하는 **기존 검수 바이트**를 백업에서 복원한다. 같은 텍스트를 재합성했다고 자동 승인하지 않는다.

| 관리 합성 단계 | 제안 상한 |
| --- | --- |
| BE 권한·snapshot·lease / 마지막 저장 | 합계 1초 |
| 연결·TLS | 2초 |
| RunPod 대기 | 2초 |
| 모델 합성 | 20초 |
| MP3 encoding | 1초 |
| 전송·BE 전체 decode 검증 | 합계 3초 |
| 오차 예산 | 1초 — 총 30초 |

이는 측정 전 예산이다. 4,500 bytes 입력 모두가 통과한다고 약속하지 않는다. 실제 발행 문장 길이 분포를 조사하고 긴 문장은 공개 전에 콘텐츠 revision으로 분리하거나 별도 오프라인 생성한다. 출력 120초/2MB 상한을 충족해도 생성 시간 초과면 실패다. 향후 공개 동기 합성을 허용할 경우에만 FE 35초·proxy 60초와 실제 Vercel 실행 한도를 함께 검증한다. 자동 재시도는 없고 401 refresh 1회도 같은 사용자 요청 deadline 안에서 처리한다.

## 8. 프론트와 예문 데이터 연결

1. `CourseLesson`의 정적 예문 대신 BE `.../practice-examples` 응답의 `id/text/practiceContentId/revision`을 사용한다. 화면 문장, 합성 문장, 녹음 분석 대본이 같은 발행 revision을 가리켜야 한다.
2. `consonant-1` 같은 FE ID가 DB에도 있다고 가정하지 않는다. ID·문장 hash·교육 revision 매핑을 조사한다. DB에 없는 예문은 별도 정식 발행 절차로 등록한다. 기존 DB 제약은 세트당 정확히 5개이며 발행 예문 수정 대신 새 revision이 필요하다. TTS GET 안에서 콘텐츠를 자동 생성하지 않는다.
3. `api.examples.getAudioBlob` 같은 바이너리 메서드를 추가해 기존 JWT·refresh 단일 실행 로직을 재사용한다. 성공은 Blob, 오류만 JSON으로 파싱한다. MIME/비어 있지 않음/크기를 검사한다.
4. 주 호출을 `/api/backend/api/practice-examples/{exampleId}/audio`로 통일한다. **기존 `/api/tts`는 같은 배포에서 즉시 JWT 필수 adapter로 바꾸지 않는다.** 현재 player는 fetch에 Authorization을 넣지 않고 정적 ID를 보내므로 그러한 변경은 호환성을 깨뜨린다. 구 player/route는 전환 기간 유지하고 신규 player만 인증된 BE 경로를 쓴다. DB ID 매핑과 신규 player 성공 확인 후 구 route 사용량을 확인해 폐기한다. 구 route를 당장 보호해야 한다면 인증된 caller를 먼저 배포하는 별도 순서를 세운다.
5. object URL 캐시는 example ID만이 아니라 예문 revision·voice profile 단위로 묶고 용량/LRU 상한을 둔다. 로그아웃/페이지 종료에 revoke한다. 문장 변경 시 AbortController로 이전 요청을 중단하고 늦게 온 결과가 새 문장에 재생되지 않게 한다.
6. 버튼은 “예시 발음 듣기”, 대기는 “예시 음성 준비 중…”으로 바꾼다. 401 로그인, 404 등록되지 않은 예문, 429 잠시 후 재시도, 503 준비/연결 문제, 브라우저 재생 실패를 구분한다. 서버 실패를 자동 기기 음성 재생으로 숨기지 않고 “기기 음성으로 듣기”를 명시적인 별도 선택으로 제공한다.

일반 연습의 `ReferencePlayer`도 지원하려면: 승인된 TTS 결과를 AWS가 별도 객체 prefix에 저장하고 `reference_audios`에 `speakerType=TTS`로 등록한다. 기존 아나운서 primary는 보존한다. playback URL reader는 실제 presigned URL을 발급하게 하고 expiresAt을 실제 만료와 맞춘다. presigned URL은 만료 전에 재발급한다. RunPod에 기존 녹음 reader보다 넓은 S3 쓰기 권한을 주지 않는다. 목록 캐시 30분도 발행 시 무효화한다. 이 작업은 코스 예문 MP3 경로와 구별해 2차 적용한다.

## 9. 검증·전환 순서

| 단계 | 산출물 / 완료 조건 |
| --- | --- |
| 0. 기존 분석 상태 정리 | CLOVA 임시 차단/backoff·GPT 실제 의존성·영속 복원 확인. 새 TTS로 기존 장애를 가리지 않음 |
| 1. 데이터·계약 확정 | 실제 Vercel revision/설정, DB 예문 매핑, 프로토콜·voice·revision·binary·승인 manifest 규칙 확정. 임의 문장/ID 생성 금지 |
| 2. TTS 전용 실행 자산 | 모델/전처리/encoder 고정, 자산 사전 다운로드, GPU/CPU 비교, live/ready 분리, child timeout·메모리 회수·과부하 종료 확인 |
| 3. 한국어 청취 QA | 제공할 모든 예문에 대해 원문 누락·첨가·반복·끝 잘림, 평/경/격음·받침·연음·숫자 읽기 검토. 억양/강세 예문은 해당 대비가 실제로 들리는지 별도 확인. 미통과 문장은 게시하지 않음 |
| 4. BE 계약 검증 | MP3 전체 decode, MIME/빈 응답/bytes 초과, revision/digest 불일치, 401/429/503/timeout, 관리 경로의 동일 키 동시 요청 1회 합성, 공개 캐시 미스에서 합성 0회, 미승인 바이트 제공 금지, ETag/304, 권한 확인 |
| 5. FE 실제 재생 | Chrome/Android/iOS Safari에서 클릭→음성→종료/정지/재생, 토큰 만료, 문장 전환 경합, 오류 JSON, 304, object URL 정리 확인 |
| 6. 제한 활성화 | TTS 독립 manager·프록시 기동 → 동거 부하 평가 → BE provider 기존값 유지 상태 배포 → 관리 합성/검수/승인 캐시 준비 → 대상 제한 flag 구현 후 활성화 → FE 전환. 이 flag는 현존 기능이 아니라 구현 대상이다. 기존 분석 claim/heartbeat/result 영향 확인 |
| 7. 확대/rollback | FE 전환 flag를 먼저 내리고 검증된 이전 provider/voice/manifest/cache 조합으로 복구. 검증된 Google이 없으면 명시적 TTS 사용 불가로 복구. TTS만 중지하며 CLOVA를 자동 재시작하지 않음. 기존 캐시와 승인 바이트 보존 |

성능 수치는 **목표치**다: 예문 10~100자 기준 warm 합성 p95 5초 이하, BE 캐시 적중 p95 500ms 이하, FE 클릭→재생 캐시 적중 p95 2초 이하. cold 모델 준비 시간은 별도 보고한다. 초기 반복 측정은 최소 100건이며 클라이언트 동시 요청 1/2/4를 주더라도 모델 실행은 1개로 제한한다. 대기/429 비율과 성공 latency를 함께 보고해 실패를 빼고 속도만 좋게 만들지 않는다. 실제 발행 긴 예문도 별도 검사한다.

동거 공개 조건은 사전 합의할 초기 기준으로 **분석 단독 대비 동시 부하 분석 p95 증가 10% 이내**, OOM/lease 만료/누락 callback 0건을 제안한다. 음성·영상 및 목표 분석 동시성별로 baseline과 비교한다. 표본·네트워크 변동이 크면 확정 판정을 보류하고 측정 횟수를 늘린다. 단순 GPU 메모리 여유나 `/health` 200으로 이 조건을 대신하지 않는다.

공개되는 오디오는 한국어 검토자 2인이 문장·학습 목표와 대조하고 **실제 청취한 MP3 digest**에 승인한다. 두 검토가 끝나기 전 해당 음원은 비공개다. STT나 기존 Seungun 점수만으로 TTS 정답을 인증하지 않는다. 상승/하강 억양·강세를 엔진이 제어하지 못하면 프롬프트를 임의 조작해 성공으로 표시하지 않고 검수된 별도 음원을 사용하거나 해당 항목 전환을 보류한다.

로그/지표: request ID, example ID/revision, provider/model revision, cache hit, queueMs, synthesisMs, encodeMs, totalMs, bytes, duration, HTTP status, 제한된 오류 code. 토큰·원문 전체·음성 데이터는 로그에 기록하지 않는다.

## 10. 구현 파일과 근거

- BE 현재: [ExampleAudioService](../../src/main/java/org/example/voice/practiceexample/application/ExampleAudioService.java), [합성 port](../../src/main/java/org/example/voice/practiceexample/domain/port/ExampleSpeechSynthesizer.java), [Google adapter](../../src/main/java/org/example/voice/practiceexample/infrastructure/GoogleChirpSynthesizer.java), [controller](../../src/main/java/org/example/voice/practiceexample/controller/PracticeExampleController.java), [캐시/lease](../../src/main/java/org/example/voice/practiceexample/infrastructure/ExampleAudioPersistence.java), [V25](../../src/main/resources/db/migration/V25__add_practice_examples_and_audio.sql).
- BE 추가/수정 예정: `RunPodTtsSynthesizer`, TTS typed properties/voice profile, 바이너리 전체 decode 검증, 선택적 provider 구성, 승인 manifest reader, 관리 사전 생성/승인 도구, 전환 flag, 해당 계약 테스트와 API 문서.
- FE 추가/수정 예정: `src/lib/api/client.ts` 바이너리 요청, API types/remote, `course-lesson.tsx`, `tts-practice-player.tsx`, 전환 flag. `/api/tts`는 전환 기간 보존 후 사용량 확인·폐기 대상이다. 일반 기준 음성은 `reference-player.tsx` 별도 단계.
- AI 추가 예정: `src/voice_coach/tts/`, `scripts/serve_example_tts.py`, 전용 `deploy/example-tts/` 실행 자산/독립 manager/manifest/운영 문서, TTS 요청·오류 schema. 모두 제안 경로다. 기존 `deploy/runpod/supervise_services.py`의 서비스 allowlist·전역 lock·상태 경로·설정 재로딩 제약을 회피하는 독립 설계를 검증한다.

이번 재검토는 현재 로컬 코드·공식 Melo 문서와 직전 CLOVA 종료 관측을 대조해 **이 문서만 수정**했다. 신규 원격 진단·TTS 설치·합성·push·배포·서비스 재시작은 하지 않았다. 운영 리비전/DB 매핑/실제 GPT 의존성/영속 마운트/자원 peak는 구현 착수 시 확인할 항목이다.
