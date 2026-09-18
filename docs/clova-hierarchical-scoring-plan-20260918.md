# CLOVA 음성·입술 계층형 채점표 개편 계획

작성: 2026-09-18 KST. 상태: **코드 조사에 근거한 설계안, 미구현·미배포**.

## 1. 결정 제안

현재 9개 평면 항목을 **11개 대분류 / 49개 소분류**로 확장한다. 대분류 숫자는 항목 수에 중복 산입하지 않는다.

- 음성: 기존 40개 기대 음소 표지를 각각 평가하고 정렬 커버리지 1개를 더해 **41개 소분류**. 현재 파이프라인 출력으로 계산할 수 있으나 집계·계약·화면 구현이 필요하다.
- 입술: 형상 4개와 움직임 4개로 **8개 소분류**. 현재 데이터로 일부 측정은 가능하지만, 전문가가 검토한 음소·문맥별 기준과 추가 계측이 없으므로 즉시 발음 점수로 활성화하지 않는다.
- 카탈로그에 49개가 있다고 한 문장에서 49개를 모두 평가하지 않는다. 기대 음소가 없는 항목은 NOT_APPLICABLE, 영상이 없는 항목은 NOT_PROVIDED, 계측 실패는 UNAVAILABLE, 기준 미검증은 NOT_VALIDATED로 구분한다.
- 수치 계산은 버전이 있는 계산기, CLOVA는 근거 해석·항목별 설명·연습 안내를 담당한다. CLOVA가 임의로 점수나 입술의 이상적인 크기를 만들지 않는다.
- 모델 가중치, Seungun detector threshold, 현재 운영 점수, 기존 분석 이력은 이번 계획 작성 과정에서 변경하지 않았다.

**49개는 서로 독립적인 49가지 발음 능력이 아니다.** 음성 항목은 동일 검출 근거를 음소별로 세분화한 것이고, 영상 항목 일부는 서로 상관된 관측이다. 세분화의 효과는 어느 기대 음소에서 어떤 근거가 부족한지 구체적으로 보여 주는 데 있다. 정확도 개선이나 인간 채점 일치도 향상을 이 설계만으로 주장하지 않는다.

## 2. 확인 범위와 현재 구조

