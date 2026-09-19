# 발음 코칭 아키텍처 실행 기록

2026-09-18. [설계 계획](pronunciation-coaching-llm-redesign-plan-20260918.md)의 첫 구현 묶음.
**로컬 구현·검증·push·Draft PR 완료. 병합·운영 배포·실제 피드백 품질 검증은 미완료.**

## 검토 대상

세 저장소의 브랜치 이름은 `feat/evidence-based-coaching`이다. 기존 작업 디렉터리와 사용자 파일은 보존했다.

| 저장소 | PR / 기준 브랜치 | 최종 커밋 |
| --- | --- | --- |
| intelligentAI | [#14](https://github.com/voice-coaching/intelligentAI/pull/14), agent/runpod-http-v1_1 | 89be7429795c7477090c963d7fd313b28caab19e |
| VC-BE | [#83](https://github.com/voice-coaching/VC-BE/pull/83), develop | acb6e000eac51f3a9c455a2fa94f615d914414b1 |
| VC-FE | [#21](https://github.com/voice-coaching/VC-FE/pull/21), dev | f2c2af1780a80ee57e35eb5807265dcf3b6df3da |

AI는 운영 검증에 사용된 로컬 선행 커밋 bea2930/0c8f4a1/0648007을 포함한다. 새 코드가 의존하는 근거 전송 코드가 원격 기준에 아직 없어 PR 본문에 포함 범위를 명시했다. 프론트는 최신 dev 7a28aac를 merge하여 팀원의 hierarchy UI를 보존했다. 공개 이력 재작성은 하지 않았다.

작업 체크아웃은 `C:/Users/Public/Documents/ESTsoft/CreatorTemp/` 아래 `coaching-redesign-ai-20260918`, `coaching-redesign-be-20260918`, `coaching-redesign-fe-20260918`이다.

## 구현한 동작

1. Seungun의 가중치·threshold·vocabulary는 유지하고 교정 근거 정책을 분리했다. 기본 deletion 특징, 기대열/후보열과 철자열의 복수 최적 정렬, 초성 ㅇ, 국소 시각 미확인 등을 REVIEW_ONLY로 보류한다.
2. 최고점 한 개 대신 전체 기대 위치에서 후보를 묶고 최대 3개 학습 과제를 구성한다. 최종 입력 한도를 확인하며 후보 묶음 단위로 줄이고 제외 ID와 이유를 남긴다.
3. 시스템/코칭 프롬프트와 Git symlink를 분리하고 CLOVA/OpenAI 공통 generator를 추가했다. 새 모드는 점수 재출력 LLM 호출 없이 코칭 0~1회다. 관측은 코드가, 설명·행동·연습·자기 확인은 생성기가 작성하며 참조·크기·형식을 검사한다.
4. LLM 실패 시 기존 불확실한 삭제 조언으로 돌아가지 않고 적격 후보에 연결된 기본 연습을 제공한다. 생성 출처·fallback과 요청별 HTTP/종료 이유/token usage/시간을 기록한다. 비밀값·원문 로그는 추가하지 않았다.
5. 최대 3개 후보의 실제 dense video window와 같은 시도 무결성 경로를 연결했다. 공통 decode와 이미 수행한 Seungun 결과는 재사용한다. 승인 action과 관측 전달을 분리하며 영상 개인 교정은 미검증 상태로 둔다.
6. 실제 평가 없이 P8 violation_count=0을 발급하던 universal release 생성기를 폐기했다. 새 실행은 NOT_EVALUATED로 종료한다. 기존 운영 release는 변경하지 않았다.
7. result v3·coaching 문서 계약, V29 JSONB, 저장/조회 DTO를 추가했다. v1/v2와 기존 polling·인증·owner/lease/deadline·DUPLICATE 의미를 유지한다.
8. 새 코칭의 총점은 근거 부족/미보정 상태와 함께 null로 보류한다. 기존 점수는 재계산하지 않는다. 완료 코칭의 점수가 미확정이면 승급 submit은 409 ANALYSIS_SCORE_UNAVAILABLE이며 합격/탈락 처리를 하지 않는다.
9. 프론트는 코칭 요약·짧은 연습·자기 확인·근거 펼치기·유효 위치 재생을 제공한다. 구형 응답은 기존 화면을 사용하며 미확정 점수와 입술 관측을 실패/0점으로 바꾸지 않는다.

## 검증 결과

- BE: 최종 `test bootJar` 성공. 293개 중 264 통과, 환경 조건으로 29 skip. callback·동일 재전달·참조/시각/UTF-16 길이·Redis serializer·로컬 H2 저장 후 HTTP 상세 GET·소유권·승급 보류 검사 포함.
- 조회 통합 테스트에서 로컬 Redis 부재를 확인해 테스트에 메모리 캐시를 연결했다. Redis serializer는 별도 round-trip으로 검사했다. 운영 Redis를 검증했다는 뜻은 아니다.
- FE: 신규 코칭/기존 점수 UI 테스트 9개, TypeScript, 변경 TS/TSX ESLint 통과. 기존 dev 병합 후 재검증했다.
- AI: 수정 Python 13개 구문 확인, 수정 문서 상대 링크, AI/BE schema 동일 바이트, symlink Git mode 120000 확인. 모델 실행/LLM 호출/녹음 QA를 수동 실행하지 않았다.
- 원격 PR 생성으로 기존 CI가 자동 시작됐다. 최종 확인에서 AI 문서 링크·기본 설치/API 계약·worker 계약, BE CI, FE Vercel preview가 모두 성공했다. 모델 artifact 없는 CI와 preview 빌드이며 실제 발음 품질 검증으로 해석하지 않는다.

## 아직 완료하지 않은 단계

| 항목 | 남은 일 / 현재 동작 |
| --- | --- |
| 표지의 음운 역할 | 학습 TextGrid·g2pK·vocabulary 규약의 전문가 검토. 현재 철자 역할과 음향 표지를 구별하고 모호한 위치를 보류 |
| 지도 자료·강점 | 음소/문맥별 출처·전문가 검토 이력. 현재 기본 지도는 초안이며 strengths는 빈 배열 |
| 속도·쉼 | 검증된 VAD/발화 시간/기준 adapter. 현재 파일 길이를 실제 말속도로 쓰지 않음 |
| 영상 참조·sync | 화자 분리 보정/독립 평가, 실제 유효 landmark 수·offset 신뢰 자료, 음소별 참조. 현재 correctiveClaimsAllowed=false |
| 영상 최적화 | 겹치는 dense window 프레임 캐시 및 음소 시각이 없을 때 단어 수준 관측 |
| 학습 효과·재녹음 비교 | 동일 문장·호환 버전·조건별 비교와 사람 평가. 현재 comparison=null |
| 점수 타당성 | 사람 평가와 보정, 판단 불가 항목 및 승급 정책. 새 점수는 null이며 과거 점수는 보존 |
| 배포/E2E | 실제 PostgreSQL V29 적용, worker callback, 새 프롬프트로 실제 음성/영상 품질 QA, 단계별 p50/p95 |

AI 저장소 [AGENTS.md](https://github.com/voice-coaching/intelligentAI/blob/89be7429795c7477090c963d7fd313b28caab19e/AGENTS.md)의 지시: “QA는 개발자가 직접 수행한다.” 및 “자동 회귀 테스트 및 관련 설정·산출물을 실행·생성하지 않는다.” 이에 따라 AI의 새로운 자동 품질 QA나 실추론을 실행하지 않았고, PR push로 시작된 기존 CI 상태만 조회했다. 설계에서도 사람의 위치별 판정과 독립 보정·평가가 배포 전제다. 평가 자료나 통과 기록을 새로 꾸며 넣지 않았다.

## 다음 전환 조건

새 모드는 기본 비활성이며 `AI_ANALYSIS_COACHING_ENABLED=true`와 명시적 result v3 설정이 함께 있어야 발행한다. 우선 BE reader와 V29를 검증한 뒤 개발자가 실제 샘플의 근거·지도 적합성을 확인해야 한다. 기존 건강한 분석/승급 흐름에 미칠 영향, 새 총점 보류 정책, LLM 출력 상한과 지연을 확인한 후 제한적으로 켠다.

이번 작업에서는 RunPod/AWS 설정·프로세스, 모델 가중치·threshold·재학습, 키 사본을 변경하지 않았다. 새 media upload나 유료 LLM 호출도 하지 않았다. PR의 Draft 해제·병합·운영 전환은 수행하지 않았다.
