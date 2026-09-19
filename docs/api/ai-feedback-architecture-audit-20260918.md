# 두 영상의 유사 피드백 원인 및 AI 아키텍처 점검

점검일: 2026-09-18 KST. 대상: 현재 RunPod의 코드·프로세스·영상 릴리스와 testvideo1/testvideo2의 보존된 분석 결과, 실제 GPT 요청·응답, 테스트 스크립트.

## 결론

**두 입력의 차이를 검출한 정보는 존재하지만, 최종 피드백을 같은 음소 하나에 집중시키는 구조가 차이를 충분히 드러내지 못한다.** 두 영상의 CTC 결과와 점수는 다르다. 그러나 두 영상 모두 같은 index 7의 `ㅇ`이 정렬상 `deletion`으로 처리되고, 같은 검출 점수로 최우선 교정 대상이 된다. LLM에는 이 음소를 우선하라는 지시가 전달된다.

**입술 결합 테스트도 전체 운영 영상 분석을 검증한 실험은 아니었다.** 실제 8개 프레임에서 입술 관측값을 추출해 GPT에 전달했지만, 음소별 구간·동기화·40점 궤적·교정 주장까지 연결한 운영 sidecar는 실행하지 않았다. 따라서 이 결과를 음성·영상 기반 종합 발음 평가가 검증된 것으로 받아들이면 안 된다.

운영 영상 릴리스에는 별도의 검증 취약점도 있다. 모든 40개 음소에 공통 입 벌림 기준을 적용하며, 보정·평가 기록은 각각 2건이다. 해당 릴리스 생성 스크립트는 평가 실행 없이 `violation_count=0`을 지정한다. 이 문제는 이번 GPT 테스트의 직접 원인과 구분해야 하지만, 운영 교정 정확도 관점에서 우선 확인해야 한다.

사용자가 설명한 ‘영상1은 좋은 발음, 영상2는 의도적으로 나쁜 발음’은 중요한 검증 기준이다. 다만 이번 조사에서는 전문가의 음소별 청취 정답을 새로 만들지 않았다. 아래의 CTC `correct`, `substitution`, `deletion`은 **모델 후보열과 기대열의 편집 정렬 결과**이지 사람 발음의 확정 판정이 아니다.

## 점검 범위와 변경

- RunPod API, CLOVA, Seungun 프로세스와 listening 포트를 읽기 전용으로 확인했다.
- 배포 코드와 로컬 AI 체크아웃의 주요 16개 파일을 SHA-256으로 대조했다. Windows CRLF를 LF로 정규화하면 일치했다.
- 로컬 AI 체크아웃 HEAD: `0648007cf689dfd16866f46cf636d3ecd6adeae2`.
- 원격 배포 디렉터리에는 Git 메타데이터가 없어 전체 트리의 커밋 일치를 단정하지 않았다. 검토한 파일 단위 일치가 근거다.
- 기존 두 영상의 분석 JSON, 채점 근거, GPT 메시지, 실제 응답을 대조했다.
- 모델 추론 없이 기존 `align_phones()`로 반복 음소의 동점 처리를 재현했다.
- 새 음성·영상 추론, LLM 호출, 운영 API 분석 접수, 배포·재시작·설정·모델·threshold 변경은 하지 않았다. 이 문서만 추가했다.
- 프로세스 환경 `/proc/.../environ` 직접 읽기는 OS 권한으로 거절됐다. 환경파일의 관련 항목과 코드·프로세스 시작 시각을 확인했으며, 이를 프로세스 메모리 전체 검증으로 표현하지 않았다.

## 1. 확인한 실행 구조

확인 시 API PID 41664는 `serve_backend_analysis_http.py`, Seungun 자식 PID 41669는 `seungun_production_runtime_server`, CLOVA PID 20312는 `serve_hyperclovax_grounded_advice.py`였다. API는 `0.0.0.0:8080`, CLOVA는 `127.0.0.1:8000`에서 listening 중이다. PID는 관측 시점의 값이다.

