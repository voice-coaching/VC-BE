# 항목별 발음 점수 응답

GET /api/analyses/{analysisId} 및 GET /api/training-sessions/{sessionId}/analysis의 공통 응답 data에 scoreBreakdown을 추가한다. 기존 인증·소유권·완료 조건과 overallScore는 바꾸지 않는다.

- scoreBreakdown: object 또는 null. 상세 v2 근거가 없는 구버전 분석, 아직 점수가 없는 분석, 지원하지 않거나 검증되지 않는 저장 근거는 null이다. 0점이나 평가 대상 없음과 구별한다.
- rubricRevision: string, clova-phone-rubric-v2.
- applicableMaxScore: integer, 표본이 있는 항목의 기본 배점 합.
- items: 아래 순서의 항목 9개. criterionId/string, label/string, description/string, expectedPhones/string[], maxScore/integer, applicable/boolean, sampleCount/integer, level/integer|null, score/number|null을 모두 제공한다.
- score = maxScore × level / 4. level은 기존 0~4 등급이며, 점수는 소수 둘째 자리까지 정확히 표현될 수 있다.
- 문장에 기대 음소가 없는 항목은 sampleCount=0, applicable=false, level=null, score=null. 해당 maxScore는 분모에서도 제외한다.
- 기대 음소가 있었으나 누락된 경우는 제외하지 않는다. 적용 가능한 0등급은 score=0이다.
- overallScore = 100 × 적용 항목 score 합 / applicableMaxScore (소수 1자리 HALF_UP). 개별 점수는 재정규화하지 않는다.
- 새로운 추론·CLOVA 호출·DB 변경 없이 저장된 clova_score_evidence를 검증·투영한다. 원본 카운트, 모델 버전, 내부 해시는 공개하지 않는다.
- 상세/세션 결과 캐시 이름을 v2로 변경해 배포 전에 생성된 DTO 캐시를 재사용하지 않는다.

| criterionId | 항목 | maxScore |
| --- | --- | --- |
| vowels | 모음 | 25 |
| plain_stops | 평음 파열음 | 10 |
| tense_stops | 경음 파열음 | 10 |
| aspirated_stops | 격음 파열음 | 10 |
| fricatives | 마찰음 | 10 |
| affricates | 파찰음 | 10 |
| nasals | 비음 | 10 |
| liquid | 유음 | 5 |
| coverage | 기대 음소 대응 범위 | 10 |

계산 예시(운영 결과 아님): 모음 level=3이면 18.75/25, coverage level=4이면 10/10, 다른 항목에 표본이 없으면 적용 배점은35이고 종합 점수는82.1이다. 모음 등급이 3이라는 이유로 발음 정확도 확률이75%라고 안내하지 않는다.
프론트는 description을 클릭·hover·키보드 포커스로 제공하며, 구버전 서버의 필드 생략/null은 항목별 정보 없음으로 표시한다.
