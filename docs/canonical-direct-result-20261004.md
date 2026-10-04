# v5 결과 직접 공개

구현·컴파일 확인. 이 변경의 운영 배포와 시간 재측정은 아직 하지 않았다. DB migration/result v5/view v2 변경 없이 기존 `analysis.canonical.delivery.enabled=true`에 적용한다.

## 사용자 대기 경로

RunPod PREFLIGHT → 결과 JSON POST → AWS 메모리 공개 → 대기 중인 결과 GET 응답 → FE 렌더링.

근거 전체 인계, EBS fsync, DB 저장, B2 archive는 사용자 대기에서 제외한다. RunPod 의미 검증을 AWS에서 다시 실행하지 않는다. 공개 GET은 현재 사용자/선택 녹음/request/execution/worker·원본 digest·revision을 한 JOIN으로 확인한다. JWT 세션 확인은 별도다. 결과 DB 쓰기나 JPA 그래프 조회를 기다리지 않지만 DB 전체 장애에서 소유권 확인 없이 공개하는 계약은 아니다.

## API

- `POST /api/internal/ai/analyses/{analysisId}/handoffs/result`: 기존 callback Bearer와 X-Worker-Instance-Id, 기존 result v5 JSON 최대 1 MiB. SQL/디스크 저장 없이 `202 {"status":"PUBLISHED","eventId":"<uuid>","payloadSha256":"<RFC8785 sha256>"}` 반환.
- 같은 execution의 동일 identity/digest는 멱등, 다른 값은 409. 후속 full handoff의 projection도 일치해야 한다.
- 메모리 한도 128건/원본 32 MiB/1시간. 포화·기능 OFF는 503. 만료는 메모리에만 적용하며 근거 파일을 삭제하지 않는다.
- **PUBLISHED는 영속 수신 ACK가 아니다.** RunPod는 원본 handoff를 계속 보내고 기존 RECEIVED 이후 실행 책임을 넘긴다. 첫 handoff는 사전 GET 없이 멱등 POST, 불확실한 재전송만 GET으로 확인한다.
- publication 실패/구 BE이면 RunPod는 전체 교환 최대 2초 후 기존 인계로 진행한다. preview 유실은 RunPod 원본 인계/영속 큐로 복구한다. preview만으로 timeout sweeper 제외나 학습 완료를 승인하지 않는다.
- `GET /api/v3/analyses/{id}?waitSeconds=8`: 0~8, 기본 0. 이벤트 또는 제한 시간 후 동일 view v2 응답. 구 서버가 query를 무시해도 즉시 조회 가능.

이벤트 등록 후 최초 조회하여 알림 경합을 막고, 이벤트 후 현재 소유권을 다시 확인한다. 기다리는 동안 servlet thread/DB transaction을 점유하지 않는다. 대기 최대 128개, 최종 조회 실행기 4개/유한 큐. async response dispatch에서도 JWT 세션을 확인한다. 기존 full handoff 공개 준비도 이벤트로 알린다.

저장 전 기존 allowlist projection을 SAVING/actions=false/RESULT_PERSISTENCE_PENDING으로 공개한다. 저장 후 SAVED와 기존 완료 정책을 적용한다. 기존 status API는 호환용이며 메모리 publication 발견의 필수 단계가 아니다.

delivery ON은 delivery/handoff worker의 Python 의미 재검증과 readiness의 독립 verifier 설치 검사를 생략한다. 현재 실행·전송 hash·영속 저장 재시도는 유지한다. OFF인 기존 경로는 종전 검증을 유지한다.

## 배포·확인

BE develop PR/자동 배포 → RunPod 코드 → FE dev/main/Vercel 순서. 부분 배포는 기존 경로로 호환되지만 지연 단축은 세 곳이 반영돼야 한다. 운영 도구의 FE asset/AI revision pin은 실제 묶음과 맞춰야 한다. 미저장 큐가 있으면 delivery를 끄거나 디렉터리를 교체하지 않는다. 단일 EC2/BE 인스턴스 전용이며 분산 캐시가 아니다.

Java 21 compileJava/compileTestJava 확인. 자동 회귀 테스트·실제 녹음 QA는 수행하지 않았다. 개발자 확인 항목: publication→첫 view→DB COMMITTED→B2 순서, 취소/재시도/로그아웃, publication 후 재시작, 메모리 포화/실패의 기존 경로 복구. 기존 4.95+4.54초 전부 절감을 주장하지 않으며 새 수치는 배포 후 측정한다.
