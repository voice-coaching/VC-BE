# Seungun GOP/GPT 결과 계약

새 RunPod 하네스 `canonical-seungun-gop-gpt-20261006-v1`의 core/LLM/lock/prompt tuple을 추가한다. 기존 이력 tuple은 유지한다. active RunPod 실행은 AI 저장소에서 새 GOP 하네스 하나로 제한한다.

- `phone-rubric-gop-ctc-af-sd-v1`의 9항목 점수와 GPT `feedback`을 수용한다.
- 새 하네스의 READY 응답은 `selection=null`, `items=[]`, 비어 있지 않은 `feedback`이다. 과거 하네스의 후보 연결 요건은 변경하지 않는다.
- 공개 DTO와 저장되는 총평에 feedback을 보존한다. 직접 분석 이력은 기존 JSON 보관 경로를 사용한다.
- Backend는 GOP를 재계산하거나 GPT 단계·총점을 교체하지 않는다. schema, 공급자 결과 출처, 요청 연결 검증은 계속 유지한다.
- DB 마이그레이션은 없으며 점수 또는 저장 완료를 FE 직접 응답의 새 선행 조건으로 추가하지 않는다.

FE 호환 코드와 새 verifier bundle을 준비한 뒤 RunPod의 새 패키지를 활성화해야 한다. schema digest가 달라지므로 양쪽 계약을 함께 반영한다. develop 병합에 따른 자동 배포는 담당자가 진행한다. 이 PR은 운영 배포가 아니다.

확인: Java compileJava와 JSON/schema 정적 확인. 자동 회귀 테스트·실제 추론·브라우저 QA는 실행하지 않는다.
