# 예문 TTS 운영 연결 및 자동 생성

최종 관측: 2026-09-19 16:46 KST / 07:46 UTC. 사용자가 새 TTS 전용 토큰의 AWS 전송·저장과 nginx 경로 적용을 명시적으로 승인하여 실행했다. 비밀값은 이 문서에 없다.

## 적용 결과

- 운영 BE JAR: `87d5a9b6fb960aaca569eff16a9bbcb72c9d7113` (PR 85). 이번에는 JAR 교체 없이 환경 설정 추가와 백엔드 재시작을 수행했다.
- RunPod 토큰을 SSH 프로세스 내부에서 읽고 AWS에 암호화 SSH로 전달했다. 토큰 값은 로그·명령줄 인자·로컬 파일에 기록하지 않았다.
- AWS `/etc/voice-coaching-tts/token`: root 소유, `0600`. 백엔드가 읽는 `/etc/alpha-backend.env`에도 전용 설정으로 등록했다. 기존 환경 파일은 `.before-tts-<UTC>` 백업으로 보존했다.
- `/etc/nginx/conf.d/ai.voice-coaching.site.conf`에 아래 exact location만 추가했다. 기존 설정을 root 전용 백업으로 보존하고 `nginx -t` 성공 후 reload했다.
- `alpha-backend`, `nginx`, `voice-coaching-tts-tunnel` 모두 active. 백엔드 관리 health `UP`, 기존 RunPod 분석 `/health`는 `alive`였다. 전체 분석 E2E·동시 부하 검증을 뜻하지 않는다.

## 실제 연결

```text
예문 INSERT → example_tts_outbox → AWS ExampleTtsWorker
 → HTTPS ai.voice-coaching.site/v1/tts/synthesize (전용 Bearer)
 → nginx exact location → AWS 127.0.0.1:18082
 → voice-coaching-tts-tunnel SSH → RunPod 127.0.0.1:8082
 → Melo Korean → MP3 → AWS 전체 decode·digest 검증 → DB 캐시
 → 검수 승인 이후 공개 예문 audio GET → FE Blob 재생
```

```nginx
location = /v1/tts/synthesize {
    client_max_body_size 32k;
    proxy_pass http://127.0.0.1:18082;
    proxy_connect_timeout 2s;
    proxy_read_timeout 25s;
    proxy_send_timeout 5s;
    proxy_next_upstream off;
}
```

공개 HTTPS 무인증 POST는 401, 인증된 터널 readiness는 200이었다. 실제 Java 워커가 HTTPS 경로로 5건을 생성했으므로 인증·프록시·터널·합성·응답 검증·DB 저장을 함께 확인했다. RunPod health 경로를 nginx에 추가로 공개하지 않았다.

## 실제 운영 설정 이름

`@ConfigurationProperties("example.tts")`의 직접 환경 바인딩에 맞춰 camel-case 속성 내부에는 underscore를 넣지 않았다. 계획에 등장한 `EXAMPLE_TTS_WORKER_ENABLED`, `EXAMPLE_TTS_BASE_URL`, `EXAMPLE_TTS_API_TOKEN`을 그대로 복사하지 않는다.

| 이름 | 값 또는 보관 규칙 |
| --- | --- |
| EXAMPLE_TTS_PROVIDER | runpod |
| EXAMPLE_TTS_WORKERENABLED | true |
| EXAMPLE_TTS_BASEURL | https://ai.voice-coaching.site |
| EXAMPLE_TTS_APITOKEN | 전용 비밀값, 문서·로그 출력 금지 |
| EXAMPLE_TTS_REVISION | melo-ko-practice-r1 |
| EXAMPLE_TTS_FINGERPRINT | 9474d12bafea63f2344e0e2e48eccbf57a512a9be717832248eab6397f8f7f0b |
| EXAMPLE_TTS_VOICE | ko-KR-practice-v1 |
| EXAMPLE_TTS_SPEAKINGRATE | 0.92 |
| EXAMPLE_TTS_DECODER | /opt/voice-media/ffmpeg/n8.1.2-51-g7ba069f4f1/bin/ffmpeg |
| EXAMPLE_TTS_PROBE | /opt/voice-media/ffmpeg/n8.1.2-51-g7ba069f4f1/bin/ffprobe |

