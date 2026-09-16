# 단계별 예문과 Chirp 음성

이슈 #68, 상위 #52. 법적 동의·비밀번호 재설정·결제 구독 제외.

## 조회와 학습 연결

- GET /api/courses/{courseId}/steps/{stepId}/practice-examples
- 기존 Bearer 인증, 기존 envelope. data는 courseId,stepId,revision,items다.
- items는 정확히 5개이며 order=1..5다. 각 item은 id,order,text,hint,focus,locale와 추가 practiceContentId를 제공한다.
- **프론트는 선택한 item의 숫자 practiceContentId를 기존 학습 생성 contentId로 전달한다.** 클래스 학습이면 courseStepId도 전달한다. 문자열 exampleId를 contentId로 보내지 않는다.
- 새 개정은 새 exampleId와 새 practiceContentId를 사용한다. exampleId는 영숫자/하이픈/밑줄 1~100자다. 과거 ID의 텍스트를 덮어쓰지 않는다.
- 기본은 본인 최신 진행 중 세션에 고정된 개정, 없으면 최신 게시 개정이다. 선택 sessionId는 본인의 과거 세션에 고정된 개정을 조회한다.
- 학습 세션과 승급 시험에 예문 세트/개정을 저장한다. 기존 세션 상세 content에 practiceExampleId/practiceExampleRevision을 추가한다(일반 콘텐츠 null).
- 예문 텍스트는 연결된 불변 practice_contents.script_text다. 합성 입력·세션 상세·기존 분석 입력이 같은 콘텐츠를 참조한다.
- course/step 불일치, 다른 사용자 세션, 없는/숨김 예문은 404다. 게시 예문 세트 미준비는 503 PRACTICE_EXAMPLES_UNAVAILABLE이다.

## 게시 데이터

V25는 운영 문장을 임의 생성하지 않는다. 실제 승인된 교육 예문이 필요하다.
한 트랜잭션에서 해당 step의 course_step_revisions, practice_example_sets, CLASS_PRACTICE 콘텐츠 5개와 practice_examples 5개를 준비한다.
세트는 education_revision_id와 해당 step이 일치해야 하며, 콘텐츠는 owner 없는 PUBLISHED/ko-KR, 비어 있지 않은 UTF-8 4500바이트 이하 원고여야 한다.
기존 교육 개정을 참조해도 되지만 새 예문 개정의 모든 exampleId/contentId는 새로 부여한다. published_at으로 예약 게시를 지원한다.
PostgreSQL deferred trigger는 커밋 시 정확히 5개/순서 유일성/교육 단계 일치를 검사한다. 세트·예문 UPDATE/DELETE 및 참조 원고·제목 변경은 차단한다.
상태를 숨김으로 바꾸는 것은 가능하나 해당 세트 조회/합성은 더 이상 준비 완료로 반환하지 않는다. 이미 생성된 오디오는 DB 캐시에 남을 수 있으며 각 요청에서 게시 상태를 다시 확인한다.

## 합성 음성

GET /api/practice-examples/{exampleId}/audio?voice=ko-KR-Chirp3-HD-Aoede

- 유일한 현재 허용 화자/default는 ko-KR-Chirp3-HD-Aoede, speakingRate=0.92, MP3다.
- 임의 text, speakingRate 및 기타 query를 허용하지 않는다. 게시된 immutable exampleId에서 서버가 텍스트를 조회한다.
- 200 audio/mpeg binary, ETag=오디오 SHA-256, Cache-Control: private,max-age=86400, Vary:Authorization. If-None-Match 일치 시 304/body 없음.
- CORS는 If-None-Match 요청과 ETag 응답 노출을 지원한다.
- 오류는 JSON envelope, 503 TTS_UNAVAILABLE 또는 429 TTS_RATE_LIMITED, Cache-Control:no-store다.
- 응답 base64를 디코딩하고 최대 2MB·MP3 헤더를 검사한다. 이는 실제 플레이어의 전체 음성 디코딩 검증을 대신하지 않는다.

## Google 연결

CHIRP_TTS_ENABLED=true 및 서버의 Google Application Default Credentials(ADC)가 필요하다. 선택 GOOGLE_CLOUD_QUOTA_PROJECT를 x-goog-user-project로 사용한다.
Google 공식 google-auth-library-oauth2-http 1.50.0으로 cloud-platform scope와 자동 토큰 갱신을 사용한다. 클라이언트에 credential/token을 보내거나 로그에 기록하지 않는다.
POST https://texttospeech.googleapis.com/v1/text:synthesize에 input.text, voice(languageCode/name), audioConfig(MP3/speakingRate)를 전송한다.
합성 HTTP 연결 5초/읽기 30초, 응답 JSON 최대 2.8MB다. 실패 원문은 응답/로그에 노출하지 않는다. 기본 enabled=false이며 ADC를 기동 시 미리 읽지 않는다.

공식 계약 확인 자료:

- [REST text.synthesize](https://docs.cloud.google.com/text-to-speech/docs/reference/rest/v1/text/synthesize)
- [Chirp 3 HD 및 pace control](https://docs.cloud.google.com/text-to-speech/docs/chirp3-hd)
- [ADC 인증](https://docs.cloud.google.com/text-to-speech/docs/authentication)

## 영속 캐시와 비용 제한

캐시 키는 exampleId/revision/voice/speakingRate의 SHA-256이다. 성공한 bytea와 ETag만 영속 저장한다. 텍스트 개정은 새 ID라 기존 캐시와 섞이지 않는다.
캐시 미스는 DB의 짧은 별도 트랜잭션에서 전역 quota 행과 캐시 행을 잠그고 5분 lease를 발급한다. Google 호출 중 DB 트랜잭션을 유지하지 않는다.
다른 요청이 같은 음성을 생성 중이면 503이며 재시도할 수 있다. 실패 뒤 5초 cooldown, lease 만료 후 재시도 가능하다. 만료된 옛 lease는 새 생성 결과를 덮어쓰지 못한다.
합성 시도는 사용자당 분당 20회, 전체 분당 100회다. 실패도 한도로 계산하며 캐시 hit는 외부 비용을 만들지 않는다. Google 429도 TTS_RATE_LIMITED로 매핑한다.
사용자 quota의 1시간 이상 오래된 행은 다음 cache miss에서 정리한다. 오디오 캐시의 보존/용량 운영 정책은 추후 게시 콘텐츠 규모에 맞춰 설정해야 한다.

## 검증 범위와 남은 작업

자동 테스트는 예문/교육/시험 개정, 기존 학습 원문 일치, 인증/소유권, MP3 응답·ETag/304, Google REST 형식·오류 변환, 캐시 lease·호출 한도·동시성, PostgreSQL fresh/upgrade/불변 trigger를 검증한다.
Google 응답 테스트는 테스트 대역을 사용한다. **실제 Google 프로젝트 권한/과금·Chirp 음성 출력·프론트 재생/녹음/AI 종단 검증과 실제 게시 예문 등록은 미완료다.**
운영 자격증명·환경변수·배포는 이번 작업에서 변경하지 않았다. 앞선 Push 실제 발송, 뉴스 발행사 데이터 등 기존 미완료 항목도 유지한다.
