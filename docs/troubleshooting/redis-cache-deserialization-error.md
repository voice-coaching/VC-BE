# Redis Cache Deserialization Error

## 발생 상황

EC2에 JAR로 배포한 서버에서 홈 화면 진입 시 Redis 캐시 역직렬화 오류가 발생했다.

프론트 기준으로 첫 렌더링에서는 API가 정상 응답했지만, 다른 화면에 들어갔다가 다시 홈 화면으로 돌아오면 같은 API에서 500 에러가 발생했다.

영향 API:

- `GET /api/home`
- `GET /api/recommendations?limit=3`
- `GET /api/practice-contents`
- `GET /api/practice-contents/{contentId}/reference-audios`
- `GET /api/practice-contents/{contentId}/recommendations`
- `GET /api/users/me/strengths-weaknesses?period=MONTH&limit=5`
- `GET /api/users/me/score-trends?metric=PRONUNCIATION&period=MONTH`
- `GET /api/users/me/weakness-recommendations?limit=3`

## 에러 로그

처음 확인된 로그는 Redis 캐시 값이 원래 domain model 타입으로 복원되지 않고 `LinkedHashMap`으로 역직렬화된 뒤 캐스팅되면서 발생했다.

```text
java.lang.ClassCastException:
class java.util.LinkedHashMap cannot be cast to class
org.example.voice.practicecontent.domain.model.PracticeContentPageData
```

```text
java.lang.ClassCastException:
class java.util.LinkedHashMap cannot be cast to class
org.example.voice.home.domain.model.RecommendationItemData
```

이후 Redis value serializer에 타입 보존 설정을 적용한 뒤, 홈/추천 API에서 cache hit 시 다음 오류가 추가로 확인되었다.

```text
org.springframework.data.redis.serializer.SerializationException:
Could not read JSON: Unexpected token (JsonToken.START_OBJECT),
expected JsonToken.VALUE_STRING: need String, Number of Boolean value that contains type id
```

마이페이지 API에서도 같은 계열의 오류가 추가로 확인되었다. 로그상 직접 실패한 지점은 `MyPagePersistenceAdapter.findUnitScores(...)` cache hit 구간이었다.

```text
org.springframework.data.redis.serializer.SerializationException:
Could not read JSON: Unexpected token (JsonToken.END_ARRAY),
expected JsonToken.VALUE_STRING: need String, Number of Boolean value that contains type id
```

## 원인

`@Cacheable` 기반 Redis 캐시는 `RedisCacheManager`의 `RedisCacheConfiguration`을 통해 value를 직렬화하고 역직렬화한다.

기존 설정은 `GenericJacksonJsonRedisSerializer.builder().build()`만 사용하고 있어 Redis value에 Java 타입 정보가 충분히 보존되지 않았다. 이 때문에 cache hit 시 record/domain model이 원래 타입으로 복원되지 않고 `LinkedHashMap`으로 복원될 수 있었다.

또한 `HomeReaderImpl.findRecommendations(...)`는 최상위 반환값으로 `List<RecommendationItemData>`를 직접 반환하고 있었다. Redis 캐시 관점에서 메서드 반환값 전체가 cache value가 되므로, 최상위 값이 generic `List<T>`이면 cache hit 시 리스트와 원소 타입 복원이 안정적이지 않았다.

같은 패턴이 마이페이지 조회 캐시에도 남아 있었다. `MyPagePersistenceAdapter.findUnitScores(...)`, `findScoreTrend(...)`, `findRecommendations(...)`가 모두 `@Cacheable` 메서드에서 최상위 `List<T>`를 직접 반환했다.

전체 캐시 정합성 점검 과정에서 `ReferenceAudioReaderImpl.findReferenceAudiosByContentId(...)`와 `PracticeContentReaderImpl.findRecommendationsByContentId(...)`도 목록 캐시를 최상위 `List<T>` 또는 `Optional<List<T>>` 형태로 반환하고 있어 같은 오류가 발생할 수 있는 후보로 확인되었다.

정리하면 문제는 두 가지였다.

- Redis value serializer가 타입 정보를 보존하지 않았다.
- 홈 추천 캐시, 마이페이지 목록 캐시, 연습 콘텐츠 하위 목록 캐시가 최상위 `List<T>` 계열 값을 직접 캐싱했다.

## 원인이 된 코드

