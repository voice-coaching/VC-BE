# DB 예문 전환·TTS 설치 진행 기록

2026-09-19 KST. 이 문서는 이전 계획과 실제 실행 결과를 구분한다. 비밀값은 포함하지 않는다.

## 완료한 운영 변경

- FE PR [23](https://github.com/voice-coaching/VC-FE/pull/23), [24](https://github.com/voice-coaching/VC-FE/pull/24) merge 및 Vercel Production 성공.
- 운영 FE revision: `dda4cf9104f41c151189fde486dd37294f6dc07e`. `/class/pronunciation` HTTP 200 확인.
- 기존 공개 코스 301(`받침 발음 기초`), 녹음 단계 303, 교육 revision ID 3에 기존 받침 예문 5개를 한 트랜잭션으로 등록.
- 예문 ID `course301-step303-r1-1`~`5`, 콘텐츠 ID 3~7, 예문 revision 1. 기존 콘텐츠/세션은 수정하지 않았다.
- 다른 여섯 정적 예문 세트는 해당하는 실제 공개 코스가 없어 임의 코스를 생성하지 않았다. 원문은 FE `docs/migration/legacy-practice-examples.ts`에 보존.
- RunPod에 `/opt/voice-coaching-tts/venv`를 분리 설치했다. 기존 scorer/API Python 환경은 변경하지 않았다.
- Melo code `209145371cff8fc3bd60d7be902ea69cbdb7965a`, Korean checkpoint `0207e5adfc90129a51b6b03d89be6d84360ed323`, GPU `cuda:0`, rate 0.92.
- 독립 manager와 TTS API `127.0.0.1:8082` 기동. 모델/전처리 자산을 준비한 후 런타임 다운로드를 비활성화했다.
- EC2에 전용 SSH 터널 서비스를 설치했다. 키는 EC2에서 생성하고 RunPod에서는 `127.0.0.1:8082` 포워딩만 허용하며 원격 명령을 금지했다. 기존 SSH 키는 보존했다.

## 구현·검증

BE 배포 완료: `87d5a9b6fb960aaca569eff16a9bbcb72c9d7113.jar`, 서비스 active, 내부 health UP. Flyway V30 성공, 예문 5건·outbox 5건·생성 jobs 0건을 운영 DB에서 읽기 전용으로 확인했다. worker 설정은 미설정(기본 false)이다.

BE 최종 배포 PR: [85](https://github.com/voice-coaching/VC-BE/pull/85), 브랜치 `feat/example-tts-production`.
PR 84는 기존 연결 브랜치의 무관한 문서/영상까지 포함되어 닫고, 최신 develop에서 TTS 커밋만 가져온 PR 85로 대체했다. 공개 이력은 재작성하지 않았다.

V30은 예문 INSERT outbox, profile별 작업, lease, 생성 결과, digest에 연결된 검수 승인을 추가한다.
워커는 별도 executor에서 순차 합성하며 공개 GET은 승인 캐시만 읽는다. 기본 worker는 비활성이다.

- 프론트 DB 예문/API 테스트 20개, TypeScript, production build 통과. 최신 운영 피드백 화면을 병합해 보존.
- 백엔드 Java 컴파일·bootJar 성공. 임시 PostgreSQL migration/outbox/lease/digest 테스트 3개와 공개 음성 접근·미승인 차단 테스트 2개 통과. 최종 GitHub CI 성공. 로컬 Gradle HTML 보고서 충돌은 `--no-problems-report`로 제외해 정상 종료 확인. 기존 전체 테스트의 임시 디렉터리 접근 실패 6건은 정상 권한 재실행에서 통과.
- TTS API: 인증 없는 401, revision 오류 409, 정상 MP3 200·SHA-256 일치, 동일 요청 재사용, 다른 본문의 같은 요청 ID 409 확인.
- 한국어 약 4.4초 음성: 첫 합성 4.656초, 후속 세 번 0.309~0.461초. HTTP/MP3 포함 단일 호출 0.798초.
- 단독 부하: 동시성 1에서 100/100 성공·p95 706ms, 동시성 2에서 100/100 성공·p95 1245ms.
- 동시성 4의 연속 burst는 2/100 성공·98/100 HTTP 429. 작은 대기열의 거절을 제외하고 성공률이 높다고 해석하지 않는다.
- TTS 모델 child 강제 종료 시 ready 503 → 17초 후 ready 200 복구 확인.
- TTS 상주 후 GPU used 2682MiB/free 42807MiB. 기존 분석 `/health` alive 확인. 분석/TTS 동시 부하 E2E 검증은 아니다.

## 이전 보류 기록 (아래 최신 적용 기록으로 갱신)

1. BE 배포와 V30/outbox 검증 완료. 워커 활성화 전 상태를 유지한다.
2. 새 전용 TTS 토큰의 RunPod→AWS 전송·비밀 파일 저장 및 nginx의 신규 TTS location 적용은 자동 승인 검토가 명시적 토큰 전송 승인 부족으로 거절했다. 사용자에게 별도 승인을 요청한 상태다. 토큰은 출력하지 않았다.
3. 승인 후 HTTPS→전용 SSH 터널→TTS 경로를 검증하고 worker를 활성화한다. DB의 기존 5개 예문 backfill과 새 예문 자동 생성 결과를 확인한다.
4. 실제 DB에 저장된 후보 MP3를 청취 검수하고 해당 digest에 승인 기록을 추가한다. 검토자 승인을 만들어내지 않는다. 아직 사용자용 승인 음원은 없다.
5. RunPod 재생성 시 독립 환경/manager 복원 startup hook과 실제 분석 동시 부하, 사용자 로그인 상태의 브라우저 오디오 재생을 추가 검증한다.

AWS 토큰/프록시 활성화가 완료되지 않았으므로 **전체 자동화가 운영에서 완료됐다고 판단하면 안 된다.**

## 승인 후 적용 완료 — 2026-09-19 16:46 KST

사용자가 TTS 전용 토큰의 AWS 전송·저장과 nginx 경로 적용을 명시적으로 승인했다. 토큰을 AWS root 전용 0600 파일과 백엔드 운영 설정에 등록했고, nginx exact location을 검증 후 reload했다. 백엔드 TTS worker를 활성화하고 재시작했으며 health UP을 확인했다.

예문 5건 모두 attempt 1에 GENERATED, outbox 5건 처리 완료, DB 캐시 바이트 SHA-256 5건 일치. 검수 기록은 0건으로 공개 음성 승인은 아직 대기 상태다. 기존 RunPod 분석 health alive를 확인했다. 앞선 토큰/프록시 승인 대기 설명은 과거 기록이다.

설정 이름·경로·검증 결과·복구 절차는 [TTS 운영 연결](example-tts-operations-20260919.md)에 기록했다. FE와 intelligentAI 작업 체크아웃의 docs에도 인계 문서를 추가했다. 팟 재생성 복원, 분석 동시 부하와 실제 청취·브라우저 검증은 남아 있다.
