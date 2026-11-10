# 팝업 이미지 첨부·만료 정리

기존 팝업 문구·게시 기간 API는 유지한다. 관리자 팝업을 먼저 저장한 뒤 이미지 하나를 별도로 첨부·교체·해제한다. AI 분석 계약은 변경하지 않는다.

## API

모든 변경은 Bearer 인증·현재 활성 ADMIN 검사와 `Cache-Control: no-store`를 적용한다.

| Method | 경로 | 요청 |
| --- | --- | --- |
| POST | `/api/admin/popups/{popupId}/image` | multipart/form-data: `file`(파일), `version`(0 이상 정수), `reason`(공백 제외 1~500자), 모두 필수 |
| DELETE | `/api/admin/popups/{popupId}/image` | JSON `{version,reason}`, 필수·non-null |

POST 파일은 PNG/JPEG, 원본 1바이트~3MiB다. 서버가 실제 형식과 가로/세로 각 1~4096, 총 800만 픽셀 이하를 확인하고 PNG로 다시 인코딩한다. 결과는 최대 8MiB다. 원본 파일명·메타데이터·임의 objectKey·외부 URL은 저장 요청으로 받지 않는다.

성공은 HTTP 200, 기존 공통 `{result:true,message,data:PopupDto}`. 이미지를 변경하면 팝업 version이 증가한다. 기존 팝업 조회/목록과 `/api/users/me/popup`의 팝업 객체에 nullable `image`가 추가된다. 기존 텍스트 팝업은 null이다.

| image 필드 | 타입 | 의미 |
| --- | --- | --- |
| id | UUID string, 필수·non-null | 첨부 식별자 |
| url | HTTPS string, 필수·non-null | 사설 S3 GET 서명 URL. DB에 저장하지 않음 |
| expiresAt | offset datetime string, 필수·non-null | 팝업 종료 시각. 이미지 노출 기한 |

GET URL은 최대 5분 또는 팝업 종료까지 남은 시간 중 짧은 값으로 서명한다. URL의 실제 인증 수명은 expiresAt보다 짧을 수 있으므로 재편집/새 조회 시 다시 받는다. 응답·URL을 장기 캐싱하지 않는다.

400 VALIDATION_ERROR: 이미지 형식/크기/픽셀/사유/요청값 오류. 미인증은 401, 일반·해제 관리자 403, 팝업 없음 404, version 불일치/이미 만료된 팝업에 첨부는 409 ADMIN_EDIT_CONFLICT. S3/DB 실패는 503 ADMIN_STORE_UNAVAILABLE. 변경 실패/응답 유실은 자동 재전송하지 않고 팝업을 재조회한다.

## 저장과 삭제

- 기존 `storage.bucket`의 **`popup-images/{UUID}.png`**만 사용한다. S3는 가상 경로이므로 첫 업로드 때 경로가 생긴다. 녹음/기준 음성/프로필 경로는 변경하지 않는다.
- 업로드 전 독립 트랜잭션으로 PENDING 파일 기록을 저장한다. 업로드 후 팝업을 잠그고 version·현재 ADMIN·종료 시각을 다시 검사해 첨부한다. 외부 저장 실패나 첨부 커밋 실패로 남은 PENDING은 24시간 후 정리한다.
- 교체/해제한 이전 이미지는 RETIRED가 되어 다음 정리 대상이 된다. 만료 전 설정을 연장하면 같은 이미지의 삭제 기준도 새 endsAt을 따른다. 삭제가 이미 끝난 이미지는 연장으로 복원하지 않고 새로 첨부한다.
- 만료 시각부터 조회 응답은 image=null이다. 전용 스케줄러가 기본 60초 간격, 배치 20개로 S3 DeleteObject를 실행하고 성공 후 DELETED로 표시한다. 팝업/첨부 변경과 같은 팝업 행 잠금을 사용하므로 정리 대상 선정 뒤 기간이 연장되어도 삭제 전에 최신 기한을 재확인한다.
- S3 삭제 실패 시 상태를 완료 처리하지 않고 이후 주기에 재시도한다. 여러 서버가 같은 항목을 선택해도 잠금·상태 재검사·S3 삭제의 멱등성으로 처리한다. 삭제는 초 단위 동시 실행을 보장하지 않으며 서비스 중단 동안 지연될 수 있다. 팝업 비활성화만으로는 파일을 즉시 삭제하지 않는다.

## 배포·S3 권한

Flyway **V72__admin_popup_images.sql**을 백엔드와 먼저 배포하고 프론트를 배포한다. 기존 텍스트 행은 보존한다. 기존 storage.enabled/region/bucket/S3Client/S3Presigner 설정을 재사용한다. 실행 IAM에는 이 버킷의 `popup-images/*`에 PutObject/GetObject/DeleteObject가 필요하다. 예시는 `docs/architecture/popup-images-s3-policy.json`이다. 정책 예시를 실제 버킷명으로 치환·검토해 기존 정책에 병합하며 기존 정책을 통째로 덮어쓰지 않는다.

**고정 30일 S3 lifecycle 규칙은 적용하지 않는다.** 서비스가 팝업 기한에 맞춰 삭제한다. 버킷 전체나 이 prefix에 더 짧은 기존 lifecycle 삭제가 걸려 있지 않은지 확인한다. 버전 관리 버킷에서 DeleteObject는 삭제 마커를 만들므로 과거 버전의 물리 보관은 해당 버킷의 비현재 버전 정책을 따른다. 이 변경은 운영 IAM/버킷 설정을 직접 수정하거나 운영 파일을 삭제하지 않는다.

## 검증

PopupImageIntegrationTest: 실제 multipart/인증/version·교체·해제·원문 보존·만료·기간 연장·정리 재시도·중단된 업로드·픽셀 제한. S3PopupImageStorageTest: 전용 prefix·private/no-store·실패 전달·서명 기한 제한. 기존 알림·팝업 회귀와 AdminCommunicationsPostgresTest의 V63→V72 마이그레이션·첨부 1개 부분 고유 인덱스도 검증한다. S3는 대역이며 실제 운영 업로드/삭제 성공을 의미하지 않는다.

최종 결과: 이미지 통합 8개, S3/조회 기한 2개, 기존 알림·팝업 13개, 격리된 로컬 PostgreSQL 마이그레이션·동시 알림 회귀 1개, 총 24개 통과. 운영 DB는 사용하지 않았다.
