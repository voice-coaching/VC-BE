# 클래스 연습 구조 정리 (V53)

소유 모듈: course. 기존 `course_steps`에 `retired BOOLEAN NOT NULL DEFAULT FALSE`를 추가한다. 공개 응답에는 노출하지 않는 단계 은퇴 표시이며 별도 인덱스는 없다. PK/FK/unique/기존 step_type enum은 변경하지 않는다.

마이그레이션 실행 시 PRACTICE 단계가 정확히 하나이고, 해당 단계의 가장 최근 게시된 예문 세트가 정확히 5개 문장을 가진 클래스만 정리한다. 해당 클래스의 THEORY, AUDIO_EXAMPLE, RESULT_REVIEW 행에 `retired=true, required=false`를 저장한다. 세트 미등록·미래 게시·문장 수 차이·복수 연습 단계인 클래스는 자동 변경하지 않는다. 새 단계의 기본값은 false다.

물리 삭제 및 단계 종류/순서 변경은 없다. 이전 세션의 course_step_id, course_education_revision_id, practice_example_set_id와 불변 개정·예문을 모두 보존한다. 새 클래스 단계 목록/stepCount/새 진도 시작 단계는 은퇴 단계를 제외한다. 기존 진도 행을 일괄 초기화하거나 완료율을 재계산하지 않는다.

운영 DB에 직접 실행하지 않고 정상 배포의 Flyway V53으로 적용한다. 테스트는 별도 임시 PostgreSQL 스키마에서 V52→V53으로 업그레이드하여 데이터 수와 세션 연결 보존을 검증한다.

```sql
SELECT c.id AS course_id, c.title, s.id AS step_id,
       s.step_type, s.step_order, s.retired, s.required,
       (SELECT count(*) FROM practice_examples e
        WHERE e.set_id = (SELECT es.id FROM practice_example_sets es
                          WHERE es.step_id = s.id AND es.published_at <= CURRENT_TIMESTAMP
                          ORDER BY es.revision DESC LIMIT 1)) AS example_count
FROM courses c
JOIN course_steps s ON s.course_id = c.id
ORDER BY c.id, s.step_order;
```
