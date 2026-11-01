# 코칭 사용량 제한

관련 이슈: #157. BM의 코칭 횟수·기간·입력 상한을 서버에서 검사한다. 새 접수 및 실패 재시도에 적용하며, 과거 결과나 적용 전 접수한 분석을 소급 차감하지 않는다.

| 이용등급 | 제공량 | 기간 |
| --- | --- | --- |
| free | 계정당 최초 8회, 월별 리셋 없음 | 신규 회원은 가입 시점, 기존 회원은 V59 적용 시점부터 UTC 달력 한 달 |
| paid | 결제 이용기간당 30회 | 현재는 기존 TEST 결제의 paidAt 이상, expiresAt 미만 |
| admin | 테스트용 횟수 제한 면제 | 기존 테스트 grant 유지 |

기존 free 회원의 체험 시작을 적용 시점으로 정하는 전환 정책과 admin 면제는 사용자 확인을 받았다. 가입 시점과 V59 적용 시점 중 늦은 값을 한 번의 체험 시작으로 사용한다. 등급 변경·환불·재시작·재배포로 체험을 다시 지급하지 않는다. 한 달은 고정 30일이 아니다. 1월 31일 시작이면 2월 말일 같은 UTC 시각에 끝난다. 끝 시각부터 신규 요청을 거절하며 이미 예약한 분석은 완료할 수 있다.

현재 paid는 **TEST 이용권**이다. 유효한 TEST PAID 주문 중 만료일이 가장 늦은 주문을 사용하고 동률이면 주문 ID 내림차순으로 선택한다. 해당 주문별 원장을 유지하므로 동일 결제의 검증·웹훅 재전달은 한도를 갱신하지 않는다. 취소/만료/결제 기능 비활성화 이후 새 접수에는 그 이용권을 사용하지 않는다. 이미 접수한 작업은 접수 당시 이용권에서 정산한다. 무료 체험 잔여분은 원래 무료 기간 안에서만 사용하며, 유료 한도 소진 시 자동으로 무료 횟수를 합산하지 않는다.

## 차감과 예약

- 새 녹음의 유효 코칭 1건은 1회다. 재녹음은 다른 녹음이므로 추가 1회다.
- 접수 시 1회를 RESERVED로 예약한다. `used + reserved < limit`일 때만 같은 트랜잭션에서 분석 및 outbox를 접수한다. 요청 실패/rollback 시 예약도 없다.
- 유효한 코칭 완료는 CONSUMED로 확정한다. legacy는 COACHING_READY/COMPLETED_NO_ISSUE, canonical은 검증된 ACCEPT 중 실제 피드백 제공 가능(READY + feedbackDeliveryAllowed) 또는 유효 근거 점수가 있는 결과다.
- 무음·재녹음 요청·판단 보류·분석 실패·타임아웃처럼 코칭을 제공하지 못한 결과는 RELEASED로 반환한다. canonical REJECT/INCONCLUSIVE/SYSTEM_FAILURE와 피드백·유효 점수를 제공하지 못한 ACCEPT는 차감하지 않는다.
- 동일 결과 이벤트를 재전달하거나 결과/녹음을 다시 조회해도 추가 차감하지 않는다. 실패 재시도는 현재 한도를 다시 검사하고 같은 분석의 반환된 예약을 갱신한다. 성공한 결과를 재시도하여 차감하지 않는 우회는 허용하지 않는다.
- 학습 기록이나 완료 분석을 삭제해도 CONSUMED 원장은 보존한다. 아직 실행 중인 분석이 삭제되면 예약을 반환한다. 실제 사용자 삭제 시에는 사용자 FK 정책으로 원장도 제거한다.
- 원고 상한은 공백·문장부호를 포함한 Unicode code point 300자, 음성은 등록된 분석 입력 기준 90,000ms다. 테스트 admin도 입력 상한과 기존 음질/동의/동시 실행/권한 검사는 유지한다.

분석 접수·사용량 검사는 동일 사용자 행 잠금으로 직렬화한다. DB trigger는 모든 결과 저장 경로(JPA, canonical, stream, 직접 SQL 실패/타임아웃)의 확정 상태를 같은 트랜잭션으로 원장에 반영한다. 요금제 선택과 초과 여부 검사는 application이 담당한다. canonical 진단 JSON을 SQL로 해석하지 않고 검증된 application commit이 기록한 `coaching_delivered`를 사용한다. 원장에는 원고·녹음·이메일·토큰을 저장하지 않는다.

## GET /api/users/me/coaching-usage

Authorization: Bearer Access Token 필수. 로그인한 본인만 조회하며 응답은 `Cache-Control: no-store`다. query/body/path에 사용자 ID를 받지 않는다.

```json
{
  "result": true,
  "message": "코칭 사용량을 조회했습니다.",
  "data": {
    "membershipType": "free",
    "environment": null,
    "unlimited": false,
    "startsAt": "2026-11-01T00:00:00Z",
    "expiresAt": "2026-12-01T00:00:00Z",
    "limit": 8,
    "used": 3,
    "reserved": 1,
    "remaining": 4,
    "canRequestCoaching": true
  }
}
```

필드는 모두 존재한다. `membershipType`은 free/paid/admin, `environment`는 free일 때 null, TEST paid/admin은 TEST다. admin의 `unlimited=true`, `limit/remaining/startsAt/expiresAt=null`, `used/reserved=0`, `canRequestCoaching=true`다. admin 이용분은 제한 원장에 기록하지 않는다. 일반 계정에서 remaining은 기간 내 `max(0, limit-used-reserved)`, 만료 시 0이다. used/reserved는 선택한 이용기간의 값이다. `canRequestCoaching`은 요금제 한도만 나타내며 AI 준비 상태나 동시 실행 제한까지 보장하지 않는다. 저장소 장애는 성공으로 처리하지 않는다.

## 기존 분석 API에 추가되는 오류

`POST /api/training-sessions/{sessionId}/analyze` 및 `POST /api/training-sessions/{sessionId}/analysis/retry`의 요청·성공 응답 계약은 유지한다.

| HTTP | code | 의미 |
| --- | --- | --- |
| 403 | COACHING_QUOTA_EXCEEDED | 사용량과 예약을 합산해 기간 한도 소진 |
| 403 | COACHING_PERIOD_EXPIRED | 일회성 무료 체험 기간 종료 |
| 422 | COACHING_SCRIPT_LIMIT_EXCEEDED | 분석 원고가 비었거나 300자 초과 |
| 422 | COACHING_AUDIO_LIMIT_EXCEEDED | 분석 음성이 유효하지 않거나 90초 초과 |

기존 인증/소유권/음질/동의/AI 가용성/동시 요청/중복 요청/재시도 횟수 오류가 먼저 반환될 수도 있다. 거절 시 분석·outbox·동의·예약 변경은 모두 rollback된다. 저장소 오류 시 새 분석은 실행하지 않는다. 프론트는 사용량 조회로 잔여 횟수를 표시하고 위 코드를 처리하되 최종 판단은 서버에 맡긴다. 기존 결과·녹음·콘텐츠 조회는 체험 만료나 한도 소진으로 막지 않는다.

## 적용 범위

본 변경은 코칭 사용 제한이다. TEST 결제를 실결제나 자동 구독으로 전환하지 않으며 빌링키/정기결제/자동 갱신/환불 조건 변경/30일 보관 정책 자동 삭제를 새로 구현하지 않는다. 무료 체험 종료만으로 결제를 시작하지 않는다. BM의 실구독 출시에는 별도 결제 및 보관 정책 구현·검증이 필요하다.