현재 공통 분석 코어의 호출 순서는 다음과 같다.

```text
AWS → POST /v1/analysis-jobs → HTTP 실행 워커
  → BackendAnalysisWorker.process_admitted()
  → S3 오디오 materialize / 무결성 확인
  → SeungunBackendAnalysisAdapter.analyze()
      대본 → g2pK 기대 표지열
      오디오 → ffmpeg 16kHz mono WAV
      XLS-R CTC logits / layer-18 hidden state
      greedy CTC 후보열 → Levenshtein 편집 정렬
      frozen MLP 검출 점수 → threshold 통과 후보 중 1개 선택
  → 선택적 운영 영상 sidecar
      영상·PCM 동일성 확인 / 기존 Seungun 결과 재사용
      전체 8프레임 MediaPipe + 영상 품질·A/V sync 판정
      선택 음소의 시간 구간으로 dense window 디코딩
      선택 구간 입술 형상·궤적 / 보정 기준 / 승인 주장
  → v3 결정적 채점 → CLOVA의 점수 확인·관심 항목 선택
  → 전체 근거 구성 → 토큰 예산에 맞춘 축약
  → CLOVA 피드백 생성 → JSON·ID·개인정보 등 검증 / fallback
  → summaryFeedback·점수·선택 음소 근거·선택적 영상 근거
  → AWS result callback → 프론트 polling 후 상세 조회
```

코드 근거는 AI 저장소 기준이다.

| 경계 | 처리 코드 |
| --- | --- |
| RunPod 접수 | `src/voice_coach/backend_analysis/http_api.py:81` |
| HTTP 실행 → 공통 코어 | `backend_analysis/http_runtime.py:37` |
| 음성 → 영상 → 점수 → 피드백 → 결과 | `backend_analysis/service.py:106` |
| 실제 음성 분석 adapter | `backend_analysis/adapters/seungun.py:204` |
| 선택적 영상 실행 | `backend_analysis/adapters/visual.py:240` |
| 채점 | `backend_analysis/hierarchical_scoring.py:72` |
| 피드백 입력 구성 | `backend_analysis/grounded_feedback.py:48` |
| 피드백 LLM HTTP 호출 | `pronunciation_feedback/adapters/advice_generator_hyperclova.py:194` |
| 결과 검증·문구 반환 | `pronunciation_feedback/grounded_advice.py:643`, `:683` |

`src/voice_coach/`를 생략한 경로는 같은 디렉터리 아래를 의미한다. 운영 설정은 CLOVA와 v3 채점을 사용한다. GPT-6 테스트는 별도 임시 프로세스에서 HTTP 호출·모델 식별 부분을 OpenAI로 교체했다. GPT가 운영 제공자로 전환됐다는 의미는 아니다.

## 2. 두 영상의 실제 차이

대본은 둘 다 「쌀과 콩을 깨끗한 그릇에 담아요」이며 기대 표지열은 30개다. 기존 분석의 backbone digest, detector config digest, detector version은 일치했다.

| 항목 | testvideo1 | testvideo2 |
| --- | ---: | ---: |
| 디코딩된 오디오 길이 | 4,843ms | 2,709ms |
| CTC 편집 정렬 `correct` | 22 | 11 |
| `substitution` | 6 | 16 |
| `deletion` | 2 | 3 |
| 검출기 threshold 통과 행 | 7 | 10 |
| v3 총점 | 62.5 | 45.8 |
| 공식 선택 index / 표지 | 7 / ㅇ | 7 / ㅇ |
| 선택 행 operation | deletion | deletion |
| 선택 행 후보 / 시작·끝 시각 | 모두 null | 모두 null |
| 선택 행 검출 점수 | 0.9472929835319519 | 0.9472929835319519 |
| 공통 operating threshold | 0.8134703636169434 | 0.8134703636169434 |
| 실제 입술 표본 수 | 8 | 8 |
| 입술 벌림 이미지 좌표 비율 평균 | 0.04071223 | 0.02429595 |
| 같은 비율 최대값 | 0.12107467 | 0.07538769 |
| GPT에 포함된 상세 음소 행 | 24/30 | 24/30 |
| GPT에 포함된 soft feature 행 | 1/30, index 7 | 1/30, index 7 |

