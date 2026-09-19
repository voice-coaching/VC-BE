# Seungun → CLOVA 전달 개선 배포 결과

2026-09-18. [기존 계획](clova-feedback-transfer-plan-20260918.md)을 재검토해 구현·배포했다. AWS 공개 API와 결과 콜백 계약은 유지하고 RunPod의 피드백 입력 생성 경로를 변경했다.

## 배포

- AI 브랜치: `feat/clova-feedback-evidence-transfer`
- 배포 코드: `0648007cf689dfd16866f46cf636d3ecd6adeae2`
- 피드백 정책: `evidence-transfer-v3-20260918`, 입력 스키마 v6. 이전 v3/v4/v5 입력도 수용한다.
- 실행 사본 및 영구 overlay 69개 항목의 SHA-256 일치 확인. 심볼릭 링크는 manifest에 기록된 링크 대상 문자열로 검증했다.
- API PID 41664, `ready`, `executorReady=true`, 위 worker revision 확인.
- CLOVA PID 20312와 기존 모델 pin 유지. 모델 재학습·가중치·검출 임계값 변경 없음.
- 실제 워커 UID 10001에서 새 설정·토크나이저를 읽고 검증 때와 동일한 최종 메시지를 만드는 것까지 확인했다.
- 원격 push/PR은 이번 작업에서 실행하지 않았다. 아래 구현 커밋은 로컬 브랜치와 운영 overlay에 반영됐다.

## 변경한 전달 경로

음성 분석 → 영상 보조 → 기존 채점 → CLOVA 피드백 → 기존 AWS 결과 콜백 순서다. 기존에는 채점 결과가 피드백 생성 뒤에 만들어졌다.

`feedback_evidence.py::build_feedback_evidence`가 전체 음소 행, 기대열·CTC 후보열, 정렬, 시각, 원래 정밀도의 검출 점수, 9개 soft feature, 원문 위치, 영상 근거와 검증된 점수를 서버 내부 근거 객체에 보존한다. 실제 LLM 입력은 모델 토크나이저 기준 3,900토큰 안에서 구성한다.

공식 선택 지점 → 오류 후보 → 인접 음소 → 나머지 순서로 전달하며, 포함·제외 index와 제외 특징값 범위를 명시한다. 세부 채점표보다 음소 관측을 우선한다. 개인정보·파일 경로·원본 hash·서버 식별자는 전달하지 않는다. 전체 근거를 모두 LLM에 넣었다는 의미는 아니다.

영상 없음·미설정·추출 실패·입력 처리 실패·공개 가능한 근거 없음·연결 검증 실패를 구분한다. 원본이 이유를 주지 않는 경우 품질 문제나 정책 제외로 추측하지 않는다. 제공되지 않은 프레임 수·표본 시각은 null이다.

주요 변경 함수는 AI 저장소의 다음 경로에 있다.

- `src/voice_coach/pronunciation_feedback/feedback_evidence.py`: 근거 구성, 입력 예산, 누락 기록
- `src/voice_coach/pronunciation_feedback/adapters/advice_generator_hyperclova.py`: 토크나이저·프롬프트·실제 호출·단계 로그
- `src/voice_coach/pronunciation_feedback/grounded_advice.py`: 입력 v6 및 JSON 응답 검증
- `src/voice_coach/backend_analysis/grounded_feedback.py`: 같은 시도의 음성·영상·점수 연결
- `src/voice_coach/backend_analysis/service.py`: 채점→피드백 순서 및 영상 처리 상태
- `deploy/runpod/prepare_http.py`, `deploy/runpod/services.http.json`: 활성화 flag와 고정 모델 토크나이저 주입

## 실제 CLOVA 검증

사용자가 승인한 testvideo1의 기존 실제 분석 JSON을 사용했다. 새 음성 추론이나 사용자 분석 DB 행을 만들지 않았다.

