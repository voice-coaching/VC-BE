# AI 세부점수 전달 계약

기존 `GET /api/v3/analyses/{analysisId}` 응답의 `data.canonicalAnalysis.score`에
선택 객체 `details`를 보존한다. 최신 AI 저장소의 `docs/api/frontend-score-details-response.md`가
필드·예시·Frontend 읽기 경로의 정본이다. 이 문서는 운영 배포 완료를 뜻하지 않는다.

- 기존 overallScore, validity, reason과 과거 rubricRevision·criteria는 유지한다.
- details는 schemaVersion, validationStatus, aggregationMode, audioScore, visualScore,
  audioWeight, visualWeight, criteria를 가진다.
- details.criteria는 pronunciation_accuracy, speaking_rate, articulation_rate, pauses, pitch,
  intensity 순서다. 항목은 criterionId, label, score, status, reasonCode, weight를 가진다.
- 각 점수는 0~100 또는 null이다. SCORED는 숫자, UNSCORABLE은 null이다. 0점은 유효하다.
- criteria.weight는 음성 종합점수 내부 비율이며 최종 음성·영상 합산 비율과 구분한다.
- 기존 Jackson projection은 DTO의 optional details를 그대로 전달한다. 채점·반올림·검산은 하지 않는다.
- 구조 schema는 새 선택 필드를 허용한다. CORE/H5 allowlist, 공식 검산표·해시·버전 게이트는 추가하지 않는다.
- details가 없는 과거 응답도 수용한다. 세부점수 누락으로 기존 총점·피드백을 숨기지 않는다.
- DB migration은 없다. 이미 저장된 원문에 없는 필드는 소급 생성하지 않는다.
- Direct 이력은 원본 result JSON을 보존하므로 새 점수 필드도 그대로 저장·반환한다.

Backend reader 반영 후 AI 응답 writer를 반영한다. Frontend는 HTTP 원문의
`data.canonicalAnalysis.score.details.criteria`를 읽으며 기존 클라이언트가 data를 풀어 반환하면
`view.canonicalAnalysis.score.details.criteria`를 사용한다. 운영 배포와 화면 변경은 별도다.

영상의 기존 visual.subScores와 phoneAssessments[].subScores도 optional DTO로 보존한다.
PROVISIONAL 표시를 수용하되 원본 입술 점수·측정 근거를 재계산하지 않는다.

독립 결과 adapter도 공개 `CanonicalAnalysisView.ScoreDetails` 타입으로 변환한다.
기존 음성 전용 `LipVisual` 생성자는 optional subScores를 null(응답 생략)로 전달하는 호환 생성자를 유지한다.
점수나 입술 하위 항목을 새로 생성하지 않으며, 이 호환성은 기존 호출부의 컴파일과 공개 필드의 선택성을 유지하기 위한 것이다.

v4·v6 스키마의 기존 `legacyScore`와 입력 미접수·결과 미전달 분기가 참조하는
`publicScoreDetails` 정의도 유지한다. 새 `scoreDetails` 정의로 이를 삭제하면
기존 handoff spool 복원 중 참조 해석 오류로 서버 기동이 중단될 수 있다.