영상2는 전체 길이가 영상1의 약 56%다. 입술 표본 평균 비율은 약 40% 낮다. 이는 실제 관측 차이지만, 발화 구간을 분리한 말속도나 자세·종횡비가 보정된 물리적 입 벌림 측정은 아니다. 이 숫자만으로 정확한 발음 점수를 산출할 수는 없다.

실제 입력에 포함된 관심 항목도 다르다.

- 영상1: `vowels.ae`, `plain_stops.d`, `fricatives.ss`
- 영상2: `vowels.e`, `aspirated_stops.k`, `aspirated_stops.t`

두 GPT 피드백 응답 모두 `gpt-6-astra`, `finish_reason=stop`, 입력 cache hit 0, fallback 없음이었다. system prompt는 동일하지만 user JSON은 다르다. 동일 결과 파일의 재사용이나 출력 잘림으로 설명할 근거는 없다. 실제 GPT 원문부터 ‘콩’의 받침 ㅇ, ‘코–콩’ 연습으로 내용이 집중되므로 AWS·프론트에서 문구가 바뀐 현상도 아니다. 이번 테스트는 AWS callback을 보내지 않았다.

## 3. 직접 원인: 같은 삭제 후보에 수렴

### 3.1 삭제 행의 고정 특징값

`seungun/src/korean_phone_ctc/frozen_detector.py:177`은 deletion 행에 다음 값을 넣는다.

```text
soft = [1, 0, 0, 1, -1, 0, 0, 1, 0]
hidden = zeros(1024)
observed_candidate = null
start_ms = end_ms = null
```

`_score_rows():334`는 이 soft feature, expected phone embedding, hidden feature로 MLP 점수를 계산한다. **동일 모델에서 동일 기대 음소가 deletion으로 분류되면 동일 특징 입력이 된다.** 해당 음소 구간에서 직접 얻은 hidden evidence는 없다.

따라서 두 영상에서 ‘ㅇ 삭제’의 점수가 소수점까지 같은 것은 실제 발음 상태가 같다는 증거가 아니다. 오디오가 deletion 여부를 만드는 과정에는 관여하지만, deletion 이후 점수는 위 입력으로 수렴한다.

### 3.2 단일 최고 점수 선택

`production_pipeline.py:_decision():182`는 threshold 통과 후보 중 다음 기준으로 하나를 고른다.

```python
_, selected = min(candidates, key=lambda item: (-item[0], item[1]))
state = "coach_one"
```

영상1의 다음 후보는 index 5 `ㅋ→ㄱ`, 점수 0.946438이었다. 영상2에는 index 1 `ㅏ→ㅅ`, index 0 `ㅆ deletion`, index 15 `ㅌ→ㄸ` 등 다른 후보가 있지만, 두 영상 모두 index 7의 0.947293이 가장 높다.

`seungun_bridge.py:_selected_phone():179`도 같은 방식으로 선택한다. `grounded_feedback.py:70`은 이 선택이 공식 pronunciation evidence와 일치하는지 검사한다. 이중 선택이 다른 대상으로 갈라진 흔적은 없고, 같은 선택을 유지한다.

이 선택 정책에는 정렬 모호성, 국소 음향 근거 유무, 문장 전체의 오류 분포, 설명할 가치, 영상에 연결 가능한 구간을 종합한 우선순위가 없다. 검출기 순위 점수 하나가 최종 조언의 중심을 결정한다.

### 3.3 초성·종성 ㅇ 표지와 정렬 모호성

실제 기대열의 해당 부분은 다음과 같다.