### Redis Cache 설정

기존 `CacheConfig`는 `RedisCacheManager` value serializer를 생성할 때 default typing을 활성화하지 않았다.

```java
private RedisCacheConfiguration baseConfiguration(Duration ttl) {
    return RedisCacheConfiguration.defaultCacheConfig()
            .disableCachingNullValues()
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                    GenericJacksonJsonRedisSerializer.builder().build()
            ))
            .entryTtl(ttl);
}
```

이 설정에서는 Redis에서 value를 `Object.class`로 읽을 때 JSON에 타입 힌트가 없으면 domain model 대신 `LinkedHashMap`으로 역직렬화될 수 있다.

### 추천 캐시 반환 타입

기존 `HomeReader` port는 추천 목록을 `List<RecommendationItemData>`로 직접 반환했다.

```java
List<RecommendationItemData> findRecommendations(Long userId, ContentType type, int limit);
```

기존 `HomeReaderImpl`도 `@Cacheable` 메서드에서 최상위 `List`를 그대로 반환했다.

```java
@Cacheable(
        cacheNames = HomeCacheNames.RECOMMENDATIONS,
        key = "T(org.example.voice.home.infrastructure.cache.HomeCacheKeys).recommendations(#p0, #p1, #p2)"
)
public List<RecommendationItemData> findRecommendations(Long userId, ContentType type, int limit) {
    PageRequest pageRequest = PageRequest.of(
            0,
            limit,
            Sort.by(Sort.Order.desc("publishedAt").nullsLast(), Sort.Order.desc("createdAt"))
    );
    return practiceContentJpaRepository.findAll(recommendationSpec(type), pageRequest)
            .getContent()
            .stream()
            .map(this::toRecommendationData)
            .toList();
}
```

첫 호출은 cache miss라서 DB 조회 결과를 바로 반환하므로 정상 동작했다. 하지만 같은 조건으로 다시 호출하면 Redis cache hit가 발생하고, 이때 최상위 `List<T>` 역직렬화가 실패했다.

### 마이페이지 캐시 반환 타입

기존 `MyPageReader` port도 목록 조회 결과를 `List<T>`로 직접 반환했다.

```java
List<MyPageData.UnitScore> findUnitScores(Long userId, OffsetDateTime from, OffsetDateTime to);
List<MyPageData.TrendPoint> findScoreTrend(Long userId, String metric, OffsetDateTime from, OffsetDateTime to);
List<MyPageData.Recommendation> findRecommendations(List<String> targetUnits, ContentType contentType, int limit);
```

기존 `MyPagePersistenceAdapter`의 `@Cacheable` 메서드도 최상위 `List`를 그대로 캐싱했다.

```java
@Cacheable(
        cacheNames = MyPageCacheNames.UNIT_SCORES,
        key = "T(org.example.voice.mypage.infrastructure.cache.MyPageCacheKeys).unitScores(#p0, #p1, #p2)"
)
public List<MyPageData.UnitScore> findUnitScores(Long userId, OffsetDateTime from, OffsetDateTime to) {
    return rows.stream()
            .map(row -> new MyPageData.UnitScore(...))
            .toList();
}
```

`strengths-weaknesses`와 `weakness-recommendations`는 내부적으로 `findUnitScores(...)`를 호출하므로, 해당 캐시 key가 이전 배열 형태로 저장되어 있거나 최상위 리스트 역직렬화가 흔들리면 두 API 모두 500 오류가 발생할 수 있었다.

### 연습 콘텐츠 하위 목록 캐시 반환 타입

전체 코드 점검 중 `ReferenceAudioReader`와 `PracticeContentReader`에도 같은 위험 패턴이 남아 있었다.

```java
List<ReferenceAudioData> findReferenceAudiosByContentId(Long contentId);
Optional<List<PracticeContentRecommendationData>> findRecommendationsByContentId(Long contentId);
```

두 메서드는 각각 `@Cacheable` 경계에서 참조 음성 목록과 연습 콘텐츠 추천 목록을 캐싱한다. 따라서 cache hit 시 이전 직렬화 포맷 또는 최상위 generic list 역직렬화 문제로 같은 500 오류가 발생할 수 있었다.

## 해결

### RedisCacheManager value serializer 수정

