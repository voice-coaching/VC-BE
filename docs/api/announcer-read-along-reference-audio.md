# 아나운서 따라 읽기 기준 음성

## 목적

`V47__seed_announcer_read_along_reference_audio.sql`은 `정상발음` 폴더의 10개 대본을 처음 등록한다. `V48__reset_announcer_read_along_order.sql`은 기존 초급·중급·고급 아나운서 연습 시드를 제거하고, 따라 읽기 1~10을 사용자가 지정한 순서의 대본과 기준 음성으로 재연결한다.

각 콘텐츠의 `practice_contents.script_text`는 아나운서가 읽는 3문장 원고 전체를 저장한다. S3 object key는 실제 음성 파일의 원본 파일명을 유지하며, 파일명이 짧아 원고 전체와 달라도 콘텐츠 번호별 매핑으로 연결한다.

## S3 경로

기존 private media bucket에 다음 prefix를 사용한다.

```text
reference-audios/announcer/<원본 파일명>
```

애플리케이션 설정은 다음과 같다.

```env
OBJECT_STORAGE_ENABLED=true
OBJECT_STORAGE_REFERENCE_AUDIOS_PREFIX=reference-audios/
```

배포 IAM role에는 `reference-audios/*`에 대한 `s3:GetObject` 권한이 필요하다. 버킷 객체를 public으로 전환하지 않는다.

## 재생 흐름

1. 프론트가 `GET /api/practice-contents/{contentId}/reference-audios`로 기준 음성 목록을 조회한다.
2. `speakerType=ANNOUNCER`이고 `primary=true`인 음성을 선택한다.
3. `GET /api/reference-audios/{audioId}/playback-url`을 호출한다.
4. 백엔드는 `object_key`를 검증하고 10분 만료 presigned GET URL을 반환한다.
5. 아나운서 기준 음성이 있는 `ANNOUNCER` 콘텐츠에는 TTS 음성을 목록에 노출하지 않는다.

## 운영 확인

- S3 객체 key가 migration의 대본별 object key와 정확히 일치하는지 확인한다.
- M4A 객체의 Content-Type은 `audio/mp4`로 업로드한다.
- 파일이 없거나 IAM `GetObject` 권한이 없으면 기준 음성은 조회되지 않거나 재생 URL 발급이 실패한다.
- 파일 원본은 Git, DB bytea, 환경변수에 저장하지 않는다.
