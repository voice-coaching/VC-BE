# CLOVA / GPT-6 Astra 실제 호출 비교 — 2026-09-18

후속 검증: [실제 입술 관측값을 결합한 GPT 재테스트](gpt6-lip-observation-retest-20260918.md). 영상 전체 표본 측정값을 추가한 별도 실험이며, 아래 음성 근거만 사용한 비교 결과와 구분한다.

기존 `testvideo1.mp4`의 분석 결과와 문장 「쌀과 콩을 깨끗한 그릇에 담아요」를 재사용했다. OpenAI API에 실제 요청했고 채점·피드백 모두 HTTP 200, `finish_reason=stop`을 받았다. 요청 모델과 응답 모델은 `gpt-6-astra`, reasoning effort는 `low`다.

## 결과

각 작업의 모델별 표본은 1개다. CLOVA는 저장된 이전 실측, GPT는 이번 실측이다.

| 비교 구간 | CLOVA | GPT-6 Astra low | 감소 |
| --- | ---: | ---: | ---: |
| v3 채점 호출·검증 | 16.105초¹ | 4.714초 | 약 71% |
| 발음 피드백 호출·검증·렌더링 | 60.280초 | 10.665초 | 약 82% |
| 위 두 시간의 합계² | 76.385초 | 15.379초 | 61.006초, 약 80% |

¹ CLOVA 채점 기록은 HTTP 16.105초이며 바로 뒤 검증이 완료됐다. GPT 채점 HTTP만의 시간은 4.712초, 피드백 HTTP는 10.654초다. 밀리초 수준의 측정 경계 차이가 있다.

² 서로 다른 저장 기록과 독립 호출 시간을 합한 값이다. 새 녹음 업로드부터 프론트 응답까지의 E2E 실측이나 동시 부하 성능, 평균·p95 수치가 아니다. 음성 추론·영상 처리·S3·콜백·프론트 polling 시간은 포함하지 않는다.

| 결과 검증 | CLOVA | GPT-6 Astra low |
| --- | --- | --- |
| overallScore | 62.5 | 62.5 |
| v3 채점 계산·항목 검증 | 통과 | 통과 |
| 채점 attentionCriterionIds | vowels.ae, plain_stops.d, fricatives.ss | vowels.ae, plain_stops.d, aspirated_stops.k |
| 피드백 공유 하네스 | validated_grounded_plan | validated_grounded_plan |
| 피드백 fallback | 없음 | 없음 |

관심 항목 선택은 달랐지만 두 응답 모두 현재 검증기가 허용하는 관측 항목을 선택했다. 점수 62.5는 기존 결정적 채점 계산값과 일치해야 통과하는 구조다. 이 결과를 두 LLM의 독립적인 발음 판정 정확도 일치로 해석하면 안 된다.

## 같은 조건으로 유지한 부분

- 실제 Seungun 분석 결과, 기대 음소, 정렬·검출기 근거, v3 채점 기준과 계산식을 재사용했다. 가중치·threshold·모델 가중치를 바꾸지 않았다.
- 두 호출 모두 CLOVA에 실제 보냈던 **system/user 메시지의 내용과 순서가 동일**하다. GPT용 설명이나 예시를 추가하지 않았다.
- 채점은 배포된 `hierarchical_scoring.score_hierarchy()`를 실행했다. 저장된 CLOVA 요청과 이번 함수가 구성한 메시지의 동등성을 먼저 확인했다.
- 피드백은 저장된 최종 메시지를 그대로 전송했다. 배포된 시스템 프롬프트와 동일한지 확인하고, 저장된 CLOVA 응답도 공유 하네스로 다시 검증·렌더링하여 기존 최종 문구와 일치함을 확인했다.
- 피드백에 적용한 함수는 `generate_grounded_video_feedback()` → `parse_grounded_advice_plan()` → `validate_grounded_advice_plan()` → `render_grounded_advice_message()`다. 오류 시 fallback 규칙도 유지했으며, fallback을 GPT 성공으로 계산하지 않았다.
- 같은 RunPod 호스트에서 호출했다. HTTP timeout 180초, 명시적인 출력 한도 512, 자동 재시도 0회다. 둘 다 프롬프트로 JSON을 요구하며, GPT에만 Structured Outputs를 추가하지 않았다.
- 채점 프롬프트 로더·심볼릭 링크 참조·기준표를 그대로 사용했다. 피드백 입력도 CLOVA의 기존 패킹 결과를 유지해 총 30개 음소 중 상세 24개와 전체 기대/CTC 음소열을 전달했다. GPT의 넓은 컨텍스트를 이용해 입력을 추가하지 않았다.

메시지 SHA-256(내용과 순서 기준):

- 채점: `7b16b5d2e3cc4830a52aa64c94b1fe58e2b0b82e7ddfcbacbc5c37ff716b4989`
- 피드백: `b7000f55add75494dc984da9db3fc859f4f62ef49541aec46e86a165e77e85f5`

## 제공자 차이 및 비교 한계

