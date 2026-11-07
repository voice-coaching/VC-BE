# 프론트 전달: 실제 영상 업로드와 입술 임시 점수

기존 사용자 UI를 새로 만드는 범위가 아니라 촬영한 실제 파일과 기존 분석 reader를 연결하는 범위다.

- `/api/analysis-capabilities`에서 acceptedVideoMimeTypes, maximumVideoUploadBytes, 길이, videoRequiresAudioTrack,
  videoProcessingConsentRequired와 videoProcessingConsentPolicyRevision을 사용한다.
- `/api/analysis-capabilities/canonical`의 VIDEO scope가 열리지 않았으면 영상 분석을 가능하다고 안내하지 않는다.
- 영상 촬영 결과의 video Blob을 업로드한다. 음성 추출 Blob만 보내는 흐름과 구분한다.
- 녹음 등록에 기존 영상 동의 필드를 보낸다. 내부 AI 요청의 grant·서명 필드를 프론트가 만들지 않는다.
- Backend 정수 analysisId를 기존 `/api/v3/analyses/{analysisId}` 조회에 사용한다. 익명 Direct UUID를 대입하지 않는다.
- 종합점수는 data.canonicalAnalysis.score.overallScore다. 프론트에서 음성/입술 점수를 다시 합산하지 않는다.
- visual.subScores와 phoneAssessments[].subScores는 없을 수 있다. 있으면 해당 label/score/status/측정값을 표시한다.
- score=0과 score=null을 구분한다. null은 정상 0점이 아니다.
- PROVISIONAL은 전문가 검증 전 참조 적합성 점수다. calibrationRevision=null을 오류로 보지 않는다.
- correctiveClaimsAllowed=false와 COMPARE_REFERENCE만 허용된 의미를 유지한다. 전체 입술 점수가 없어도 국소 관측은 표시할 수 있다.
- subScores 미포함 구 응답과 음성 전용 NOT_PROVIDED 응답도 계속 읽는다.

현재 이 문서는 프론트 코드 수정·배포 완료 기록이 아니다. 실제 영상·동의·운영 저장·공개 조회를 함께 확인한 뒤 공개 접수를 활성화한다.
