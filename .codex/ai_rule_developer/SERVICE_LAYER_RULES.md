# 서비스 계층 규칙 — VC-BE

## 책임과 계약

- 유스케이스 서비스는 feature/application에 두며, 생성자 주입을 사용한다. 기존 @RequiredArgsConstructor와 final 필드 관례를 따른다.
- HTTP Request/ResponseEntity, Servlet 타입이나 외부 SDK 모델을 신규 service 계약으로 쓰지 않는다.
- 입력·결과는 명시적 타입과 domain/model을 사용한다. public API entity 반환은 금지한다.
- createSession, requestAnalysis, updateMyProfile처럼 동작을 드러내는 이름을 사용한다.
- 의미 있는 유스케이스 흐름은 서비스에서 읽을 수 있게 유지한다. 줄 수만으로 helper를 쪼개지 않는다.

## 변경 흐름

1. 인증 사용자 식별자와 입력을 전달받고 소유권·사용 가능 상태를 검증한다.
2. port로 필요한 데이터와 동시성 제어를 확보한다.
3. 엔티티 불변조건을 지키며 상태 전이를 수행한다.
4. 동일한 DB 원자성이 필요한 저장·동의·outbox를 하나의 트랜잭션에 포함한다.
5. 결과 모델을 반환하고 HTTP 변환은 Controller/응답 DTO에 맡긴다.

- @Transactional 경계를 명시한다. 조회는 필요한 경우 readOnly=true를 사용한다.
- self-invocation, 커밋 이후 실패, 잠금 순서, 중복 요청 및 사용자별 동시 작업 상한을 변경 시 확인한다.
- 승인·동의·소유권 확인을 adapter의 성공 여부로 대체하지 않는다.
- 엔티티 내부의 유효한 상태 변경 메서드는 유지하고, application에서 유스케이스 순서를 조정한다.
- 기존 BaseException/BusinessException/ErrorCode 체계를 사용한다. 메시지를 파싱해 비즈니스 상태를 판단하지 않는다.

## 분석·미디어 유스케이스

- 분석 접수는 완료가 아니다. DB 결과 상태와 outbox 발행, worker 수신, callback 반영을 구분한다.
- retry는 기존 계약에 따라 새 요청/실행 세대를 사용한다. 이전 세대·취소 후 결과가 상태를 복원하지 않도록 한다.
- 스토리지 정규화·삭제의 보상 처리와 삭제 outbox를 유지한다. DB rollback만으로 외부 파일이 되돌아간다고 가정하지 않는다.
- 장시간 네트워크·미디어 작업을 잠금 트랜잭션 안에 추가하기 전에 기존 adapter와 outbox 흐름을 확인한다.
- 인증·상태·동시성·실패 부수효과가 바뀌면 해당 service 테스트와 flow/state 명세를 갱신한다.
