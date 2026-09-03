# CI/CD Failure Report - test-reports-33731316528

## Summary

- Report path: `C:\Users\mmkan\Downloads\test-reports-33731316528`
- Report generated at: 2026-09-03 08:05:28 UTC
- Test task: Gradle `:test`
- Total tests: 129
- Failed tests: 1
- Skipped tests: 7
- Success rate: 99%

이 보고서는 다운로드된 Gradle HTML 테스트 리포트를 기준으로 작성한다.
현재 로컬 checkout은 해당 CI 리포트가 생성된 브랜치와 다르므로, 로컬 소스 파일과의 직접 대조는 제한된다.

## Failed Test

```text
org.example.voice.training.infrastructure.storage.FfmpegS3RecordingMediaNormalizerTest
└─ rejectsObjectOutsideAuthenticatedOwnerPrefixBeforeStorageAccess()
```

실패한 테스트는 전체 129개 중 1개이며, 실패 클래스는 `FfmpegS3RecordingMediaNormalizerTest` 하나다.

## Failure Message

리포트의 실패 상세는 다음과 같다.

```text
Expecting actual throwable to be an instance of:
  org.example.voice.common.exception.BaseException
but was:
  java.lang.IllegalStateException: media_normalization_configuration_invalid
```

관련 stack trace상 예외 발생 순서는 다음과 같다.

```text
FfmpegS3RecordingMediaNormalizer.validateConfiguration(...)
FfmpegS3RecordingMediaNormalizer.<init>(...)
FfmpegS3RecordingMediaNormalizerTest.normalizer(...)
FfmpegS3RecordingMediaNormalizerTest.rejectsObjectOutsideAuthenticatedOwnerPrefixBeforeStorageAccess(...)
```

## Root Cause

테스트 의도는 이름상 다음 보안 동작을 검증하는 것으로 보인다.

```text
인증된 사용자 소유 prefix 밖의 object key는 storage 접근 전에 BaseException으로 거부해야 한다.
```

하지만 실제 실행에서는 object key 검증 로직까지 도달하지 못했다.
`FfmpegS3RecordingMediaNormalizer` 객체 생성 시점에 `validateConfiguration()`이 먼저 실행되었고, 이 설정 검증에서 `IllegalStateException: media_normalization_configuration_invalid`가 발생했다.

즉 CI 실패의 직접 원인은 테스트 대상 동작 실패라기보다, **테스트 fixture가 media normalizer를 정상 생성할 수 없는 설정값으로 구성된 것**이다.

## Why CI Fails

CI 환경에서 해당 테스트는 다음 순서로 실패한다.

1. 테스트가 `normalizer(...)` helper로 `FfmpegS3RecordingMediaNormalizer`를 생성한다.
2. 생성자 내부에서 `validateConfiguration()`이 실행된다.
3. FFmpeg/FFprobe 경로, canonical media 설정, bucket/prefix 설정, 임시 디렉터리, 허용 codec 설정 중 하나 이상이 유효하지 않은 것으로 판단된다.
4. 생성자에서 `IllegalStateException`이 먼저 발생한다.
5. 테스트가 기대한 `BaseException` 검증까지 가지 못한다.
6. AssertJ가 "기대한 예외 타입은 BaseException인데 실제 예외는 IllegalStateException"이라고 판단하여 실패 처리한다.

## Evidence

Gradle report summary:

```text
129 tests
1 failures
7 skipped
99% successful
```

Failed class:

```text
org.example.voice.training.infrastructure.storage.FfmpegS3RecordingMediaNormalizerTest
```

Failed test:

```text
rejectsObjectOutsideAuthenticatedOwnerPrefixBeforeStorageAccess()
```

Actual exception:

```text
java.lang.IllegalStateException: media_normalization_configuration_invalid
```

Expected exception:

```text
org.example.voice.common.exception.BaseException
```

## Recommended Fix

### 1. Fix the Test Fixture

가장 우선적인 해결은 실패 테스트의 fixture가 `FfmpegS3RecordingMediaNormalizer`를 정상 생성할 수 있도록 유효한 설정을 주는 것이다.

예상 수정 대상:

```text
FfmpegS3RecordingMediaNormalizerTest.normalizer(...)
```

확인할 설정:

- ffmpeg binary path
- ffprobe binary path
- temporary working directory
- source bucket 또는 storage client mock
- allowed authenticated owner prefix
- normalized output prefix
- canonical audio/video format 설정
- media normalization enabled 여부

이 테스트는 "설정 검증 실패"를 보는 테스트가 아니라 "소유 prefix 밖 object key 거부"를 보는 테스트이므로, fixture 설정은 모두 정상이어야 한다.

### 2. Split Configuration Validation Test

`media_normalization_configuration_invalid` 자체를 검증하고 싶다면 별도 테스트로 분리하는 것이 좋다.

```text
invalidConfigurationThrowsIllegalStateException()
```

이렇게 분리하면 각 테스트의 의도가 명확해진다.

### 3. Ensure Security Validation Runs Before Storage Access

해당 실패 테스트의 목적이 보안 검증이라면, 정상 설정을 가진 normalizer에서 다음 순서를 보장해야 한다.

```text
request object key ownership/prefix validation
-> reject invalid owner prefix with BaseException
-> only then access S3/storage
```

즉, object key가 인증 사용자 prefix 밖이면 storage 접근 전에 즉시 `BaseException`을 던져야 한다.

### 4. CI Environment Check

CI에서 FFmpeg 기반 테스트를 실행하려면 둘 중 하나를 선택해야 한다.

- CI runner에 ffmpeg/ffprobe를 설치하고 경로를 테스트 설정에 주입한다.
- ffmpeg/ffprobe가 필요한 integration 성격 테스트는 환경 조건이 없을 때 `Assumptions`로 skip한다.

다만 이번 실패 테스트는 실제 normalization 성공 케이스가 아니라 prefix guard 테스트이므로, 가능하면 ffmpeg 실행 의존 없이 검증되도록 테스트 seam을 분리하는 편이 더 안정적이다.

## Suggested Resolution Plan

1. CI 대상 브랜치에서 `FfmpegS3RecordingMediaNormalizerTest.normalizer(...)`를 확인한다.
2. 실패 테스트가 사용하는 설정값 중 `validateConfiguration()` 조건을 만족하지 못하는 값을 찾는다.
3. 해당 테스트의 fixture를 정상 설정으로 수정한다.
4. 설정 오류 검증은 별도 테스트로 분리한다.
5. prefix guard가 storage 접근 전에 `BaseException`을 던지는지 다시 확인한다.
6. CI에서 `./gradlew test --tests org.example.voice.training.infrastructure.storage.FfmpegS3RecordingMediaNormalizerTest`를 먼저 실행한다.
7. 통과 후 전체 `./gradlew test`를 실행한다.

## Notes

- 현재 로컬 브랜치에는 리포트에 등장하는 `org.example.voice.training.infrastructure.storage.FfmpegS3RecordingMediaNormalizer` 소스가 존재하지 않는다.
- 따라서 이 문서는 로컬 코드 수정 보고서가 아니라, 다운로드된 CI 테스트 리포트 기준의 실패 원인 분석 보고서다.
- 현재 리포트 기준으로는 Redis Stream, cache, auth, mypage, analysis ingestion 관련 테스트는 실패 원인이 아니다.