- CLOVA는 로컬 HyperCLOVAX-SEED-Think-32B, `temperature=0`, `thinking=False`다. GPT는 외부 OpenAI API의 `gpt-6-astra`, `reasoning_effort=low`다. GPT-6에서 지원하지 않는 temperature와 CLOVA 전용 chat_template_kwargs는 보내지 않았다. [공식 모델 사용 안내](https://developers.openai.com/api/docs/guides/latest-model)
- CLOVA의 512는 생성 출력 한도이고 GPT의 `max_completion_tokens=512`는 추론·가시 출력 합산 한도다. 같은 숫자가 완전히 같은 출력 예산은 아니다. 이번 두 GPT 호출은 512에서 정상 종료했으며 더 큰 예산 재호출은 하지 않았다.
- 토크나이저와 내부 chat template은 다르다. 같은 메시지라도 피드백 입력은 CLOVA 3,882토큰, GPT 3,313토큰이었다.
- OpenAI 응답에는 CLOVA의 checkpoint revision 필드가 없으므로 제공자별 모델 식별 검증만 별도로 적용했다. CLOVA revision을 위조하거나 GPT 응답을 CLOVA 결과로 저장하지 않았다.
- 실험은 HTTP 전송 부분만 임시 프로세스 안에서 바꿨다. 운영 scorer가 `generator=hyperclova`를 기록하는 부분은 GPT 운영 전환 시 별도 수정이 필요하다. 이번 반환 evidence를 운영 DB나 callback으로 보내지 않았다.
- 저장된 분석·점수를 고정해 LLM 경계를 비교했다. 새 영상 전송, 음성·입술 추론, 운영 분석 요청, 전체 순차 파이프라인 재실행은 하지 않았다. 특히 영상 보조 근거가 없는 이 샘플로 입술 피드백 품질을 평가할 수 없다.
- 모델별 1회, 시각이 다른 비교다. GPT가 항상 5배 빠르거나 모든 사용자 요청이 15초에 끝난다는 보장은 아니다.

GPT 실제 usage:

| 호출 | 입력 토큰 | 출력 토큰(추론 포함) | 그중 추론 토큰 | cache hit |
| --- | ---: | ---: | ---: | ---: |
| 채점 | 2,036 | 89 | 37 | 0 |
| 피드백 | 3,313 | 415 | 0 | 0 |

## GPT가 실제 생성한 최종 피드백

> 이번에 확인할 부분은 ‘콩을’의 ‘콩’ 받침 ㅇ입니다. 제공된 자동 정렬에서는 이 받침에 대응하는 소리와 시각을 찾지 못했습니다. 다만 이것만으로 실제로 받침을 생략했다고 단정할 수는 없습니다.
>
> 목표 발음에서 ‘콩’의 받침 ㅇ은 소리가 있는 비음 [ŋ]입니다. ‘을’의 첫 글자 ㅇ처럼 자음 소릿값이 없는 경우와 다릅니다. 일반적으로 받침 [ŋ]은 혀 뒤쪽으로 입안 통로를 막고 공기를 코로 내보내며 냅니다. 이는 목표 발음 방법이며, 현재 혀 위치나 기류가 잘못되었다는 진단은 아닙니다.
>
> ‘코–콩’을 천천히 번갈아 말해 보세요. ‘콩’에서는 입술을 다물어 ‘ㅁ’을 만들지 말고, 혀 뒤쪽에서 막힘을 만들며 끝의 코 울림을 잠깐 유지해 보세요. 이어서 ‘콩 → 콩을 → 콩을 깨끗한 그릇에 담아요’로 연결합니다. 연습할 때만 받침을 조금 늘리고, 문장에서는 자연스럽게 이어 주세요.
>
> 스스로 확인할 기준은 ‘코’와 ‘콩’의 끝소리가 구별되는지, ‘콩을’에서도 ‘ㅗ’와 ‘ㅡ’ 사이에 받침의 코 울림이 남는지입니다. 받침 뒤에 별도의 ‘그’나 ‘으’를 덧붙이지 않는 것도 함께 확인해 보세요.

이번 샘플에서 GPT는 정렬 실패와 실제 생략을 구분하고, 초성 ㅇ/받침 ㅇ 차이, 조음 방법, ‘코–콩’ 대조 연습, 자가 확인 기준을 제공했다. CLOVA도 받침 [ŋ] 조음 방법을 설명했지만 불확실성 구분과 단계별 연습은 덜 구체적이었다. 이는 해당 문구에 대한 정성 검토이며 발음 전문가의 독립 평가나 실제 학습 효과 검증은 아니다. JSON 검증 통과만으로 모든 발음 설명의 사실 정확성을 보증하지 않는다.

## 변경 범위와 실행 자료

- 사용자 요청대로 VC-BE `.env`에 `OPENAI_API_KEY`를 저장했다. `.env`는 Git 무시 대상이며 추적되지 않음을 확인했다. 키 값은 문서·결과·스크립트에 기록하지 않았다.
- 키는 로컬 환경파일에서 읽어 SSH 표준입력으로 해당 비교 프로세스에만 전달했다. RunPod 운영 환경파일에는 추가하지 않았다.
- 운영 서비스·라우팅·DB·모델·프론트는 변경하지 않았다. commit/push/PR/배포는 하지 않았다.
- 키 없는 재현 스크립트: `C:\Users\Public\Documents\ESTsoft\CreatorTemp\clova-gpt6-comparison-20260918`의 `run_comparison.py`, `compare_remote.py`, `compare_scoring_remote.py`.
- 로컬 피드백 결과: 같은 폴더의 `comparison-output.jsonl`. 재실행 시에는 `feedback-comparison-output.jsonl`에 저장한다.
- 로컬 채점 결과: 같은 폴더의 `scoring-comparison-output.jsonl`.
- 피드백 RunPod 관측: 2026-09-18 20:19 KST. 원격 보고서: `/tmp/feedback-transfer-stage-20260918/gpt6-comparison-20260918T111959Z/report.json`.
- 채점 RunPod 관측: 2026-09-18 20:23 KST. 원격 보고서: `/tmp/feedback-transfer-stage-20260918/gpt6-scoring-20260918T112333Z/report.json`.

실제 모델 호출은 성공했지만 이는 운영 제공자 전환 완료가 아니다. 운영 전환 시에는 모델 provenance, 설정 로더, 서비스 준비 상태, 오류·요금 한도 처리와 실제 callback을 포함한 별도 통합 검증이 필요하다.
