# 배포 JAR 자동 보관·정리

이슈 #190. 정상 배포가 누적될 때 실행 JAR이 디스크를 채우는 문제를 줄인다.
새 HTTP API·환경변수·DB 마이그레이션은 없다.

## 실행 시점

production workflow가 JAR을 활성화한 뒤 `jar_retention.py --apply`를 실행한다.
배포와 정리는 같은 `deployment.lock`을 배타적으로 사용하고 workflow도
`production-deploy` 그룹에서 실행 중인 작업을 취소하지 않고 직렬화한다.

정리 전에 다음 조건을 확인한다.

- workflow가 지정한 release ID·JAR SHA-256과 현재 원장·실행 링크가 일치한다.
- 서비스가 active/running이고 같은 프로세스에서 health UP이 3회 연속 확인된다.
- 해당 서비스 프로세스가 지정한 JAR을 실제로 열고 있다.
- 다른 배포 프로세스가 없고 참조·파일 조사를 완료할 수 있다.

health 확인은 최대 약 60초 동안 재시도한다. 이 확인은 기동 상태 확인이며
모든 API의 기능 검증을 뜻하지 않는다. 기동 실패 시 정리하지 않는다.
기존 배포의 활성화·재시작 정책 자체를 변경하거나 실패 시 자동 롤백을 추가하지 않는다.

## 보관 기준

기본 보관은 **현재 실행본 1개 + 정상 기동이 확인된 이전 배포 15개**다.
`/var/lib/alpha-deploy/jar-retention/healthy.json`에 검증한 release ID·JAR hash·시각을
기록한다. 이 작은 원장은 최대 512건을 유지한다.

도입 초기의 과거 JAR에는 정상 기동 확인 원장이 없다. 확인된 이전 버전이 15개보다
적을 때는 부족한 개수만큼 최근 SHA명 JAR을 보수적으로 남긴다. 이 파일들을
정상 검증 완료 버전으로 표시하지 않는다. 확인된 기록이 쌓이면 실제 정상 버전 15개를
우선한다.

다음 파일은 기본 보관 개수와 별도로 보호한다. 따라서 실제 남는 수는 16개를 넘을 수 있다.

- Java 프로세스가 실행하거나 열고 있는 JAR
- 운영·테스트 링크 및 `alpha-*` 서비스가 참조하는 JAR
- 현재의 직전 롤백 JAR과 선택한 보관 버전의 바로 이전 JAR
- `baseline.jar`, `canonical-*.jar` 등 SHA 이외 이름의 복구용 파일
- 배포 state가 없어 용도를 확인할 수 없는 수동·과거 SHA명 JAR

현재 롤백 참조가 없거나 유효하지 않으면 정리하지 않는다. 과거의 승인된 정리로 이미
사라진 **보관 버전의 옛 predecessor**는 결과에 `missing_archived_predecessors`로
알린다. 없는 파일을 다시 만들거나 현재 롤백 참조 누락을 허용하는 규칙은 아니다.
이전의 모든 state가 가리키는 JAR을 재귀적으로 보존하지는 않는다. 그런 방식은 전체
배포 이력을 영구 보관하게 된다.

오래된 JAR을 지우면 그 버전으로 즉시 로컬 롤백하는 선택지도 사라진다. 남겨둔 JAR도
현재 DB migration과의 호환성까지 자동 보증하는 것은 아니다. DB를 되돌리는 기능은 없다.

## 삭제 경계와 실패 처리

삭제 범위는 `/opt/alpha/releases` 바로 아래의 40자리 소문자 SHA 이름을 가진
일반 `.jar` 파일뿐이다. 심볼릭 링크·외부 경로·하위 디렉터리·알 수 없는 파일 유형은
삭제하지 않는다. DB·녹음·원고·로그·백업·업로드 임시 파일은 정리 대상에 포함하지 않는다.

매 파일 삭제 전 현재 state·실행/서비스 참조·프로세스·health·파일 inode/크기/수정 시각을
다시 대조한다. 열린 디렉터리 FD를 기준으로 정확한 basename 하나만 unlink하며 재귀 삭제는
사용하지 않는다. 각 삭제 전 예정 파일을 기록하고 삭제 후 완료 기록을 남긴다.

검사 실패 시 남은 정리를 중단하며 실행 중 서비스를 중지하거나 재시작하지 않는다.
workflow의 정리 단계는 실패로 표시된다. 배포 활성화 성공과 정리 성공을 구분해 점검한다.
이미 삭제된 파일은 자동 복구하지 않는다. 부분 실행 결과는 원장으로 확인한다.

## 수동 확인

업로드된 해당 배포의 deployment 디렉터리에서 아래처럼 실행할 수 있다.
`--expected-release`와 `--expected-jar-sha`는 현재 운영 원장의 실제 값으로 대체한다.

```bash
sudo python3 -B jar_retention.py \
  --expected-release <40자리-current-release> \
  --expected-jar-sha <64자리-current-JAR-SHA256>
```

기본은 dry-run이며 파일 삭제나 정상 기동 원장 기록을 하지 않는다. `--apply`를 추가할 때만
삭제·기록한다. 운영 경로는 CLI로 변경할 수 없다.
영수증은 `/var/lib/alpha-deploy/jar-retention/cleanup-*.json`에 root 전용으로 저장한다.

Linux에서 다음 회귀를 실행한다.

```bash
python3 -m unittest discover -s scripts/deployment -p 'test_*retention*.py'
```

CI/CD 모두 이 회귀를 실행한다. 기존 Java 전체 QA의 실패·미완료 상태를 이 테스트의 성공으로
덮지 않는다. 이 기능은 기존 인증 401 회귀나 개별 API의 500 오류를 수정하지 않는다.
