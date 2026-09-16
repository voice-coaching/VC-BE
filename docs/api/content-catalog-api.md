# 콘텐츠 필터·이전/다음·메타데이터

이슈 #64 / 상위 #52. 법적 동의·비밀번호 재설정·결제 구독 제외.

## 계약

- `GET /api/practice-contents/facets?type=NEWS`: 필수 type, 카테고리/난이도 value·label·count·order, revision.
- `GET /api/practice-contents/{contentId}/adjacent?type=NEWS`: 필수 type, 선택 category/difficulty/focus. previous/next는 id/title 또는 null.
- 기존 `GET /api/practice-contents`: publisher, paragraphCount, sentenceCount, syllableCount, publishedAt, speakerName 추가. hasNext 유지.

모두 기존 Bearer 인증과 envelope를 사용한다. type은 기존 ContentType enum을 사용한다.
새 조회의 잘못된 ID/enum·필수 type 누락은 400 VALIDATION_ERROR, 필터에 속하지 않는 anchor/비공개/미존재는 404 RESOURCE_NOT_FOUND다.
첫 항목의 previous와 마지막 항목의 next는 null이며 목록을 순환하지 않는다. 빈 facets는 빈 배열이다.

## 조회 기준

목록·facets·adjacent 모두 owner 없는 PUBLISHED 콘텐츠 중 publishedAt이 null 또는 현재 시각 이전인 항목만 사용한다.
사용자 custom 원고, 숨김·미게시·예약 게시 콘텐츠는 제외한다.
정렬은 publishedAt DESC NULLS LAST, createdAt DESC, id DESC다. 동일 시각도 id로 순서를 확정한다.
이웃은 동일 필터에서 timestamp/id를 비교해 바로 앞뒤 1개씩 조회한다. 전체 목록을 애플리케이션 메모리에 읽지 않는다.
목록은 본문을 포함한 현재 페이지와 전체 개수를 조회하고, 화자 fallback은 페이지에 포함된 참조 음원을 한 번에 조회한다.

목록 캐시는 사용하지 않는다. 오래된 목록과 현재 이웃/필터가 서로 달라지는 것을 줄이기 위한 변경이며 기존 상세/추천 캐시는 유지한다.
여러 요청 사이에 실제 게시 데이터가 변경되면 결과가 달라질 수 있다. 이 API는 페이지 전체 snapshot/cursor 계약이 아니다.

## taxonomy

V23 content_categories에는 명세가 제시한 NEWS/SOCIETY=사회, NEWS/ECONOMY=경제만 초기 등록한다.
등록되지 않은 실제 category는 value와 같은 label로 반환하며 sort order는 마지막이다. 알려지지 않은 한글 라벨을 추측하지 않는다.
카테고리 순서는 등록 sort_order, 동률은 code다. 건수가 0인 카테고리는 표시하지 않는다.
난이도는 BEGINNER=초급, INTERMEDIATE=중급, ADVANCED=고급이다.
revision은 type, 정렬된 라벨·코드·건수·순서의 SHA-256이다. 같은 응답은 같은 revision이며 집계 또는 라벨 변경 시 달라진다.

## 실제 원고 메타데이터

- NEWS: paragraphCount는 빈 줄로 분리한 비어 있지 않은 문단 수, sentenceCount는 한국어 BreakIterator 문장 수다.
- SENTENCE: syllableCount는 NFC 정규화 후 한글 완성형 음절 수다. 공백·기호·숫자·영문자를 한글 음절로 세지 않는다. 제목의 숫자는 사용하지 않는다.
- ANNOUNCER: content.speaker_name을 우선 사용하고, 없으면 primary 참조 음원 우선/ID 오름차순의 등록된 화자명을 사용한다.
- publishedAt은 실제 저장된 게시 시각을 UTC로 반환하며 미등록이면 null이다.

### 기존 데이터 보완 필요

기존 DB에는 publisher가 없어 V23에서 nullable 필드를 추가한다. 기존 뉴스는 실제 발행사 확인 후 채워야 한다.
미등록 publisher/speakerName은 null이며 가짜 출처나 화자명을 넣지 않는다. 신규 명세의 NEWS publisher 필수 조건은 운영 데이터 보완 전에는 완전히 충족되지 않는다.
프론트는 이관 기간 동안 null을 미등록으로 표시해야 한다. 값이 없는 기존 콘텐츠를 목록에서 갑자기 제거하거나 전체 목록을 실패시키지는 않는다.
실제 카테고리 라벨 자료·발행사 데이터와 프론트 타입 대조는 별도 확인이 필요하다.

## 검증

정렬 동률/null 날짜/페이지 경계/단일 항목, 필터 및 공개 범위, taxonomy 건수/개정 변화, 실제 원고 메타데이터와 대표 화자,
HTTP 인증/검증/404, PostgreSQL fresh/upgrade 및 기존 데이터 보존을 테스트한다.
실제 Redis 연동과 프론트 브라우저 종단 검증을 의미하지 않는다.