```text
... ㅋ ㅗ ㅇ ㅇ ㅡ ㄹ ...
          7  8
```

원문 정렬상 7은 ‘콩’ 받침, 8은 ‘을’ 초성이다. `phonology/g2pk_expected.py:85`와 `:99`는 초성 ᄋ과 종성 ᆼ을 모두 같은 호환 자모 `ㅇ`으로 변환한다. 모델 입력 표지에 초성·종성 구분이 사라진다. 피드백 system prompt는 한편으로 두 역할을 다르게 설명하라고 지시한다.

두 CTC 후보열에는 이 자리에 ㅇ 하나가 남는다. 실제 배포 `align_phones()`를 추론 없이 호출한 결과:

```text
reference = [ㅇ, ㅇ], hypothesis = [ㅇ]
→ 첫 ㅇ deletion, 둘째 ㅇ correct
```

역할이 다른 두 표지가 합쳐진 상태에서 Levenshtein 동점 처리로 첫 표지를 삭제했다고 배정한다. **이것만으로 ‘콩’의 받침을 실제 생략했다고 국소화할 수 없다.** 영상1의 잘못된 교정 후보를 설명할 유력한 원인이다.

다만 학습 TextGrid에서 초성 ㅇ을 어떤 규약으로 라벨링했는지까지 원본 데이터와 대조하지 않았으므로, 당장 초성 ㅇ을 모두 삭제하면 해결된다고 단정하지 않는다. 학습 target 생성은 `seungun/.../data/ctc_manifest.py:29`의 관측 TextGrid 표지 정규화를 사용한다. 기대열과 학습 표지 규약의 일치 여부를 먼저 검증해야 한다.

### 3.4 시간 정렬의 실제 의미

현재 경로는 greedy CTC nonblank span을 후보열로 만든 뒤 편집 정렬한다. 별도의 음향 forced alignment로 기대 음소마다 구간을 찾아준 결과가 아니다. 두 영상의 많은 span은 약 20ms CTC 출력 구간이다.

이 span을 실제 음소 지속시간으로 사용하면 빠르기·늘임·쉼에 잘못된 의미를 붙일 수 있다. 실제 입력에서도 duration, VAD 기반 발화 시간, 말속도, F0·강세·쉼 측정값은 피드백 JSON에 없다. 현재 활성 코어에 prosody 평가가 연결된 근거도 없다. 따라서 ‘영상2가 지나치게 빠르다’를 근거 있는 별도 평가 항목으로 전달하지 못한다.

## 4. 영상 파이프라인과 테스트 범위

### 4.1 지난 두 GPT 테스트에서 실행한 경로

`visual_remote.py`, `visual_feedback_remote.py`, `testvideo2_remote.py`를 확인했다.

- 컨테이너 probe와 RGB 디코딩은 실제 수행했다.
- 기본 `MediaPipeLipLandmarkAdapter`로 전체 영상에서 균등하게 8개 프레임을 측정했다.
- 기본 입술 4점에서 이미지 좌표의 세로/가로 비율을 얻었다.
- `feedbackEvidence.unboundClipLevelLipObservation`에 이를 추가했다.
- 선택 음소와 연결되지 않았으며 A/V sync도 검증하지 않았다.
- 운영 `visual_supplement`는 null이었다. 입력의 `NO_PUBLISHABLE_EVIDENCE`는 운영 sidecar 실행 실패를 관측한 상태가 아니라 실험에서 구성한 값이다.
- 운영 40점 궤적·dense phone window·승인 교정 주장 경로는 이 테스트에서 실행하지 않았다.

두 GPT 입력에는 ‘정상 참조값 없음’, ‘너무 작거나 크다고 판정하거나 채점하지 말 것’, ‘선택 음소와 대응 없음’이 명시되어 있다. 이 제약은 실제 실험 근거 수준에 맞는다. 그 결과 GPT가 단순 비율을 발음 오류로 바꾸지 않은 것은 예상되는 동작이다.

