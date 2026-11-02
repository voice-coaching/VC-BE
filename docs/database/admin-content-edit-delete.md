# V65: 관리자 콘텐츠 수정·삭제 데이터

- practice_contents.deleted_at TIMESTAMPTZ nullable: 관리자 삭제 시각. status=HIDDEN, 행과 ID 보존. 기본값 null.
- reference_audios.retired_at TIMESTAMPTZ nullable: 원문 수정/콘텐츠 삭제로 더 이상 선택·재생하지 않는 기준 음성. is_primary=false로 전환한다. 행과 S3 객체는 보존한다.
- practice_contents.tts_revision: 기존 컬럼을 엔티티에 매핑한다. 원문 변경 때만 1 증가한다.
- training_sessions.content_script_snapshot TEXT nullable, content_updated_at_snapshot TIMESTAMPTZ nullable: 공용 세션 평가 원문/프롬프트 보존. V65는 현재 공용 원문으로 기존 행을 채우고 개인 문장은 채우지 않는다.
- idx_practice_contents_live_admin: owner_id IS NULL AND deleted_at IS NULL인 생성일/ID 목록용 부분 인덱스.

기존 행/연관 관계/학습 결과를 삭제하거나 개인 암호문을 복호화하지 않는다. 전역 엔티티 필터로 과거 세션의 콘텐츠 관계를 숨기지 않고, 신규 진입 조회에 삭제 필터를 적용한다. 감사 action=CONTENT_DELETE, target_type=CONTENT이며 사유·변경 필드·대상 ID를 저장한다. 원문/음성 URL을 감사 로그에 복제하지 않는다.
