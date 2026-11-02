# 배포 후 nginx 502: CanonicalDeliverySpool 기동 실패

## 확인한 증상과 원인

2026-11-02 main `7389c79` 배포 이후 nginx가 소셜 로그인 요청에 HTML 502를 반환했다.
EC2 로그에서 `canonicalDeliverySpool: Invocation of init method failed`, Spring 종료,
systemd의 반복 재시작과 localhost 9091 연결 실패를 확인했다. DB 연결과 Flyway 검증은 통과했다.

`CanonicalDeliverySpool.start()`는 delivery가 활성화되면 `owner.tryLock()`을 호출하지만
파일 채널을 초기화하는 코드가 빠져 있었다. 이 경로는 null 참조로 기동을 중단한다.
테스트의 기본 delivery 비활성 설정으로는 드러나지 않는다.

## 수정과 검증

`owner.lock`을 `FileChannel.open(CREATE, WRITE)`으로 연 뒤 기존 잠금 획득을 실행한다.
기존 저장 디렉터리·복구 문서·API·DB 구조는 변경하지 않는다.
활성 스풀의 시작, 종료 시 잠금 반환, 재시작과 비활성 스풀의 파일 미생성을 검증한다.

배포 후 `alpha-backend.service`가 반복 재시작 없이 유지되고,
`http://127.0.0.1:9091/internal/actuator/health`가 200/UP인지 확인한 뒤 사용자 요청을 재확인한다.
systemd의 일시적인 `active (running)` 표시만으로 Spring 기동 완료를 판단하지 않는다.

현재 main 배포 스크립트는 restart 요청 후 실제 health를 기다리지 않으므로 Actions 성공과
서비스 정상 기동은 다를 수 있다. 이 수정은 배포 정책·자동 rollback을 변경하지 않는다.
스풀 파일을 삭제하거나 처리 대기 데이터를 비우는 조치는 필요하지 않다.
