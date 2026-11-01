# 관리자 아나운서 기준 음성 검증 기록

검증일: 2026-11-01. 브랜치: `feat/admin-phase-one-api`. 관리자 1차 변경에 수동 기준 음성 업로드·연결을 추가한 상태다.

## 결과

- 일반 전체 테스트: 총 520개 중 442개 통과, 78개 조건부 제외, 실패·오류 0개.
- 실행 JAR `bootJar`: 성공.
- 별도 로컬 PostgreSQL: AdminPostgresMigrationTest, AdminAudioPostgresMigrationTest 2개 통과.
- 관리자 API는 기본 18개+음성 5개다. Swagger 공개 operation은 전체 121개로 검증한다.
- `git diff --check` 및 endpoint index·specification 경로 대응 검증 통과.

## 확인 항목

- 활성 ADMIN 권한, 업로드의 관리자·콘텐츠 바인딩, 비ANNOUNCER 대상 차단.
- 콘텐츠 초안에 음성을 등록하고 관리자 미리듣기 가능. 일반 사용자 조회는 공개 전 404.
- 공개 후 기존 사용자 기준 음성 목록·재생 URL API에서 연결된 음성을 반환.
- 실제 파일 길이는 서버 검증 port 결과로 저장하며 클라이언트 objectKey·duration을 등록 입력으로 받지 않음.
- 동일 완료 요청은 기존 음성을 반환하며 파일 검증·음성·감사를 중복 생성하지 않음.
- 재조회가 이후의 대표 음성 선택을 되돌리지 않음.
- 대표 변경 시 콘텐츠 version 증가, stale version 거절, 원문 보존.
- 발급 후 원문 변경·만료·다른 관리자의 업로드는 파일 검증 이전에 차단.
- S3·디코더 I/O가 일반 호출에서 DB 트랜잭션 밖에서 실행됨.
- 감사 저장 실패 시 음성 연결·업로드 완료 상태 롤백.
- 실제 AWS presigner로 create-only 조건·체크섬·필수 헤더 확인. 외부 S3 HTTP 요청은 수행하지 않음.
- 파일 바이트 SHA-256 불일치는 decoder와 final PUT 전에 거절.
- 검증된 파일은 임시 키와 다른 서버 전용 final 키로 저장하고 다른 기존 바이트를 덮어쓰지 않음.
- MP3/AAC M4A의 컨테이너·코덱·오디오 단일 스트림·채널·샘플레이트·길이 검증 로직.
- NaN·무한대·음수·초과 길이·잘못된 probe 출력 거절.
- V61→V62 적용 시 기존 기준 음성 수 보존, 상태·MIME 쌍·크기 CHECK, 같은 콘텐츠 음성 연결 FK.

## 실행 범위의 한계

API 테스트의 파일 저장·미리듣기 port와 S3 client는 mock이다. 실제 S3 IAM·CORS·업로드·재생 성공을 확인한 기록이 아니다.

`ReferenceAudioInspectorTest.realLocalToolsDecodeGeneratedAudioAndRejectFalseMedia`는 실제 ffmpeg/ffprobe가 있는 환경에서만 생성한 MP3의 전체 디코딩과 가짜 파일 거절을 검증한다. 이번 Windows 실행에서는 기본 실행 경로에 도구가 없어 제외됐다. 컨테이너·길이 등 parser 검증은 별도 단위 테스트에서 통과했다. 이를 실제 decoder 통합 검증 완료로 해석하지 않는다.

배포 전/TEST 환경에서 다음을 확인한다.

1. 실제 관리자 지정·새 로그인, V61·V62 적용.
2. S3 임시·최종 prefix의 IAM 권한과 프론트 origin·signed header CORS.
3. ffmpeg/ffprobe 절대 경로·실행 권한, 유효한 MP3·M4A와 가짜 파일의 등록 결과.
4. 초안 생성→PUT→등록→관리자 미리듣기→공개→일반 사용자 재생.
5. 동일 uploadId 재시도, 원문·버전 변경, 만료·권한 회수 상황.
6. 임시 객체 lifecycle, 등록 실패 후 미연결 final 객체의 운영 정리 절차.

프론트 UI·운영 DB·S3 객체에 직접 변경을 적용하지 않았다. [실제 API·운영 계약](../api/admin-reference-audios.md)을 따른다.
