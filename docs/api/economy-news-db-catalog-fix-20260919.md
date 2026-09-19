# 경제 뉴스 분류 오류와 DB 예문 경로 수정

2026-09-19 KST. 운영 DB 조회 및 분류 변경, 최신 FE dev 소스 대조에 근거한다.

## 원인

운영 DB의 공개 콘텐츠 두 개가 같은 제목과 본문을 가진다. 본문은 “한국은행은 오늘 기준금리를 동결한다고 발표했습니다.”다.

| ID | 변경 전 | 변경 후 |
| --- | --- | --- |
| 102 | SENTENCE / TENSE_CONSONANT / PRONUNCIATION | NEWS / ECONOMY / PRONUNCIATION |
| 103 | NEWS / ECONOMY / INTONATION | NEWS / ECONOMY / PRONUNCIATION |

실제 분석 차단에 사용되는 속성은 category가 아니라 learningFocus다. PracticeSession은 INTONATION을 capabilities의 supportedLearningFocuses와 대조해 업로드 전에 차단한다. ID 103의 학습 유형은 현재 발음 분석 지원 범위와 맞지 않았다. ID 102에는 별도로 콘텐츠 유형·주제 분류 오류가 있었다. 단순 ECONOMY 변경만으로 ID 103의 분석 차단은 해결되지 않는다.

위 두 행을 제목·본문·공개 상태·소유자 없는 공용 콘텐츠 조건으로 확인한 후 하나의 트랜잭션에서 수정했다. 해당 detail/list/next/recommendation 캐시 범위에서 존재하던 키 1개를 제거했다. 사용자 녹음·분석·기존 세션의 스냅샷은 변경하지 않았다. 같은 제목의 ID를 삭제하거나 병합하지 않았다. 이전 세션은 기존 학습 유형을 보존하므로 수정된 콘텐츠에서 새 연습을 시작해야 한다.

## 프론트 데이터 경로

현재 배포된 일반 /news, /sentences, /announcer는 ContentCatalog→서버 콘텐츠 API를 사용한다. CourseLesson도 이미 practice-examples API를 사용한다. 원래 로컬 VC-FE dev 체크아웃에는 예전 정적 practice-examples.ts가 남아 있어 최신 원격 dev/배포 코드와 구분했다.

남아 있던 문제는 localStorage의 ttobak.developer-mode 플래그가 전체 API를 createDevApi로 교체하는 구조다. 이 경로는 가짜 콘텐츠·추천·코스·기준 음성을 반환했다. 이제 API 진입점을 createRemoteApi 하나로 고정해 예문, 연습 문장, 홈 추천, 코스, 녹음, 분석 결과가 같은 서버 API를 사용한다. 이전 개발자 플래그는 mock 데이터를 다시 활성화할 수 없다.

이전 가상 토큰만 정리하며 실제 인증 토큰은 보존한다. 숨겨진 개발자 로그인 호출은 실제 계정 로그인 안내를 반환하며 가상 세션을 생성하지 않는다. 개발용 fixture·연결되지 않은 prototype 파일은 앱 API에 import하지 않으며 콘텐츠 등록 원본으로 임의 사용하지 않는다. DB 조회 실패를 mock 문장이나 가짜 성공으로 대체하지 않는다.

## 확인과 제한

API 경로 회귀 검증에서 개발자 플래그가 남은 경우에도 서버 경로·인증 헤더·서버 데이터 사용을 확인했고, 503에서 mock fallback이 없음을 확인했다. 가상 토큰 정리와 실제 토큰 보존도 확인했다.

운영 분류 수정은 적용됐다. 새 음성 업로드·추론은 실행하지 않았으므로 이번 수정으로 실제 분석 E2E 성공을 확인했다고 말하지 않는다. TTS 공개 청취 승인은 별도이며 카테고리 수정으로 승인 상태를 바꾸지 않았다.

최종 검증: 관련 테스트 22개, TypeScript, 변경 파일 ESLint, diff 공백 검사 통과. 기본 Turbopack은 외부 node_modules junction 경계 오류로 실패했고 `next build --webpack`은 컴파일·타입 검사·32개 페이지 생성을 완료했다. FE 변경 브랜치는 `fix/db-practice-catalog`이며 이번 작업에서 FE push/배포는 수행하지 않았다.
