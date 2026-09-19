# 전체 학습자료 TTS 구현·배포 기록

2026-09-19 KST. 브랜치 fix/tts-playback-without-review, 기준 develop f778e96. 사용자 승인: 전체 학습자료 TTS 계획 실행 및 2인 사전 승인 조건 폐지.

## 구현

- V31은 기존 example_tts_jobs를 content_id/text_revision/profile_revision 기준으로 확장한다. 새 asset 테이블을 중복 생성하는 대신 기존 작업·digest·cache와 object_key를 공유한다. 기존 example_id 연결 및 BYTEA 캐시를 보존한다.
- practice_contents 대본·소유자 변경에 tts_revision 증가, 공개 INSERT/수정/재발행과 동일 트랜잭션에 content_tts_outbox 기록. 개인·숨김·미발행·빈 대본은 제외한다. 주기적 reconcile이 profile별 누락 작업을 보정한다.
- 기존 단일 worker가 코스·문장·뉴스를 처리하므로 이중 합성이 없다. 기존 GENERATED 캐시는 hash 확인 후 S3로 이관하며 재합성하지 않는다. 새 음성은 기존 RunPod adapter의 전체 MP3 decode·digest·duration 검사 후 저장한다.
- AWS 기존 S3 client가 tts/practice/{jobId}/{sha256}.mp3에 비공개 저장한다. RunPod 권한·TTS 설정은 변경하지 않는다. S3 업로드는 트랜잭션 밖, DB 연결은 lease와 현재 문장 revision/발행 상태를 잠가 확인한다.
- ReferenceAudioReader는 현재 공개 revision/profile의 TTS만 목록·재생에 제공하고 실제 S3 presigned URL과 만료 시각을 반환한다. 기존 정적 HTTP 음성은 보존하되 example.com/org/net placeholder는 공개 reader에서 제외한다. 기존 가짜 DB 행을 삭제하지 않는다.
- 기준 음성 목록의 오래된 Redis 캐시 사용을 제거했다. 코스 audio GET은 기존 MP3 binary 계약과 캐시를 유지한다. 프론트의 기존 API 호출은 변경 없이 새 결과를 사용한다.
- 2인 승인 조건은 제거했다. 명시적 REJECTED, 권한, profile, digest 검사는 유지한다. 오류를 사람이 승인한 결과로 위장하지 않는다.

## 검증과 배포 준비

- 로컬 전용 PostgreSQL17을 127.0.0.1:15432에 실행하여 V30 캐시가 있는 상태에서 V31 업그레이드, 캐시 재사용, 일반 뉴스 등록, 이전 대본 작업 차단, 숨김·placeholder 제외를 검증한다.
- 전체 H2 suite와 PostgreSQL 전용 suite를 분리한다. 두 설정을 섞은 첫 전체 실행은 PostgreSQL 전용 기존 테스트가 H2에서 실행되어 실패했으며, 올바른 설정으로 재실행했다.
- EC2의 pg_dump15는 운영 PostgreSQL18과 호환되지 않아 사용하지 않았다. 단일 읽기 전용 쿼리로 관련8개 테이블을 /var/backups/alpha-tts-20260919/before-content-tts.json.gz(root0600)에 백업했다. 비밀·테이블 내용은 로그·Git에 없다.
- 배포 전 공개 자료8개(코스5, 문장1, 뉴스2), GENERATED5개, 진행 분석0개 확인. 운영 migration은 V30이었다.

## 유지·복구

기존 캐시와 음성은 삭제하지 않는다. S3 업로드 후 DB 연결 실패/대본 변경으로 남은 객체는 사용자 재생에 노출되지 않는다. 자동 객체 삭제는 아직 구현하지 않았으며, 연결되지 않은 객체 정리에는 별도 목록 대조와 유예기간이 필요하다. 최대 재시도5회와 job/digest 키로 범위를 제한한다.

이 배포는 FE 레이아웃·Melo 모델·GPT/Seungun 설정을 바꾸지 않는다. 운영 backend worker flag로 생성 중단이 가능하다. V31 이후 예전 JAR worker는 새 스키마와 호환되지 않을 수 있으므로 rollback 시 먼저 TTS worker를 비활성화하고 새 reader 수정본으로 복구한다. 추가 migration을 자동 삭제하지 않는다.

운영 적용 및 실제 MP3 검증 결과는 완료 후 아래에 추가한다.