### 4.2 운영에는 더 풍부한 경로가 있지만 선택 음소 하나에 종속

`pronunciation_attempts/composition.py:269`는 geometry artifact가 있을 때 `ClosedBetaMediaPipeLipLandmarkAdapter`를 선택한다. 운영 환경파일에는 geometry artifact와 enabled visual release가 설정되어 있다.

영상 품질·동기화 판정 후 `runtime.py:1295`의 `plan_phone_window()`가 선택 음소의 시간 구간을 요구한다. 시각이 null이면 `:1333`에서 dense window를 만들지 않는다. 이어지는 production sidecar는 `DensePhoneWindowEvidence`를 요구한다.

`FrozenSeungunRuntimeAdapter`에는 deletion 시간 구간을 주변 span으로 추정하는 별도 코드가 있지만, AWS HTTP 경로의 영상 adapter는 `_PrecomputedSeungun`을 사용하며 보존된 `analysis_evidence`를 그대로 넘긴다. 두 경로를 혼동해서 ‘운영에서 삭제 음소의 시간이 자동 복구된다’고 말하면 안 된다. 또한 추정 시각은 실제 관측 시각이 아니다.

운영 dense 경로가 성립하면 `production_visual_sidecar.py:590`에서 선택 구간의 형상 평균과 최대 변화율을 생성한다. 하지만 문장 전체의 모든 음소를 순회하며 입술 움직임을 종합 평가하는 구조는 아니다. 음성 선택 결과가 불안정하면 영상의 분석 대상도 그 지점에 묶인다.

### 4.3 관측값과 교정 가능한 주장을 함께 차단하는 경계

- `backend_analysis/adapters/visual.py:_public_projection():319`는 `visual_feedback_ready`, `supports_upstream`, `supplemental_action_candidate`를 요구한다.
- `grounded_feedback.py:_bound_visual_evidence():120`은 공개 supplement와 일치하는 action candidate가 없으면 영상 근거를 받아들이지 않는다.
- 따라서 ‘입술 관측은 유효하지만 교정 주장은 아직 승인되지 않음’을 독립적인 관측 채널로 전달하기 어렵다.
- `VideoAttemptFacade.run_http():599`는 내부의 상세 `reason_codes`와 stage를 반환하지 않고 `draft.visual_supplement`만 반환한다.
- `service.py:241`은 예외를 `EXTRACTION_FAILED`로 축약하고 상세 예외 사유를 남기지 않는다. 정상적인 audio-only 경로는 `NO_PUBLISHABLE_EVIDENCE`로 합쳐질 수 있다.

영상 근거가 null일 때 미제공·구간 없음·동기화 실패·품질 실패·action 미승인을 구별하기 어려운 구조다. 이번 테스트에서 이 운영 경로의 실제 실패 stage를 관측한 것은 아니므로 특정 stage가 실행 중 실패했다고 주장하지 않는다.

## 5. 운영 영상 릴리스의 검증 문제

현재 `artifacts/visual_feedback/releases/production_visual/release.json`은 `enabled`다. `runtime_bundle.json`을 직접 확인한 결과:

| 항목 | 현재 릴리스 값 |
| --- | --- |
| calibration rule 수 | 1 |
| 적용 대상 | 전체 40개 음소 |
| cue | `lip_aperture_ratio`, `phone_center` |
| reference center | 0.040438737635200096 |
| reference scale | 0.03241351358994795 |
| 승인 행동 | 입 벌림 늘리기 / 줄이기 2종 |
| 보정 사례 수 | 2 |
| 평가 사례 수 | 2 |
| 기록된 violation count | 0 |

음소 종류·음절 위치·주변 모음·화자별 참조가 분리되어 있지 않다. ‘공통 기준보다 작음’에서 곧바로 해당 음소의 좋은 발음 방법을 도출할 수 있는 근거는 확인되지 않았다.

