# 아나운서 피드백 프롬프트 복구 계약

AI의 `canonical-dlpc-gop-announcer-20261007-v1`은 9/29 Git 원문에 있던 아나운서 피드백과 지도 방식을 복구한 prompt-only derivative다. GOP core, rubric과 출력 형태는 유지한다.

SemanticVerifier 및 result v4/v5 association/sourceIdentity에 다음 추가 tuple을 등록한다. 기존 배포 tuple도 계속 허용한다.

- Core: `f6d56e1decc160f0ca38cc8d52303f3de20945f872d714bf6a2e743ed94c0391`
- LLM: `def65f68a849a2452b0e4dde600f0ef85d99beaa97123d30d97262653ebf8e77`
- Lock: `b605c3b9ba06bbf18fc126770b18de3d2da0d3f70c08e04873f17bc735b948bc`
- Prompt: `977c49744e7af9fc60fdc2eb13c23985692cb00dd71789d8b3c43c3813e3f1b5`

`bootJar --offline --no-daemon -x test -x check` 성공. AI와 두 schema 파일의 바이트 일치를 확인했다. 자동 테스트·실제 생성 QA는 미수행이다.

2026-10-07 11:11 KST 기존 배포 도구로 drain을 확인한 뒤 Backend `84260c05f2dde80ebec3f0d522726cca1ca8ff63` 및 새 verifier/profile을 활성화했다. handoff의 새 9개 schema와 HTTP 200을 확인했다. DLPC 설치기가 hold 중에도 `admissionEnabled=true`를 기다리는 결함을 수정한 뒤 사용자가 재개 명령을 실행했다.

11:30 KST DLPC source `df90eb10…`, bundle `b7170dda…`, LLM `def65f68…` 및 readiness를 확인하고 기존 배포 도구의 `resume`을 완료했다. Backend release는 `COMPLETE`, maintenance hold는 제거됐다. 공개 분석/direct 및 독립 TTS readiness HTTP 200, Backend handoff supported/admission true, 활성 profile과 9개 schema 일치를 확인했다. AWS 비밀 복구본의 source/LLM/pipeline identity도 갱신했다. 이번 프롬프트 복구에서 실제 GPT 생성·음성 품질 QA는 실행하지 않았다. 후속 문서 commit과 활성 JAR source `84260c05…`는 구분한다.

원문 커밋과 세부 범위는 intelligentAI `docs/canonical-integration/announcer-prompt-restoration-20261007.md`에 기록했다. 과거 전체 system의 `items` 계약을 현재 `scoring/feedback` 계약에 그대로 덮어쓰는 롤백은 아니다.