AI 검토 체크아웃 HEAD: `90ffc747539e5f650a62ffaa21d688ddc69cba16`.
백엔드 항목별 공개 응답 검토본: `b4bfb13907cccca5c175351650ef74c49f1cd1d9` (PR #82). 이 SHA를 운영 배포 SHA로 간주하지 않는다.

RunPod 배포 폴더에는 .git이 없어 Git HEAD를 얻지 못했다. 대신 아래 7개 파일의 SHA-256을 비교했다. Windows CRLF를 LF로 정규화하면 로컬 소스와 RunPod 파일이 일치했다.

| 파일 | 확인한 역할 |
| --- | --- |
| `src/voice_coach/backend_analysis/service.py` | 음성 결과 → 영상 보조 근거 → 피드백 → 채점 호출 |
| `src/voice_coach/backend_analysis/clova_scoring.py` | 현재 9항목 근거 집계, 고정 기준 계산, CLOVA 응답 검증 |
| `configs/pronunciation_feedback/clova_pronunciation_rubric_v2.json` | 현재 기준표·배점·등급·제외 항목 |
| `seungun/src/korean_phone_ctc/frozen_detector.py` | 음소별 실제 출력 스키마 |
| `src/voice_coach/backend_analysis/adapters/visual.py` | 선택 음소의 영상 근거 생성과 공개 projection |
| `src/voice_coach/pronunciation_attempts/adapters/production_visual_sidecar.py` | 영상 근거 및 closed-beta 구간 집계 |
| `src/voice_coach/visual_feedback/services/p6_lip_trajectory.py` | 입술 landmark 매핑과 시간별 형상 계산 |

이는 배포 파일의 정적 대조다. 실행 중 프로세스가 이미 적재한 모듈, 영상 기능의 활성 설정, 신규 요청의 실제 출력까지 확인한 것은 아니다. 새 분석·LLM 호출·서비스 재시작은 하지 않았다.

현재 `service.py:146`은 영상 근거를 피드백 renderer에 전달하지만, `service.py:150`의 채점 호출은 `self._clova_scorer.score(analysis)`다. **현재 CLOVA 채점 입력에 영상 근거는 없다.**

`ClovaRubricScorer.score()`는 음성의 각 음소를 6가지 상태로 집계하고 기준표·검산표를 CLOVA에 보낸다. `observed_candidate`, soft feature 전체, 영상 landmark 전체를 채점 프롬프트에 보내는 구조가 아니다.

## 3. 실제 데이터와 점수화 가능 범위

| 입력/출력 | 현재 코드에서 확인한 데이터 | 활용 결정 |
| --- | --- | --- |
| 기대 발음 | g2pK 기반 `expected_phones`, 음소 인덱스 | 원문 글자가 아니라 실제 기대 표지로 항목 적용 여부 판단 |
| 음소 정렬 | `alignment_operation`: correct/substitution/deletion, `observed_candidate` | 기대 표지별 대응 근거·대치/누락 후보 집계. 사람의 확정 오류나 STT로 표현하지 않음 |
| 검출 | `error_score`, `operating_threshold`, `is_error` | 기존 동결 판정으로 6상태 집계. 점수 크기를 정확도 확률로 변환하지 않음 |
| 전체 정렬 | `ctc_hypothesis`, `insertions`, 기대 음소 수 | 추가 표지 후보·전체 대응률 표시 가능. 삽입은 위치 정보가 제한돼 특정 단어 오류로 귀속하지 않음 |
| 음소 시각 | `start_ms`, `end_ms` 또는 null | 증거 위치·영상 연결에 사용. CTC spike 길이를 실제 조음 지속시간·말속도로 채점하지 않음 |
| soft feature | `expected_prob`, `competitor_prob`, `expected_margin`, `predicted_prob`, `entropy`, `blank_prob`, `has_predicted_span`, `hard_error`, `span_seconds` | 내부 진단·검토 후보. 서로 상관된 수치를 독립 점수로 더하지 않음. 새 기준 검증 전 사용자 점수 제외 |
| 입술 정적 형상 | `mouth_width_fraction`, `outer_aperture_ratio`, `inner_aperture_ratio`, `outer_area_ratio`, `inner_area_ratio`, `corner_height_asymmetry_ratio` | 일부 관측값 존재. 카메라 거리·방향, 음소 문맥별 비교 기준 필요 |
| 입술 시계열 | 내부 `timestamp_ms`, `visible_shape`, `shape_change_rate_per_second` | 원시 시계열은 내부 메모리에서 사용 가능. 현재 공개 관찰은 구간 평균과 최대 변화율로 축약됨 |
| 영상 계측 조건 | 얼굴 수, 유효 프레임, 단일 얼굴 coverage, 영상 구간·음성 앵커·A/V offset 근거 | 채점 가능 여부 gate. 촬영 품질이 나쁘다는 이유로 발음 점수를 감점하지 않음 |
| 승인 영상 설명 | cue 상태, 승인 claim, renderer, 동일 시도 연결 근거 | 승인된 설명만 CLOVA에 전달. 관측과 교정 정답을 구분 |

현재 영상 adapter는 `COACHING_READY`이고 선택 음소가 있을 때만 보조 근거를 생성한다. 해당 선택 구간만으로 문장 전체의 입술 점수를 계산하면 선택 편향이 생긴다. 문장 수준 영상 평가에는 **선택 음소와 무관하게 사전에 정한 적용 구간을 수집하는 별도 경로**가 필요하다.

현재 P6 관찰은 `containsPronunciationTruth=False`, `containsActionTruth=False`이며 `calibrated_deviation=None`이다. geometry artifact는 점 대응 근거이지 발음의 정답 기준이 아니다. 저장소의 `internal_visual_calibration_v1.json`은 `synthetic_contract_only`이므로 실사용 채점 참조로 쓰지 않는다.

## 4. 계층형 평가 카탈로그: 소분류 49개

표의 ‘음성 R’은 §5의 동일 계산 규칙을 해당 기대 표지에 적용한다는 뜻이다. 표지별 항목명은 실제 혀 위치·성대·기류를 측정했다는 뜻이 아니다. g2pK의 실제 표지, 문맥, 허용 발음 변이를 기준으로 적용한다.

| 번호 | 대분류 | 소분류 ID | 평가 항목/대상 | 근거·활성 조건 |
| ---: | --- | --- | --- | --- |
| 1 | A. 모음 | vowel.a | ㅏ 대응 | 음성 R |
| 2 | A. 모음 | vowel.ae | ㅐ 대응 | 음성 R |
| 3 | A. 모음 | vowel.ya | ㅑ 대응 | 음성 R |
| 4 | A. 모음 | vowel.yae | ㅒ 대응 | 음성 R |
| 5 | A. 모음 | vowel.eo | ㅓ 대응 | 음성 R |
| 6 | A. 모음 | vowel.e | ㅔ 대응 | 음성 R |
| 7 | A. 모음 | vowel.yeo | ㅕ 대응 | 음성 R |
| 8 | A. 모음 | vowel.ye | ㅖ 대응 | 음성 R |
| 9 | A. 모음 | vowel.o | ㅗ 대응 | 음성 R |
| 10 | A. 모음 | vowel.wa | ㅘ 대응 | 음성 R |
| 11 | A. 모음 | vowel.wae | ㅙ 대응 | 음성 R |
| 12 | A. 모음 | vowel.oe | ㅚ 대응 | 음성 R |
| 13 | A. 모음 | vowel.yo | ㅛ 대응 | 음성 R |
| 14 | A. 모음 | vowel.u | ㅜ 대응 | 음성 R |
| 15 | A. 모음 | vowel.wo | ㅝ 대응 | 음성 R |
| 16 | A. 모음 | vowel.we | ㅞ 대응 | 음성 R |
| 17 | A. 모음 | vowel.wi | ㅟ 대응 | 음성 R |
| 18 | A. 모음 | vowel.yu | ㅠ 대응 | 음성 R |
| 19 | A. 모음 | vowel.eu | ㅡ 대응 | 음성 R |
| 20 | A. 모음 | vowel.ui | ㅢ 대응 | 음성 R |
| 21 | A. 모음 | vowel.i | ㅣ 대응 | 음성 R |
| 22 | B. 평음 파열음 | plain.g | ㄱ 대응 | 음성 R |
| 23 | B. 평음 파열음 | plain.d | ㄷ 대응 | 음성 R |
| 24 | B. 평음 파열음 | plain.b | ㅂ 대응 | 음성 R |
| 25 | C. 경음 파열음 | tense.gg | ㄲ 대응 | 음성 R |
| 26 | C. 경음 파열음 | tense.dd | ㄸ 대응 | 음성 R |
| 27 | C. 경음 파열음 | tense.bb | ㅃ 대응 | 음성 R |
| 28 | D. 격음 파열음 | aspirated.k | ㅋ 대응 | 음성 R |
| 29 | D. 격음 파열음 | aspirated.t | ㅌ 대응 | 음성 R |
| 30 | D. 격음 파열음 | aspirated.p | ㅍ 대응 | 음성 R |
| 31 | E. 마찰음 | fricative.s | ㅅ 대응 | 음성 R |
| 32 | E. 마찰음 | fricative.ss | ㅆ 대응 | 음성 R |
| 33 | E. 마찰음 | fricative.h | ㅎ 대응 | 음성 R |
| 34 | F. 파찰음 | affricate.j | ㅈ 대응 | 음성 R |
| 35 | F. 파찰음 | affricate.jj | ㅉ 대응 | 음성 R |
| 36 | F. 파찰음 | affricate.ch | ㅊ 대응 | 음성 R |
| 37 | G. 비음 | nasal.n | ㄴ 대응 | 음성 R |
| 38 | G. 비음 | nasal.m | ㅁ 대응 | 음성 R |
| 39 | G. 비음 | nasal.ng | ㅇ 기대 표지 대응 | 음성 R. 원문 초성 ㅇ을 자동 가산하지 않음 |
| 40 | H. 유음 | liquid.r | ㄹ 대응 | 음성 R. 탄설·측음 원인을 자동 진단하지 않음 |
| 41 | I. 대응 범위 | coverage.aligned | 전체 기대 음소 정렬 대응 | 현재 coverage 계산 유지 |
| 42 | J. 입술 형상 | lip.inner_aperture | 안쪽 벌림 비율의 문맥별 적합성 | inner_aperture_ratio + 검증 참조 필요 |
| 43 | J. 입술 형상 | lip.outer_aperture | 바깥쪽 벌림 비율의 문맥별 적합성 | outer_aperture_ratio + 검증 참조 필요 |
| 44 | J. 입술 형상 | lip.inner_area | 안쪽 개구 면적 비율의 문맥별 적합성 | inner_area_ratio + 검증 참조 필요 |
| 45 | J. 입술 형상 | lip.outer_area | 바깥 윤곽 면적 비율의 문맥별 적합성 | outer_area_ratio + 검증 참조 필요 |
| 46 | K. 입술 움직임 | lip.closure_extent | 목표 구간의 입술 닫힘 정도 | 시계열 최소 벌림·유효 프레임·음소 문맥·참조 필요 |
| 47 | K. 입술 움직임 | lip.opening_transition | 문맥별 열림 전환의 방향·변화량 | 시작/끝 구간 요약·시간 provenance·참조 필요 |
| 48 | K. 입술 움직임 | lip.closing_transition | 문맥별 닫힘 전환의 방향·변화량 | 시작/끝 구간 요약·시간 provenance·참조 필요 |
| 49 | K. 입술 움직임 | lip.change_profile | 문맥별 움직임 변화율 패턴 | 여러 유효 프레임의 변화율 분포·참조 필요 |

42~49는 목표 평가 항목이다. 42~45는 측정값을 집계하는 코드가 있지만 **적합성 점수 기준은 미완성**, 46~49는 시간 구간별 새 요약과 검증도 필요하다. 현재 최대 shape-change 값 하나로 빠르다/느리다/좋다/나쁘다를 판정하지 않는다. 46은 양순음 등 적용 문맥을 참조 정책에서 제한하며, 닫힘 수치만으로 실제 음향 폐쇄·기류를 확인했다고 표현하지 않는다.

입술 너비의 이미지 대비 비율은 카메라 거리에 민감하고, 좌우 높이 차이는 고개 기울임·개인차와 혼재한다. 이 두 기존 계측값은 초기에는 품질·보정 참고로만 사용한다. 값이 존재한다는 이유로 미관·대칭성을 발음 점수에 포함하지 않는다.

## 5. 음성 채점과 집계 규칙

### 5.1 소분류 계산 R

음소별 sampleCount와 다음 상태 개수를 보존한다.

| 정렬/검출 상태 | 현재 근거 단위 |
| --- | ---: |
| 일치 후보 / detector 미표시 | 4 |
| 일치 후보 / detector 표시 | 2 |
| 대치 후보 / detector 미표시 | 3 |
| 대치 후보 / detector 표시 | 1 |
| 누락 후보 / detector 미표시 | 0 |
| 누락 후보 / detector 표시 | 0 |

`근거비율 = 근거 단위 합 / (4 × sampleCount)`.
기존 구간 95% 이상→4, 80% 이상→3, 60% 이상→2, 40% 이상→1, 그 미만→0을 유지한다. 비교 전 반올림하지 않는다. 소분류 화면의 정규화 점수는 `25 × level`이며, 근거비율 자체는 확률이 아닌 별도 진단 수치로만 표시한다.

- 기대 표지가 없는 경우 sampleCount=0, level=null, score=null.
- 기대 표지는 있지만 모두 deletion 후보인 경우 실제 0점이 될 수 있다. ‘문장에 없음’과 구분한다.
- 1회만 등장한 표지도 점수를 계산하되 표본 1개 표시를 제공한다. 높은 통계 신뢰도나 확정 발음 진단으로 표현하지 않는다.
- 입력 손상·누락은 ‘평가 대상 없음’으로 바꿔 총점을 올리지 않는다. 필수 음성 근거가 불완전하면 채점 실패/미제공으로 처리한다.
- 세분화는 위치 설명을 개선하지만 기존 5등급의 점수 간격 자체를 촘촘하게 만들지는 않는다. 연속 점수 도입은 인간 평가와의 대조 후 별도 버전으로 검토한다.

### 5.2 기존 종합점수 의미 보존

대분류 배점은 모음25, 평음10, 경음10, 격음10, 마찰10, 파찰10, 비음10, 유음5, 커버리지10을 유지한다.

부모 점수는 자식의 6상태 **개수를 합한 뒤 기존 등급을 계산**한다. 자식 등급의 단순 평균은 사용하지 않는다. 음소 등장 횟수 차이와 등급 양자화 때문에 평균 방식은 기존 점수를 바꾼다.

`overallScore = HALF_UP(100 × Σ(적용 부모 배점 × 부모 level) / (4 × Σ적용 부모 배점), 소수 1자리)`.

부모와 자식 점수를 총점에 중복 합산하지 않는다. coverage도 음성 항목과 근거가 겹치지만 기존 정책 호환을 위해 유지한다. 새 음소별 자식에는 별도 전체 배점을 부여하지 않고 부모 내 진단 점수를 제공한다.

예: 모음 표지만 등장한 문장에서 모음 부모 level=3, coverage level=4라면 `(25×3+10×4)/(4×35)×100=82.1`. 소분류를 늘렸다는 이유만으로 총점이 달라지면 안 된다.

### 5.3 영상 점수 분리

초기 overallScore와 승급 채점에는 영상 점수를 섞지 않는다. 오디오만 제출한 사용자에게 손해를 주거나 같은 발음을 영상 유무로 다르게 승급시키지 않기 위해서다.

입술은 검증 후 `visualScore`라는 별도 보조 점수로 제공하는 안을 우선한다. 참조 버전·카메라 조건·유효 구간·전문가 라벨을 확보하기 전에는 점수 null을 유지한다. 8개를 같은 배점으로 합산하는 방식도 기본값으로 확정하지 않는다. 벌림/면적처럼 상관된 항목의 중복 가중을 검토한 뒤 대분류 한도와 배점을 결정한다.

통합 총점이 제품상 반드시 필요해지면 음성 전용과 음성+영상의 서로 다른 rubric/시험 정책을 정의하고 비교 가능성을 검증한다. 현재 title-exam은 저장된 overallScore를 사용하므로 기존 합격선에 영상 점수를 바로 섞지 않는다.

## 6. 영상 항목 활성화에 필요한 작업

1. **문맥별 참조:** 기대 음소, 앞뒤 음소, 음절 위치, 허용 발음 변이, 촬영조건을 가진 전문가 검토 참조를 준비한다. ‘크게 열면 고득점’ 같은 단조 규칙은 금지한다. 최소·최대·목표 범위와 등급 경계는 실제 검토 데이터로 정하고 revision으로 고정한다.
2. **시간·구간 근거:** CTC spike와 실제 조음 구간의 차이를 명시한다. 조음 시점 채점은 검증된 구간 연결이 있을 때만 허용한다. 기존 시간값이 있다는 이유만으로 이를 발음 지속시간 정답으로 쓰지 않는다.
3. **선택 편향 해소:** 현재 선택된 교정 음소 하나만 보는 경로와 문장 평가용 관찰 경로를 분리한다. 기대 문장으로 사전 정의한 평가 구간을 수집하고 유효 구간 비율을 반환한다. 누락 구간을 제외해 좋은 부분만으로 영상 점수가 상승하지 않게 최소 coverage 조건을 적용한다.
4. **집계 계약:** 구간별 실제 프레임 시각, 유효 프레임 수, 최대 frame gap, 시작/중간/끝 형상, 최소/최대/분위수, 변화율 분포, 시간 provenance와 참조 ID를 추가한다. raw landmark 배열·원본 영상은 CLOVA/API에 내보내지 않는다.
5. **품질 gate:** 동일 시도·파일·음소 index, A/V 매핑, 얼굴 수, pose/가림, 표본 수, tracking 품질을 확인한다. 일부 조건은 현재 출력 계약에 없으므로 먼저 계측한다. threshold 수치는 여기서 임의 설정하지 않는다.
6. **승격:** 처음에는 관측값만 기록하는 shadow 상태로 운영 검증하고, 사람이 검토한 정확성·재현성·조건별 편향·교정 적절성 기준을 통과한 항목만 점수화한다. 미통과 항목은 NOT_VALIDATED를 유지한다.

음성의 억양·강세·쉼·속도, VOT, 포먼트, 혀 위치, 성문 상태, 실제 입술 돌출 깊이는 이번 49개에 넣지 않는다. 현재 채점 입력에서 검증된 해당 근거를 확보하지 못했다. 별도 추출기·문맥별 참조 없이 CLOVA에게 추론시켜 채점하지 않는다.

## 7. CLOVA 채점 프롬프트 개편

기준표를 `clova-phone-rubric-v3`처럼 새 revision으로 정의한다. 기존 v2 파일을 덮어쓰지 않는다. 음성 점수는 v2와 수치 호환을 목표로 하고 자식 구조를 추가한다. 영상 기준이 검증되는 시점에는 별도 visual rubric revision을 사용한다.

- 정본 JSON: hierarchy, criterionId/parentId, 기대 표지, 입력 feature, 적용 조건, status 규칙, 계산식, 등급, 근거 한계, 설명 허용 범위를 정의.
- 채점 전용 파일 제안: `prompts/scoring/pronunciation-hierarchy-v3.md`.
- 공통 시스템에는 SCORING 참고 경로와 조건만 유지. `active.md` symlink의 정본을 로더가 채점 시에만 읽어 주입. CLOVA가 파일 시스템을 직접 탐색한다고 가정하지 않음.
- 일반 피드백 호출에 채점표 전체를 중복 주입하지 않음.
- 계산기가 생성한 카탈로그 적용 목록·근거 요약·검산표와 evidence digest를 입력. 음소 원시 데이터·영상·사용자 인증정보는 제외.

채점 전용 지시 초안:

> TASK_MODE=SCORING. RUBRIC_JSON과 EVIDENCE_WORKSHEET에 명시된 항목만 평가한다. 먼저 status와 sampleCount를 확인한다. 평가 대상이 없거나 계측·검증이 불충분한 항목에 점수를 만들지 않는다. 정렬 후보를 인간의 확정 오류로, detector 수치를 정확도 확률로 해석하지 않는다. CALCULATED_RESULT의 수치·상태를 변경하지 않는다. 각 설명은 evidenceRef로 연결되는 현재 시도 근거만 사용한다. 입술 항목은 승인된 visual rubric 및 시간·표본 gate를 통과한 경우에만 적합성을 설명한다. 제한된 측정으로 혀·기류·돌출 깊이를 추측하지 않는다. 허용된 JSON만 반환한다.

**출력 용량 설계도 같이 바꿔야 한다.** 현재 scorer 출력 상한은 512토큰이며 runtime도 512토큰 상한을 검증한다. 49개 항목의 긴 설명을 한 번에 요구하면 잘릴 위험이 있다.

권장안은 전체 수치·카탈로그를 계산기/백엔드가 만들고, CLOVA는 현재 평가 가능한 항목 중 우선순위가 높은 소수의 설명만 제한된 길이로 생성하는 것이다. 모든 항목의 정의·근거 개수는 LLM 없이도 프론트에서 제공한다. 모든 항목에 생성 설명이 반드시 필요하면 별도 배치 설계와 실측 토큰·지연 예산을 마련한다. 상한을 먼저 크게 늘리는 방식은 채택하지 않는다.

현재의 ‘CLOVA가 숫자를 재출력하고 검산하는 계약’을 설명 중심 계약으로 바꾸는 경우 `generator/calculatorRevision/llmModelRevision/feedbackSource`를 분리한다. CLOVA 장애 시 수치 공개 정책은 별도로 결정하고, 기존 점수 fail-closed를 자동으로 완화하지 않는다. 기존 피드백 fallback과 점수 생성 실패를 구분한다.

## 8. 워커·백엔드·프론트 계약 계획

### 워커

`clova_scoring.py`의 evidence 집계를 40개 음소 자식과 9개 음성 부모로 확장한다. 모든 기대 음소가 정확히 한 음소 leaf에 귀속되는지 검사한다. coverage는 의도적인 별도 축이다. 영상은 `score(analysis)`에 임의 dict를 추가하는 대신 동일 시도가 검증된 `ScoringContext(audioEvidence, visualEvidence, quality, referenceVersions)` 같은 내부 계약으로 명시한다.

### 백엔드

`ClovaScoreEvidence.validate()`는 현재 정본의 9항목 개수·순서·해시를 검증한다. 49개를 그대로 전송하면 거절되므로 **프롬프트만 수정해서는 안 된다.** 워커 parser, 양쪽 result JSON Schema, Java 검증기와 저장 JSON을 함께 버전화한다. 파일명이 현재 `runpod_result_v1.schema.json`이라고 실제 허용 schemaVersion까지 v1이라고 가정하지 않는다.

공개 응답은 기존 scoreBreakdown 9항목을 호환 유지하고, `scoreHierarchy`라는 별도 nullable 필드 추가를 제안한다. 각 노드는 id/parentId/label/description/status/sampleCount/level/normalizedScore/children 등을 갖고, 부모만 전체 배점·획득점을 갖는다. 음성 단위수와 영상 measurement는 타입을 분리한다. 이 필드명은 제안이며 현재 API 응답이라고 문서화하지 않는다.

상태는 다음 의미를 고정한다.

| 상태 | 점수 | 뜻 |
| --- | --- | --- |
| SCORED | 0~100 | 평가된 항목. 실제 0점 가능 |
| NOT_APPLICABLE | null | 기대 문장에 해당 표지가 없음 |
| NOT_PROVIDED | null | 예: 영상 입력 없음 |
| UNAVAILABLE | null | 필요한 계측 실패/품질 부족 |
| NOT_VALIDATED | null | 계측은 가능해도 점수 기준 미승격 |
| LEGACY_UNAVAILABLE | null | 기존 저장 결과에는 소분류 근거가 없음 |

기존 DB의 9항목 합계만으로 음소별 점수를 역산하지 않는다. 기존 결과는 기존 형식으로 조회하고 신규 분석부터 hierarchy를 저장한다. 원본 전체 음소 근거가 별도로 남아 있는지 확인 없이 backfill 계획을 만들지 않는다. 캐시 namespace/직렬화 호환성, result canonical digest·중복 ACK, rubric/prompt hash 검증도 갱신한다.

### 프론트

기존 총점 → 대분류 요약 → 펼치면 음소별 소분류 점수·표본 수·근거 상태 → 클릭/hover/focus로 정의·제한 설명의 흐름으로 구성한다. 숨긴 항목을 0점 막대로 채우지 않는다. 카탈로그 전체 49개와 이번 문장의 평가 대상 수를 구분한다. 미검증 영상 항목은 ‘점수 준비 중’이며 사용자의 발음 오류로 표시하지 않는다.

## 9. 구현 순서와 완료 조건

| 단계 | 산출물 | 완료 조건 |
| --- | --- | --- |
| 1. 계약 확정 | 49개 ID·계층·상태·집계·버전·프롬프트 입력 명세 | 음성 표지40개가 중복/누락 없이 매핑되고 미제공과 0점이 구분됨 |
| 2. 음성 41항목 | 워커 집계·검산, BE parser/저장/공개 projection, FE 계층 표시 | 같은 기존 근거로 부모/overallScore가 v2와 일치. 누락/대치/없음/1표본 사례 개발자 검증 |
| 3. CLOVA 상세 설명 | 조건부 프롬프트·근거 참조·출력 길이 제한 | 근거 밖 오류/입술 진단 없이 설명. 토큰 초과·잘림·실패 시 기존 정책 유지 |
| 4. 영상 관찰 확장 | 다구간 계측·시각/표본 provenance·8항목 observation | 단일 선택 음소를 문장 전체로 일반화하지 않음. 영상 없는 요청은 정상 음성 평가 |
| 5. 참조와 품질 검증 | 음소/문맥/촬영조건별 전문가 검토 참조·승격 문서 | 적합성 라벨·재현성·조건별 편향·coverage gate 검토 후 승인된 항목만 활성 |
| 6. 점진 배포 | BE 호환 reader → worker opt-in → FE → 영상 shadow/승격 | 이전 결과 조회·승급 호환, 롤백 시 v2 유지, 새 요청에서만 v3 선택 |

개발자 검증에는 부모·자식 중복 합산 방지, 손상 입력의 분모 제외 금지, 카메라/얼굴/동기화 이상, 영상미제공, NO_ISSUE 음성 결과, legacy 결과, scorer/LLM timeout, callback APPLIED/DUPLICATE, 프론트 표시를 포함한다. 이번 조사에서는 자동 테스트·신규 추론을 실행하지 않았다.

최종 출시 판정은 ‘49개 JSON이 나온다’가 아니라 **근거가 있는 항목만 평가되고, 같은 근거의 계산이 재현되며, 미제공과 실제 0점이 구분되고, CLOVA가 근거 밖 진단을 만들지 않는가**로 한다. 영상 점수는 5단계 이전에 출시 완료로 보고하지 않는다.

## 10. 주요 코드 근거

AI 경로는 검토 SHA `90ffc747539e5f650a62ffaa21d688ddc69cba16` 기준이다.

- `seungun/src/korean_phone_ctc/frozen_detector.py:57` — PhoneEvidence. `:74` — PronunciationAnalysis. `:438` 부근 — 실제 출력 구성.
- `src/voice_coach/backend_analysis/clova_scoring.py:48` — evidence_counts. `:74` — detailed_evidence. `:98` — criterion_level. `:106` — ClovaRubricScorer.
- `src/voice_coach/backend_analysis/service.py:146,150` — 영상 설명 전달과 음성 전용 채점 호출의 분리.
- `src/voice_coach/backend_analysis/adapters/visual.py:240` — analyze_with_grounded_evidence. 선택 음소/COACHING_READY 조건. `:348` — closed-beta projection.
- `src/voice_coach/pronunciation_attempts/adapters/production_visual_sidecar.py:553` — 음성·영상 구간 대응. `:589` 부근 — closed-beta 평균·최대 변화율·calibrated_deviation=None.
- `src/voice_coach/visual_feedback/models/p6_lip_trajectory.py:111` — 2D 형상 정의. `:146` — 프레임 시각과 내부 시계열.
- `src/voice_coach/visual_feedback/services/p6_lip_trajectory.py:31` — trajectory 구성. `:120` — 정규화·형상 계산.
- `src/voice_coach/backend_analysis/grounded_feedback.py:36` — 승인 근거를 전달하는 BackendGroundedFeedback.
- `src/voice_coach/pronunciation_feedback/clova_prompt_routing.py:16` — 채점 프롬프트 조건부 로딩.
- `src/voice_coach/pronunciation_feedback/adapters/hyperclova_transformers_runtime.py:18,212` — 생성 512토큰 상한 및 검증.
- BE `src/main/java/org/example/voice/analysis/domain/model/ClovaScoreEvidence.java:59,103` — 현재 항목 수 검증과 공개 breakdown 생성.
- BE `src/main/java/org/example/voice/title/application/TitleService.java:98` — 저장 점수 기반 승급 채점.

이번 산출물은 이 계획 문서뿐이다. 기존 PR #82/#19, 모델, threshold, 프롬프트, 환경설정, 운영 데이터는 변경하지 않았다.