배포본과 해시가 일치한 `scripts/create_universal_lip_production_release.py`에서:

- `:140`: 전달받은 calibration 값의 평균으로 공통 reference center를 만든다.
- `:215`: 전체 음소에 이 한 규칙을 적용한다.
- `:294`: 실제 평가를 실행하지 않고 evaluation 파일 수와 `violation_count=0`으로 receipt를 만든다.
- 스크립트 설명은 calibration과 evaluation에 같은 영상을 사용할 수 있다고 명시한다. 이번 배포에서 실제 동일 파일이었는지는 독립 manifest 없이 확정하지 않았다.
- 운영 A/V sync policy의 `minimum_correlation=-1.0`, `minimum_anchor_strength=0.0`, `minimum_anchor_count=1`을 확인했다. 값이 존재하는 정상 범위의 상관계수는 이 문턱으로 거의 걸러지지 않는다. 다른 무결성·구간·입력 검사는 남아 있으므로 무조건 통과하는 경로라고 표현하지 않는다.

**이 receipt는 파일 구성·계약 통과의 증거이며, 발음 교정 품질에 대한 독립 평가 결과로 취급할 수 없다.** 별도의 평가 자료가 있는지는 추가 확인 대상이다. 이 기준을 바꾸거나 릴리스를 중지하지는 않았다.

## 6. 채점·입력 축약·프롬프트·검증

### 6.1 점수는 LLM의 독립 청취 평가가 아님

`hierarchical_scoring.py:76`에서 총점을 먼저 계산하고 `RUBRIC_RESULT`로 LLM에 제공한다. LLM의 반환 총점이 다르면 거절한다. LLM이 선택하는 부분은 허용된 낮은 등급의 관심 항목 최대 3개다.

채점의 입력은 정렬 operation과 `is_error`를 조합한 6가지 상태다. 현재 unit은 다음과 같다.

```text
matchedClear=4, matchedFlagged=2,
substitutedClear=3, substitutedFlagged=1,
deletedClear=0, deletedFlagged=0
```

이를 95/80/60/40/0% 경계로 4/3/2/1/0 등급으로 나누고 대분류 배점으로 합산한다. 문장에 없는 항목은 `sampleCount=0, level=null`이며 분모에서 제외한다. **없는 음소를 0점으로 처리하는 문제는 이번 원인이 아니다.**

이 방식은 정렬·검출기의 오류를 그대로 사용자 점수에 반영한다. 또 40개 음소별 항목이 있어도 각각 서로 다른 조음 측정기가 추가된 것은 아니다. 입술 항목 8개 중 실제 채점 입력에서 읽는 수치는 형상 비율 4개이며, 동작 항목은 미구현/미검증 상태이고 총점에 들어가지 않는다.

영상2가 전반적으로 나쁘더라도 대분류 일부는 더 높을 수 있다. 실제로 평음 파열음은 영상1 level 1 → 영상2 level 3, 비음은 1 → 2였고, coverage는 둘 다 3이다. 이는 현재 관측 상태 조합과 구간화된 계산식의 결과다. 인간 평가와 일치하는지는 별도 검증이 필요하다.

### 6.2 정보는 추가되었지만 LLM 입력에서 다시 축약됨

`feedback_evidence.py:184`의 토큰 예산은 기본 3,900이다. 실제 모델 토크나이저로 계산하면서 세부 점수표, 음소 행, 후보열 등의 일부를 줄인다.

두 실제 요청에서:

- 전체 기대열·CTC 후보열은 포함됐다.
- 검출기가 flagged한 행은 모두 포함됐다.
- 상세 음소는 24/30개였다. 영상1 제외 index는 22–23, 25, 27–29, 영상2는 22, 24, 26–29다.
- soft feature는 두 영상 모두 index 7 하나였다. 나머지 29개의 신뢰·경쟁 후보·국소 특징은 들어가지 않았다.
- 40개 음소별 점수 행은 모두 빠지고 `phoneCriteria=[]`, `phoneCriteriaOmitted=true`가 됐다. 총점·대분류 등급·관심 항목은 남았다.
- 실험용 입술 자료는 기존 패킹 **후** 추가했다. 따라서 운영의 동일 입력 예산 경로를 끝까지 재현한 실험도 아니다.

