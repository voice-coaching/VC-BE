# 프론트 전달: 문장 연습 카테고리 요청값 정정

## 작업 유형

- 일반 문장 연습 콘텐츠 데이터 보완
- 카테고리 표시명과 API 요청값 구분

## 1. 원인 및 백엔드 변경

현재 목록 API는 `category`를 DB 값과 정확히 비교합니다. `category=받침`처럼 화면 표시명을 보내면 영문 코드와 일치하지 않아 `200 OK`, `items: []`를 반환합니다.

V58 마이그레이션에서 일반 문장 콘텐츠를 다음과 같이 추가합니다. 기존 예시문제·승급시험·클래스 문장과 테이블 구조는 변경하지 않습니다.

| 화면 표시명 | API 요청값 | 이번 추가 문장 수 |
| --- | --- | --- |
| 받침 | `FINAL_CONSONANT` | 5 |
| 된소리 | `TENSE_CONSONANT` | 0 (기존 데이터 유지, 표시명 등록) |
| 자음 | `CONSONANT` | 5 |
| 모음 | `VOWEL` | 5 |

내용은 한국어 연습 문장이고, 영어는 카테고리 코드입니다. 된소리는 작업 전 사용자가 확인한 DB에서 공개 문장 2개였습니다. 배포 후 개수는 실제 목록 응답으로 확인합니다.

## 2. 프론트 변경 사항

카테고리 목록은 아래 API로 조회합니다. 로그인 access token을 사용합니다.

```http
GET /api/practice-contents/facets?type=SENTENCE
Authorization: Bearer <accessToken>
```

응답 `data.categories` 각 항목의 `label`은 화면 표시, `value`는 목록 요청의 `category`에 사용합니다. 한글 필터를 먼저 노출하고 매핑이 없으면 한글을 그대로 보내는 fallback을 제거합니다. 목록을 받기 전에는 필터 요청을 보류하고, 조회 실패 시 재시도를 제공합니다.

예를 들어 받침 선택 시 다음 요청을 보냅니다.

```http
GET /api/practice-contents?type=SENTENCE&category=FINAL_CONSONANT&page=0&size=20
Authorization: Bearer <accessToken>
```

프론트 프록시 경로:

```text
/api/backend/api/practice-contents?type=SENTENCE&category=FINAL_CONSONANT&page=0&size=20
```

`src/components/content-catalog.tsx`의 카테고리 선택·첫 페이지·더 보기 요청 모두 같은 `value`를 사용해야 합니다. 카테고리 변경 시 `page=0`으로 초기화합니다. 전체 조회는 `category`를 생략합니다. `ALL`, `전체`, 빈 한글 매핑값을 보내지 않습니다.

`EXAMPLE_QUESTION`, `TITLE_EXAM` 등 다른 분류도 facets에 포함될 수 있습니다. 현재 문장 연습 화면을 네 필터로 제한한다면 위 네 코드로 선택지를 제한하되, 데이터 값은 facets 응답을 사용합니다. 누락된 코드를 임의 생성하지 않습니다.

## 3. 검증 및 반영 상태

1. 백엔드 V58 적용 후 facets에 네 코드와 한글 표시명이 나오는지 확인합니다.
2. 받침·자음·모음 각 목록에 추가한 5개 문장이 나오는지 확인합니다. 기존 동일 분류 데이터가 있다면 더 많을 수 있습니다.
3. 된소리 선택 시 `TENSE_CONSONANT`가 전송되는지 확인합니다.
4. 전체 조회·더 보기·필터 변경 후 첫 페이지가 정상인지 확인합니다.
5. 기존 예시문제·승급시험·클래스 조회가 유지되는지 확인합니다.

이 PR은 DB 마이그레이션과 전달 문서입니다. 프론트 코드는 수정하지 않았고 운영 DB에 직접 SQL을 실행하지 않았습니다. 백엔드 배포에서 마이그레이션 적용과 프론트 수정·배포가 모두 필요합니다. 문장 공개 시 기존 TTS outbox 트리거가 적용되지만, 기준 음성 생성 완료까지 보장하는 데이터 변경은 아닙니다.
