# 일반 문장 발음 카테고리 데이터

## 저장 구조와 범위

V58은 기존 `practice_contents`와 `content_categories`에 데이터만 추가합니다. 컬럼·관계·인덱스·Java enum·API 형식은 변경하지 않습니다. category는 기존 문자열 필드입니다.

`content_categories`는 `(content_type, code)` 유일 키로 표시명과 순서를 관리합니다. `practice_contents.category`와 외래 키로 묶여 있지는 않으며, facets 조회 시 콘텐츠 유형과 코드를 조합해 표시명을 찾습니다.

| content_type | code | label | sort_order | 신규 콘텐츠 |
| --- | --- | --- | --- | --- |
| SENTENCE | FINAL_CONSONANT | 받침 | 1 | 5 |
| SENTENCE | TENSE_CONSONANT | 된소리 | 2 | 없음 |
| SENTENCE | CONSONANT | 자음 | 3 | 5 |
| SENTENCE | VOWEL | 모음 | 4 | 5 |

신규 콘텐츠는 `learning_focus=PRONUNCIATION`, `status=PUBLISHED`, `owner_id=NULL`, `published_at=now()`로 등록합니다. 카테고리별 난이도는 BEGINNER 2개, INTERMEDIATE 2개, ADVANCED 1개입니다. 원문·설명·연습 어구·예상 소요 시간을 포함합니다. 각 원문은 한국어 창작 문장입니다.

ID는 기존 시퀀스를 사용합니다. 이미 같은 유형·카테고리·제목의 공용 콘텐츠(`owner_id IS NULL`)가 있으면 삽입하지 않습니다. 여기서 기존 상태를 PUBLISHED로 바꾸거나 기존 원문을 덮어쓰지 않습니다. 기존 숨김 콘텐츠도 같은 키라면 유지됩니다. 카테고리 표시명과 순서는 지정값으로 갱신합니다.

기존 `EXAMPLE_QUESTION`, `TITLE_EXAM`, 클래스용 콘텐츠·연결·학습 기록을 재분류하거나 삭제하지 않습니다. 문장 등록 시 기존 `enqueue_content_tts` 트리거가 outbox를 생성합니다. TTS 생성·승인은 기존 처리 흐름을 따릅니다.

## 적용 확인

```sql
SELECT category, COUNT(*) AS visible_count
FROM practice_contents
WHERE content_type = 'SENTENCE'
  AND status = 'PUBLISHED'
  AND owner_id IS NULL
  AND (published_at IS NULL OR published_at <= CURRENT_TIMESTAMP)
GROUP BY category
ORDER BY category;

SELECT code, label, sort_order
FROM content_categories
WHERE content_type = 'SENTENCE'
ORDER BY sort_order, code;
```

운영 적용은 Flyway V58 배포로 수행합니다. pgAdmin에서 선실행하면 Flyway 적용 기록과 실제 데이터 반영 시점이 달라지므로 배포 적용을 기준으로 확인합니다. 프론트 요청 계약은 [전달 문서](../api/frontend-sentence-categories.md)를 참고합니다.
