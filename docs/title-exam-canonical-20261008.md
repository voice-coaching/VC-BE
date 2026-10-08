# 승급시험의 canonical 음성 분석 지원

- `TITLE_EXAM` 지원 여부를 활성화한다. 분석 접수 readiness 검사는 유지한다.
- 시험에 연결된 발음 음성 세션을 canonical 요청 대상으로 허용한다. 클래스와 영상 시험은 미지원 상태를 유지한다.
- 기존 시험 생성, 응시 자격, 콘텐츠 고정, 세션 연결 및 analysisId 채점 API를 유지한다.
- 채점 점수는 현재 사용자·시험 세션·콘텐츠에 연결된 선택 녹음의 COMPLETED v5 분석에서만 읽는다. `CanonicalCommittedResultReader`가 현재 실행과 COMMITTED 저장 증명을 검증한 뒤 ACCEPT/INLINE/feedback 및 adapter/generation 상태와 점수를 확인한다.
- 점수 없음, 거절, 저장 증명 없음, 이전 실행, 범위 밖 점수는 승급에 사용하지 않는다. 클라이언트 점수는 받지 않는다.
- 배포 대상은 VC-BE다. 프런트의 TITLE_EXAM 검사는 그대로 유지하며, 서버 배포 후 지원 응답을 받아 시험을 진행한다.

검증: production Java compile 및 CanonicalTitleScoreTest. 전체 테스트 컴파일은 기존 RecordingDeletionOutboxDispatcherTest의 생성자 인자 불일치로 막히므로, 해당 테스트만 임시 Gradle init script로 컴파일하여 실행한다.