| 항목 | 결과 |
|---|---|
| 전체 원본 음소 | 30개, 지원 필드와 정밀도 보존 및 v6 왕복 확인 |
| 최종 LLM 음소 행 | 24개. 제외 index: 22–23, 25, 27–29 |
| 기대열 / CTC 후보열 | 전체 30개 / 28개 전달 |
| 세부 특징 | 선택 음소의 9개 필드 전달. 원래 없는 값은 null |
| 점수 | 기존 총점·대분류 전달, 49개 세부 채점표는 입력 예산상 생략 |
| 입력 / 출력 | 3,882 / 221토큰. 모델 서버 관측값과 일치 |
| 생성 시간 / 호출 처리 | 59.773초 / 약 60.28초 |
| 종료 / 검증 | `stop` / `validated_grounded_plan` |
| fallback | 최종 호출에서는 없음 |

실제 생성 문구의 일부:

> 받침 'ㅇ'은 소리가 있는 연구개 비음 [ŋ]으로, 혀 뒤쪽으로 입안 통로를 막고 공기를 코로 내보내는 방식으로 발음해야 합니다. 이를 위해 '강', '방'과 같은 단어를 연습하며 올바른 혀 위치와 기류 방향을 느껴보세요.

이는 일반적인 발음 안내다. 실제 입력의 선택 지점은 CTC deletion 후보이며 후보 음소·시각이 null이다. 사용자에게 실제 생략이나 혀 위치 오류가 있었음을 확인한 결과가 아니다. 한 문장 검증으로 모든 피드백의 정확성을 보증하지 않는다.

## 검증 중 발견해 해결한 문제

1. 워커 Transformers 5와 모델 서버 4.52의 GPT2 토큰 수 차이: 모델 원본 `tokenizer.json` 그래프를 사용하도록 보완했다. 최종 메시지의 양쪽 토큰 수가 일치한다.
2. CLOVA의 단일 JSON 코드블록을 기존 파서가 거절해 fallback: 완결된 단일 JSON 코드블록만 정규화한 뒤 기존 스키마·ID·enum·개인정보 검증을 수행한다. 설명문을 임의로 잘라서 통과시키지 않는다.
3. 초기 생성의 “받침 ㅇ은 무음”이라는 잘못된 설명: 초성/종성의 소릿값 차이와 일반 조음 참고를 명시하고 실제 재생성에서 해당 오류가 사라진 것을 확인했다. 참고: [국립국어원](https://www.korean.go.kr/front/onlineQna/onlineQnaView.do?mn_id=216&pageIndex=1&qna_seq=329332).
4. 최초 배포 시 영구 볼륨의 신규 파일 소유권 변경 거절: 자동 원복으로 이전 리비전 ready를 확인했다. 영구 볼륨은 기존 root:root 방식, 실행 사본은 워커 그룹 방식을 유지하도록 배포 도구를 보완한 뒤 재배포에 성공했다.

## 운영 보호 및 한계

배포 전·접수 보호 적용 직후 PROCESSING 및 최근 PENDING 모두 0건이었다. 새 analyze/retry 접수만 잠시 보호했고 자동 복구 타이머를 준비했다. 배포 후 AWS→RunPod와 백엔드 callback readiness가 모두 ready였다.

AWS nginx 원본 복구 SHA-256은 `777d581a1eb02ce80435e1d5b72a8bfd68108e33a329399330c877950a010331`로 배포 전과 일치한다. 임시 접수 보호와 타이머는 해제했다. 백엔드 설정·DB·키 사본은 변경하지 않았다.

원복 자료는 RunPod root 전용 백업 `feedback-transfer-20260918-v3-r2`에 보존했다. `AI_ANALYSIS_CLOVA_FULL_EVIDENCE_ENABLED=false`로 기존 선택 음소 입력으로 돌아갈 수 있다. 완전 원복은 코드·overlay·manifest·환경·attestation 백업을 함께 사용한다.

검증 범위는 AST·JSON·diff 검사, 기존 실제 근거의 전달 보존, 실제 CLOVA 생성, 배포·권한·파일 무결성·양쪽 readiness다. 새 미디어 업로드부터 AWS 결과 저장까지의 E2E 및 자동 회귀 QA는 이번 작업에서 실행하지 않았다. 영상 표본 메타데이터 추가와 장문 전체를 위한 분할 생성은 이번 구현 범위에 포함하지 않는다.