`CacheConfig`에서 `RedisCacheManager`가 사용하는 value serializer에 default typing을 활성화했다. 또한 `PolymorphicTypeValidator`를 적용해 `org.example.voice.*` 타입과 명시적 deserializer가 있는 타입만 역직렬화 대상이 되도록 제한했다.

```java
private RedisCacheConfiguration baseConfiguration(Duration ttl) {
    return RedisCacheConfiguration.defaultCacheConfig()
            .disableCachingNullValues()
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                    redisValueSerializer()
            ))
            .entryTtl(ttl);
}

private GenericJacksonJsonRedisSerializer redisValueSerializer() {
    PolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
            .allowIfSubType(APPLICATION_PACKAGE_PREFIX)
            .allowSubTypesWithExplicitDeserializer()
            .allowIfSubTypeIsArray()
            .build();

    return GenericJacksonJsonRedisSerializer.builder()
            .typePropertyName(CACHE_TYPE_PROPERTY_NAME)
            .enableDefaultTyping(typeValidator)
            .build();
}
```

Redis cache prefix는 변경하지 않았다. 기존 캐시는 배포 시 운영 환경에서 직접 삭제하는 방식으로 처리한다.

### 추천 캐시 최상위 반환 타입 보정

최상위 `List<RecommendationItemData>`를 직접 캐싱하지 않도록 `RecommendationListData` domain model을 추가했다.

```java
public record RecommendationListData(
        List<RecommendationItemData> items
) {
}
```

`HomeReader` port 반환 타입을 명확한 domain model로 변경했다.

```java
RecommendationListData findRecommendations(Long userId, ContentType type, int limit);
```

`HomeReaderImpl`은 내부적으로 목록을 만들되, 캐시되는 최상위 반환값은 `RecommendationListData`가 되도록 변경했다.

```java
@Cacheable(
        cacheNames = HomeCacheNames.RECOMMENDATIONS,
        key = "T(org.example.voice.home.infrastructure.cache.HomeCacheKeys).recommendations(#p0, #p1, #p2)"
)
public RecommendationListData findRecommendations(Long userId, ContentType type, int limit) {
    PageRequest pageRequest = PageRequest.of(
            0,
            limit,
            Sort.by(Sort.Order.desc("publishedAt").nullsLast(), Sort.Order.desc("createdAt"))
    );
    List<RecommendationItemData> items = practiceContentJpaRepository.findAll(recommendationSpec(type), pageRequest)
            .getContent()
            .stream()
            .map(this::toRecommendationData)
            .toList();

    return new RecommendationListData(items);
}
```

서비스 계층에서는 기존 DTO 변환 흐름을 유지하기 위해 `items()`를 꺼내 전달한다.

```java
homeReader.findRecommendations(userId, null, 1).items()
```

```java
homeReader.findRecommendations(userId, condition == null ? null : condition.type(), limit).items()
```

### 마이페이지 캐시 최상위 반환 타입 보정

마이페이지의 캐시 대상 목록들도 최상위 `List<T>`를 직접 캐싱하지 않도록 domain model wrapper를 추가했다.

```java
public record UnitScoreList(List<UnitScore> items) {}
public record TrendPointList(List<TrendPoint> items) {}
public record RecommendationList(List<Recommendation> items) {}
```

`MyPageReader` port 반환 타입을 wrapper domain model로 변경했다.

```java
UnitScoreList findUnitScores(Long userId, OffsetDateTime from, OffsetDateTime to);
TrendPointList findScoreTrend(Long userId, String metric, OffsetDateTime from, OffsetDateTime to);
RecommendationList findRecommendations(List<String> targetUnits, ContentType contentType, int limit);
```

`MyPagePersistenceAdapter`는 내부적으로 목록을 만들되, 캐시되는 최상위 반환값은 wrapper가 되도록 변경했다.

```java
public MyPageData.UnitScoreList findUnitScores(Long userId, OffsetDateTime from, OffsetDateTime to) {
    List<MyPageData.UnitScore> items = rows.stream()
            .map(row -> new MyPageData.UnitScore(...))
            .toList();

    return new MyPageData.UnitScoreList(items);
}
```

서비스 계층에서는 기존 응답 조립 흐름을 유지하기 위해 `items()`를 꺼내 사용한다.

```java
reader.findUnitScores(userId, range.from(), range.toExclusive()).items()
```

```java
reader.findScoreTrend(userId, normalized, range.from(), range.toExclusive()).items()
```