토큰 교체는 RunPod `/workspace/voice-coaching-tts/secrets/token`, AWS 전용 token 파일, 백엔드 환경의 세 위치를 일치시켜야 한다. 값은 프로세스 내부에서만 취급하고 적용 후 서비스별 인증을 확인한다. 기존 분석 callback 토큰과 SSH 키는 재사용하지 않는다.

## DB 결과

코스 301, 연습 단계 303, 교육 revision ID 3, 예문 revision 1. 기존 등록 예문 5개를 backfill했다. outbox 처리 5건, 모든 작업 `GENERATED`, attempt 1, 오류 없음. 캐시 바이트의 SHA-256과 작업 digest가 5건 모두 일치했다.

| 예문 ID | 콘텐츠 ID | 음성 길이 ms | MP3 bytes |
| --- | --- | --- | --- |
| course301-step303-r1-1 | 3 | 4396 | 53648 |
| course301-step303-r1-2 | 4 | 3735 | 45498 |
| course301-step303-r1-3 | 5 | 3862 | 47065 |
| course301-step303-r1-4 | 6 | 3735 | 45498 |
| course301-step303-r1-5 | 7 | 3932 | 48005 |

위 길이는 생성 소요시간이 아닌 재생 길이다. 새 예문 INSERT는 V30 trigger로 outbox에 기록되며 worker가 유효한 발행 예문을 profile별 작업으로 만든다. 이번 운영 확인은 기존 5건 backfill이며 검증만을 위한 추가 운영 예문은 생성하지 않았다. 새 INSERT/lease/중복 동작은 이전 별도 PostgreSQL 검증에서 확인했다.

**검수 기록은 0건이다.** 공개 reader는 실제 바이트 digest에 대해 서로 다른 검토자 2인의 승인이 있고 거절이 없는 경우만 제공한다. 현재 공개 audio GET은 승인 캐시가 없어 사용 불가 상태이며, 생성 완료를 청취 품질 통과나 사용자 재생 성공으로 보고하지 않는다. 공개 GET은 합성을 시작하지 않는다.

## 운영·복구

- 백엔드 재시작 전 진행 중 분석을 확인한다. 이번 조회에는 PROCESSING이 없고 기존 PENDING 1건이 남아 있었다.
- 자동 생성 중지는 `EXAMPLE_TTS_WORKERENABLED=false`로 설정하고 백엔드를 통제된 절차로 재시작한다. DB jobs/cache/검수 기록은 보존한다.
- 전체 원복은 이번 `.before-tts-<UTC>` 환경·nginx 백업을 현재 변경과 비교해 복원한다. 그 이후 다른 운영 변경을 덮어쓰지 않는다. nginx 검증 후 reload, 백엔드 health를 확인한다. 검증되지 않은 Google fallback을 성공으로 표시하지 않는다.
- 터널 서비스와 키는 `/etc/systemd/system/voice-coaching-tts-tunnel.service`, `/etc/voice-coaching-tts/tunnel_ed25519`, 전용 known_hosts로 관리한다. 포워딩은 RunPod loopback 8082로 제한된다.
- TTS 장애 시 분석 nginx location이나 CLOVA를 변경하지 않는다. TTS worker의 인증/revision 오류는 자동 반복 대신 중지 상태가 되므로 설정을 수정한 후 재시작한다.
- RunPod 독립 manager의 팟 재생성 후 startup 복원, 분석 동시 부하, 한국어 청취 검수와 로그인 브라우저 재생은 아직 추가 확인 대상이다.

구현 정본: [배포 자산](../../deploy/example-tts/README.md), [V30](../../src/main/resources/db/migration/V30__automate_example_tts.sql). 이번 문서 추가는 별도 코드 재배포를 뜻하지 않는다.
