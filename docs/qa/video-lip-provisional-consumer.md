# 영상 입술 임시 점수 연동 검증

기준: main `f48a6c8`, AI 공유 계약 `c312ca78c7b84e6be49a1ee5ddfb0dcf3565aeb1`.
운영 DB나 AI 서버에 테스트 데이터를 쓰지 않고 로컬에서 검증한다.

## 계약·저장·접수

Java 21 / PostgreSQL 18에서 다음 17개 테스트가 통과했다.

| 테스트 | 수 | 확인 내용 |
| --- | --- | --- |
| LipProvisionalContractTest | 6 | 임시 점수·측정값 계약, 0/null 보존, 선택 필드 생략, 기존 음성 결과 호환, 잘못된 값 거부 |
| LipMediaPostgresTest | 1 | Flyway 적용, 실제 준비 영수증·실행 연결, 결과 저장과 공개 조회 왕복, 다른 영상 해시 거부 |
| LipAudiovisualAdmissionTest | 2 | 런타임 확인 전 VIDEO 접수 차단, digest 비교 정책 유지 |
| UnifiedV6PublisherTest 중 영상 관련 | 3 | 실제 준비 정보 전송, 영수증 없는 영상 거부, 준비되지 않은 VIDEO 거부 |
| VoiceRecordingServiceTest | 5 | 녹음 등록·업로드 확인·중복 및 정리 흐름 |

`bootJar`도 성공했다. 전체 테스트 통과를 의미하지 않는다. 첫 확대 실행에서 기존
`UnifiedV6PublisherTest`의 admission budget 예약 및 구 writer 거부 테스트 2개가 실패했다.
이 테스트가 요구하는 동작은 현재 main에서 제거되어 있어 이번 변경으로 복구하지 않았다.

## 실제 미디어 변환

로컬 Linux의 FFmpeg/ffprobe로 합성 미디어를 변환하는 기존 테스트 10개 중 9개가 통과하고 1개가 제외됐다.
H.264 MP4, HEVC QuickTime, VP8/Vorbis 및 VP9/Opus WebM, PCM WAV와 길이 정보가 없는
WebM을 대상으로 하며, 허용되지 않은 코덱·다른 회원 경로·디코딩 후 길이 초과도 확인한다.
개인 기기의 원본 영상은 제공되지 않아 해당 조건부 테스트 1개는 제외됐다.
Windows에서 컴파일·검증한 동일 Java 클래스를 Linux에서 실행했으며, 미디어 도구 필수 설정
`VC_BE_REQUIRE_MEDIA_TOOLS=true`를 사용해 FFmpeg 부재로 조용히 건너뛰지 않도록 했다.

## 운영 확인 범위

- 운영 VIDEO 활성화 플래그는 변경하지 않았다.
- 실제 사용자의 영상 업로드 → AI 실행 → 공개 결과 조회는 배포 후 별도 검증이 필요하다.
- 합성 파일 변환과 로컬 저장 왕복만으로 실제 AI 영상 분석 성공을 선언하지 않는다.
- 분석 171의 `runpod_execution_failed`와 반복 409의 운영 원인을 해결한 검증은 아니다.

변경 계약과 프론트 연동 방법은 [백엔드 연동](../api/video-lip-provisional-consumer.md),
[프론트 전달](../api/frontend-video-lip-consumer.md)을 참고한다.