### 연습 콘텐츠 하위 목록 캐시 최상위 반환 타입 보정

연습 콘텐츠 하위 목록 캐시도 명확한 domain model wrapper를 사용하도록 변경했다.

```java
public record ReferenceAudioListData(List<ReferenceAudioData> items) {}
public record PracticeContentRecommendationListData(List<PracticeContentRecommendationData> items) {}
```

`ReferenceAudioReader`와 `PracticeContentReader` port 반환 타입을 wrapper로 변경했다.

```java
ReferenceAudioListData findReferenceAudiosByContentId(Long contentId);
Optional<PracticeContentRecommendationListData> findRecommendationsByContentId(Long contentId);
```

서비스 계층에서는 기존 DTO 변환 흐름을 유지하기 위해 `items()`를 꺼내 전달한다.

```java
referenceAudioReader.findReferenceAudiosByContentId(contentId).items()
```

```java
practiceContentReader.findRecommendationsByContentId(contentId)
        .map(recommendations -> PracticeContentRecommendationsResponseDto.from(recommendations.items()))
```

## 배포 시 조치

이번 변경은 Redis cache prefix를 변경하지 않는다. 따라서 기존 Redis에 이전 직렬화 방식으로 저장된 캐시 값이 남아 있으면 배포 직후 cache hit 시 오류가 재발할 수 있다.

Redis가 조회 캐시 전용이면 배포 전후로 기존 캐시를 삭제한다.

```bash
sudo bash -c 'cd /etc/voice && set -a && . /etc/alpha-backend.env && set +a && docker compose --env-file /etc/alpha-backend.env exec redis redis-cli -a "$REDIS_PASSWORD" FLUSHDB'
```

Redis에 캐시 외 데이터가 섞여 있다면 전체 `FLUSHDB`를 사용하지 말고 `--scan`으로 키를 확인한 뒤 캐시 키만 삭제한다.

```bash
sudo bash -c 'cd /etc/voice && set -a && . /etc/alpha-backend.env && set +a && docker compose --env-file /etc/alpha-backend.env exec redis redis-cli -a "$REDIS_PASSWORD" --scan'
```

## 재발 방지 포인트

- `@Cacheable`이 붙은 메서드는 최상위 반환값으로 generic `List<T>`를 직접 반환하지 않는 것을 우선 검토한다.
- 목록 캐시가 필요하면 `*ListData`, `*PageData` 같은 명확한 domain model wrapper를 사용한다.
- Redis value serializer 변경 시 기존 Redis 캐시 삭제 또는 cache prefix/version 전략을 함께 검토한다.
- cache miss뿐 아니라 cache hit 상황도 배포 전에 확인한다.

## 관련 변경

- `src/main/java/org/example/voice/common/config/CacheConfig.java`
- `src/main/java/org/example/voice/home/domain/model/RecommendationListData.java`
- `src/main/java/org/example/voice/home/domain/port/HomeReader.java`
- `src/main/java/org/example/voice/home/infrastructure/HomeReaderImpl.java`
- `src/main/java/org/example/voice/home/application/HomeService.java`
- `src/main/java/org/example/voice/home/application/RecommendationService.java`
- `src/main/java/org/example/voice/mypage/domain/model/MyPageData.java`
- `src/main/java/org/example/voice/mypage/domain/port/MyPageReader.java`
- `src/main/java/org/example/voice/mypage/infrastructure/MyPagePersistenceAdapter.java`
- `src/main/java/org/example/voice/mypage/application/MyPageService.java`
- `src/main/java/org/example/voice/practicecontent/domain/model/ReferenceAudioListData.java`
- `src/main/java/org/example/voice/practicecontent/domain/model/PracticeContentRecommendationListData.java`
- `src/main/java/org/example/voice/practicecontent/domain/port/ReferenceAudioReader.java`
- `src/main/java/org/example/voice/practicecontent/domain/port/PracticeContentReader.java`
- `src/main/java/org/example/voice/practicecontent/infrastructure/ReferenceAudioReaderImpl.java`
- `src/main/java/org/example/voice/practicecontent/infrastructure/PracticeContentReaderImpl.java`
- `src/main/java/org/example/voice/practicecontent/application/ReferenceAudioService.java`
- `src/main/java/org/example/voice/practicecontent/application/PracticeContentService.java`