입력에는 차이를 설명할 정보가 일부 남아 있다. 그러나 그 정보를 반드시 사용하도록 요구하거나 결과에서 대응 관계를 검사하는 구조가 없다.

### 6.3 단일 issue와 프롬프트의 우선순위

두 실제 요청의 `issue`는 동일한 `phone-7`, `targetUnit=ㅇ`, 같은 위치·검출 점수였다. system prompt에도 다음 지시가 있다.

> 공식 선택 지점을 우선하고, 다른 근거가 도움이 되면 후보임을 밝히며 설명한다.

고정된 문장 수나 재녹음 안내를 의무화하는 예전 제한은 현재 제거되어 있다. 하지만 결과 계약의 단일 `issueId/adviceId`, 선택된 음소의 일반 조음 설명, ‘공식 선택 우선’은 유지된다. 문장 전체 진단이나 서로 다른 주요 오류 2~3개를 다루는 요구는 없다.

이 때문에 입력된 다른 관심 항목과 총점 차이가 최종 글에 없어도 정상 출력으로 인정된다. GPT로 모델만 바꿔도 같은 문제 중심으로 설명이 모일 수 있는 구조다.

### 6.4 검증 통과와 피드백 품질은 별개

`validate_grounded_advice_plan():643`은 schema, issue/advice ID, 허용 claim, 후보 존재, 길이, 개인정보 등을 확인한다. 모든 중요한 음소 근거가 반영됐는지, 낮은 점수와 조언이 연결되는지, 다른 두 입력에 실질적으로 다른 설명이 나오는지는 검사하지 않는다.

현재 문구는 `render_grounded_advice_message()`에서 그대로 반환된다. 이번 두 GPT 결과는 fallback이 아니므로, 같은 catalog 문구로 교체되어 비슷해진 것도 아니다.

운영에서는 LLM 예외 시 catalog fallback이 가능하고, 그 generator status는 일반 사용자 결과의 독립 필드로 전달되지 않는다. 이후 운영 품질 진단에서는 생성 경로와 fallback 여부를 따로 추적해야 한다.

## 7. 원인의 우선순위와 수정 방향

아래는 조사 결과에 따른 제안이며 이번에 구현·배포하지 않았다.

| 우선순위 | 조치 | 완료 판단 기준 |
| --- | --- | --- |
| 1 | 기대 표지와 학습 target의 초성/종성 ㅇ 규약 및 반복 표지 정렬 점검 | 전문가가 표시한 구간과 비교해 ‘콩 받침 삭제’의 타당성을 확인. 임의로 기대열을 바꾸어 점수만 높이지 않음 |
| 1 | deletion의 고정 MLP 입력과 단일 최고점 선택 재설계 | 국소 근거가 없는 삭제 후보와 신뢰할 수 있는 관측 오류를 구분. 한 모호한 후보가 전체 진단을 독점하지 않음 |
| 1 | 운영 visual release의 평가 증거 재검토 | 별도 평가 결과·입력 provenance가 있는 경우에만 평가 완료로 표시. 음소 공통 기준과 광범위한 sync 통과 문턱의 정당성 입증 |
| 2 | 관측·추정·교정 주장을 분리한 영상 전달 계약 | QC·sync·타이밍·표본 수·불확실성·차단 사유를 보존. action 미승인이라는 이유로 유효한 중립 관측까지 제거하지 않음 |
| 2 | 음소/단어 시간 정렬과 문장 수준 음성 지표 보강 | CTC spike를 실제 지속시간으로 오인하지 않음. VAD 기반 속도·쉼 등의 측정 정의와 검증 확보 |
| 2 | 주요 문제 묶음과 전체 요약을 지원하는 피드백 입력·출력 | 선택된 한 음소뿐 아니라 서로 다른 주요 오류와 근거 위치, 불확실성, 연습법을 추적 가능하게 전달 |
| 2 | 입력 패킹 개선 | 핵심 오류별 필요한 음향 특징·관심 항목의 근거를 우선 보존하고 실제 최종 메시지 전체를 계수 |
| 3 | LLM 품질 평가 추가 | 정상 발음에 대한 불필요한 교정, 의도적 오류 누락, 입력 근거와 조언의 대응, 서로 다른 녹음의 구분 여부를 전문가 기준으로 평가 |
| 3 | 채점 검증 및 단계 관측 가능성 | 기계 근거 점수와 인간 발음 품질의 일치 확인. request 단위로 영상 차단 사유·LLM 사용 근거·fallback·단계 시간을 연결 |

