# develop·main 자동 배포

두 브랜치의 push(일반적인 PR 병합 포함)가 기존 `production` 환경의 같은 EC2·서비스·DB로 배포된다. 서버나 Secret을 추가하지 않는다. `main`을 별도 운영 환경으로 분리한 설정은 아니다.

## 적용 순서

1. 이 변경의 PR을 `develop`에 병합한다. 이후 develop 배포부터 아래 검사를 사용한다.
2. 팀원이 `develop → main` PR을 병합해 변경된 workflow와 스크립트를 main에도 반영한다. 그 push부터 main 자동 배포가 실행된다.
3. 두 브랜치의 PR은 CI에서 테스트·빌드와 배포 정책 테스트를 실행한다. 배포 workflow는 실제 병합 커밋을 다시 테스트한 뒤 같은 작업에서 만든 JAR를 전송한다.

직접 main push나 PR 병합은 이 변경 작업에서 수행하지 않는다. 테스트·브랜치 정책 실패를 우회하는 수동 배포는 자동화하지 않는다.

## 공유 운영 서버 보호

- push 및 수동 실행은 `develop`/`main` 브랜치만 허용한다. 태그와 기능 브랜치는 배포하지 않는다.
- `production-deploy` 공통 concurrency group, `cancel-in-progress: false`, `queue: max`로 실행 중 배포를 끊지 않고 대기 배포를 공유한다. 큐 순서에 의존하지 않고 활성화 직전 별도 Git 검사를 수행한다.
- 현재 원격 브랜치 최신 SHA와 후보가 다르면 `SUPERSEDED_BRANCH_COMMIT`으로 활성화를 생략한다.
- 현재 운영 릴리스의 전체 Git 파일 트리가 후보의 이력에 있어야 한다. Java뿐 아니라 migration·빌드·workflow·배포 스크립트의 상태도 포함한다. 기존 운영 변경을 포함하지 않은 과거/분기 브랜치는 `LIVE_CODE_NOT_INCLUDED_SYNC_BRANCH_FIRST`로 중단한다.
- main 병합 커밋과 develop 선조의 전체 파일 트리가 같으면 같은 코드 상태로 인정한다. merge commit SHA만 다르다는 이유로 정상적인 다음 develop 배포를 차단하지 않는다.
- SSH에서 배포 잠금을 공유 모드로 잡고 root 소유 current state와 실제 JAR SHA-256을 확인한다. 진행 중인 배포/보류 상태/변조·불완전한 상태는 거부한다.
- 기존 `/opt/alpha`의 서비스 사용자 소유권은 유지한다. 이 경로 외의 versioned release 디렉터리·JAR·상위 경로는 root 소유, symlink 및 group/world 쓰기 금지를 검사하고 root current state의 정확한 JAR digest와 대조한다. 운영 파일 권한을 자동 변경하지 않는다.
- 활성화할 때 기존 배포 잠금의 독점 모드 안에서 예상 current release·JAR fingerprint를 다시 확인한다. 검사 후 운영 릴리스가 바뀌면 nginx reload, admission hold, drain, 재시작 전에 `CURRENT_RELEASE_CHANGED`로 중단한다.
- 기존 분석 작업 drain·JAR 해시 검증·연속 health 확인·수동 rollback 절차를 유지한다. 실패 시 HOLD를 무조건 삭제하거나 DB migration을 되돌리지 않는다.

| 상황 | 결과 |
|---|---|
| 최신 main이 현재 운영 develop 변경을 포함 | 자동 배포 |
| main 병합 전 develop 상태와 같은 트리를 가진 main이 배포된 뒤, 더 최신 develop | 자동 배포 |
| 운영보다 오래된 main / 운영 main hotfix를 빠뜨린 develop | 거부. 변경을 대상 브랜치에 병합한 뒤 새 커밋으로 재실행 |
| 후보보다 최신 커밋이 이미 같은 원격 브랜치에 존재 | 구버전 후보 활성화 생략 |
| live snapshot 이후 운영 JAR/릴리스가 변경 | 활성화 거부, 현재 운영 보존 |

Git 이력 검사는 실제 의미상의 회귀나 의도적인 revert를 판별하지 않는다. branch protection/리뷰와 자동 테스트가 계속 필요하다. 두 브랜치에 서로 다른 변경을 만든 경우 어느 한쪽을 강제로 덮어쓰지 말고 먼저 동기화한다.

## 검증

```bash
python -B -m unittest discover -s scripts/deployment -p 'test_*.py'
python -B -m unittest discover -s scripts/payment-sandbox -p test_settings.py
./gradlew clean test bootJar --no-daemon
bash -n scripts/deploy.sh
```

실제 임시 Git 저장소에서 정방향/역방향/분기·hotfix·동일 트리 main merge·superseded·잘못된 ref와 SHA를 검사한다. snapshot/JAR 변조·불완전한 운영 상태·진행 중 배포·검사 후 릴리스 변경은 서버 조작을 실행하지 않는 테스트로 검증한다. 실제 `flock` 검사는 Linux에서 실행한다.

GitHub의 [`queue: max` 공식 문서](https://docs.github.com/en/actions/how-tos/write-workflows/choose-when-workflows-run/control-workflow-concurrency)를 기준으로 대기 정책을 설정했다. actionlint 1.7.12는 해당 필드를 아직 인식하지 않으므로 이 필드의 단일 문법 경고만 제외하고, `test_workflow_contract.py`에서 정확한 키·값 조합을 별도로 검증한다. 그 외 workflow lint 오류는 제외하지 않는다.