후속 검증은 두 영상을 ‘정답/오류’ 단일 라벨로만 나누는 데서 끝내면 부족하다. 동일 문장의 원본에 대해 단어·음소별 실제 발화와 오류 위치, 영상에서 비교 가능한 구간을 표시하고, 운영 경로 그대로의 음성·영상 분석 결과와 대조해야 한다. 영상1을 무조건 100점으로 만드는 튜닝이나 두 샘플만으로 threshold를 맞추는 방식은 해결 기준이 될 수 없다.

## 8. 재현 근거와 남은 확인 범위

AI 소스 기준 디렉터리:

`C:\Users\Public\Documents\ESTsoft\CreatorTemp\clova-feedback-policy-20260918`

실험 스크립트 디렉터리:

`C:\Users\Public\Documents\ESTsoft\CreatorTemp\clova-gpt6-comparison-20260918`

기존 RunPod 증거:

- `/tmp/clova-live-v3-20260918-80235e9/analysis-evidence.json`, `scoring-evidence.json`, `report.json`
- `/tmp/feedback-transfer-stage-20260918/gpt6-visual-20260918T115231Z/gpt-messages.json`, `gpt-response-512.json`, `lip-observation.json`
- `/tmp/gpt6-testvideo2-20260918/analysis-evidence.json`, `scoring-evidence.json`, `feedback-messages.json`, `feedback-response-512.json`, `report.json`
- `/tmp/gpt6-testvideo2-20260918/gpt6-visual-20260918T120753Z/lip-observation.json`

동일 system prompt SHA-256: `91d6abd6b9e0d1c3f4d3a48f13d1a28ce3765865277c3f6411704bdff6331226`.

실제 메시지 배열을 `ensure_ascii=false, sort_keys=true, separators=(',', ':')`로 직렬화한 SHA-256:

- 영상1: `ccaf351138f492de4bffd163d92280f3748c836aa6e4b863eff2406c27fa5d18`
- 영상2: `5073666519b0d101704032cb0451043a8203cccd5337ad8fb48b464a7daed34a`

이 값은 이전 보고서의 파일 원문/다른 직렬화 방식 hash와 비교하는 용도가 아니다. 이번 두 입력이 서로 달랐음을 같은 방식으로 대조한 값이다.

이번에 확인하지 않은 항목: 전문가 청취 정답, 원본 학습 TextGrid의 초성 ㅇ 라벨 실태, 전체 학습 데이터와 calibration/evaluation 원본의 중복 여부, 새 운영 영상 sidecar 실행 결과, 모든 요청의 프론트 E2E, 모델 정확도에 대한 통계적 성능. 이를 확인하지 않고 모델의 구별 능력이 전혀 없거나 모든 학습이 잘못됐다고 결론 내리지 않았다.

관련 기존 실행 기록: [testvideo1 입술 결합 실험](gpt6-lip-observation-retest-20260918.md), [testvideo2 실험](gpt6-testvideo2-verification-20260918.md).
