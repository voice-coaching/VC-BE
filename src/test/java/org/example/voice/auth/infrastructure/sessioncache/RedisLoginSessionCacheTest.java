package org.example.voice.auth.infrastructure.sessioncache;

import org.example.voice.auth.domain.model.LoginSessionState;
import org.example.voice.auth.domain.port.LoginSessionCache;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="VC_BE_TEST_REDIS_HOST",matches=".+")
class RedisLoginSessionCacheTest {
    private LettuceConnectionFactory factory;
    private StringRedisTemplate redis;
    private SessionCacheProperties settings;
    private SessionCacheBootstrap bootstrap;
    private RedisLoginSessionCache cache;
    private final LoginSessionState active=new LoginSessionState("current-sid",OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.MILLIS).plusHours(1));
    @BeforeEach void setup() {
        var server=new RedisStandaloneConfiguration(System.getenv("VC_BE_TEST_REDIS_HOST"),Integer.parseInt(System.getenv("VC_BE_TEST_REDIS_PORT")));
        String password=System.getenv("VC_BE_TEST_REDIS_PASSWORD");if(password!=null)server.setPassword(password);
        factory=new LettuceConnectionFactory(server,LettuceClientConfiguration.builder().useSsl().and().commandTimeout(Duration.ofSeconds(2)).build());
        factory.afterPropertiesSet();factory.start();redis=new StringRedisTemplate(factory);
        settings=new SessionCacheProperties();settings.setNamespace("test:auth:{sessions}:"+UUID.randomUUID());
        bootstrap=mock(SessionCacheBootstrap.class);when(bootstrap.awaitOlderTransactions(any())).thenReturn(true);
        cache=new RedisLoginSessionCache(redis,bootstrap,settings);
    }
    @AfterEach void cleanup() {
        try {
            if(redis!=null && settings!=null) {
                // Only this test's exact, bounded set of keys, never other Redis data or Streams.
                var keys=new ArrayList<String>();keys.add(meta());for(int i=0;i<=9;i++)keys.add(key(i));redis.delete(keys);
            }
        } finally {if(factory!=null)factory.destroy();}
    }
    private String meta(){return settings.getNamespace()+":meta";}
    private String key(long user){return settings.getNamespace()+":user:"+user;}
    private void fill(long user,LoginSessionState state) {
        var ticket=cache.lookup(user);assertThat(ticket.kind()).isEqualTo(LoginSessionCache.Kind.LOAD);
        cache.publish(user,ticket.ticket(),Optional.ofNullable(state));
    }
    @Test void realScriptsCacheCurrentSessionAndAbsenceWithoutCredentials() {
        fill(1,active);var hit=cache.lookup(1L);assertThat(hit.kind()).isEqualTo(LoginSessionCache.Kind.CACHED);
        assertThat(hit.session()).isEqualTo(active);assertThat(hit.session().accepts("other",OffsetDateTime.now())).isFalse();
        assertThat(redis.opsForHash().entries(key(1)).toString()).doesNotContain("refreshToken","accessToken","password");
        fill(2,null);assertThat(cache.lookup(2L).kind()).isEqualTo(LoginSessionCache.Kind.CACHED);assertThat(cache.lookup(2L).session()).isNull();
    }
    @Test void lateCacheFillCannotReintroduceRevokedSession() {
        var stale=cache.lookup(1L);String change=cache.beginChange(1L);cache.finishChange(1L,change);
        cache.publish(1L,stale.ticket(),Optional.of(active));
        assertThat(cache.lookup(1L).kind()).isEqualTo(LoginSessionCache.Kind.LOAD);
    }
    @Test void multipleWritersKeepBarrierUntilAllHaveCompleted() {
        fill(1,active);String first=cache.beginChange(1L),second=cache.beginChange(1L);
        assertThat(redis.getExpire(key(1))).isEqualTo(-1L);
        cache.finishChange(1L,first);assertThat(cache.lookup(1L).kind()).isEqualTo(LoginSessionCache.Kind.BYPASS);
        cache.finishChange(1L,second);assertThat(cache.lookup(1L).kind()).isEqualTo(LoginSessionCache.Kind.LOAD);
    }
    @Test void independentInstancesObserveTheSameInvalidation() {
        var other=new RedisLoginSessionCache(redis,bootstrap,settings);other.lookup(0L);
        fill(1,active);assertThat(other.lookup(1L).kind()).isEqualTo(LoginSessionCache.Kind.CACHED);
        String change=cache.beginChange(1L);assertThat(other.lookup(1L).kind()).isEqualTo(LoginSessionCache.Kind.BYPASS);
        cache.finishChange(1L,change);assertThat(other.lookup(1L).kind()).isEqualTo(LoginSessionCache.Kind.LOAD);
    }
    @Test void globalBootstrapGateBlocksHitsAndFillsUntilDatabaseBarrierCompletes() {
        fill(1,active);when(bootstrap.awaitOlderTransactions(any())).thenReturn(false);
        var joining=new RedisLoginSessionCache(redis,bootstrap,settings);
        assertThat(joining.lookup(1L).kind()).isEqualTo(LoginSessionCache.Kind.BYPASS);
        assertThat(cache.lookup(1L).kind()).isEqualTo(LoginSessionCache.Kind.BYPASS);
        when(bootstrap.awaitOlderTransactions(any())).thenReturn(true);
        var recovered=new RedisLoginSessionCache(redis,bootstrap,settings);
        assertThat(recovered.lookup(1L).kind()).isEqualTo(LoginSessionCache.Kind.LOAD);
    }
    @Test void changedRedisIncarnationRequiresDatabaseBootstrapAndDiscardsOldValues() {
        fill(1,active);redis.opsForHash().put(meta(),"runId","previous-server");
        assertThat(cache.lookup(1L).kind()).isEqualTo(LoginSessionCache.Kind.BYPASS);
        assertThat(cache.lookup(1L).kind()).isEqualTo(LoginSessionCache.Kind.LOAD);
        verify(bootstrap,times(2)).awaitOlderTransactions(any());
    }
    @Test void evictionCounterChangeDisablesExistingHits() {
        fill(1,active);redis.opsForHash().put(meta(),"evicted","-1");
        assertThat(cache.lookup(1L).kind()).isEqualTo(LoginSessionCache.Kind.BYPASS);
        assertThat(cache.lookup(1L).kind()).isEqualTo(LoginSessionCache.Kind.LOAD);
    }
    @Test void databaseBarrierCanRecoverAbandonedMarkersOnlyInANewEpoch() {
        fill(1,active);cache.beginChange(1L);
        var recovered=new RedisLoginSessionCache(redis,bootstrap,settings);
        assertThat(recovered.lookup(1L).kind()).isEqualTo(LoginSessionCache.Kind.LOAD);
        verify(bootstrap,times(2)).awaitOlderTransactions(any());
    }
    @Test void expiredCacheEntryReloadsAndSessionExpiryIsIndependentlyEnforced() {
        fill(1,active);redis.expire(key(1),Duration.ZERO);
        assertThat(cache.lookup(1L).kind()).isEqualTo(LoginSessionCache.Kind.LOAD);
        assertThat(active.accepts("current-sid",active.expiresAt())).isFalse();
    }
    @Test void corruptedCacheRecordNeverAuthenticates() {
        fill(1,active);redis.opsForHash().put(key(1),"value","invalid");
        assertThat(cache.lookup(1L).kind()).isEqualTo(LoginSessionCache.Kind.BYPASS);
    }
    @Test void redisConnectionFailureFallsBackOnReadsAndRefusesUnfencedChanges() {
        var broken=mock(StringRedisTemplate.class);
        when(broken.execute(any(org.springframework.data.redis.core.script.RedisScript.class),anyList(),any(),any(),any(),any()))
                .thenThrow(new org.springframework.data.redis.RedisConnectionFailureException("private endpoint diagnostic"));
        var unavailable=new RedisLoginSessionCache(broken,bootstrap,settings);
        assertThat(unavailable.lookup(1L).kind()).isEqualTo(LoginSessionCache.Kind.BYPASS);
        assertThatThrownBy(()->unavailable.beginChange(1L))
                .isInstanceOf(org.example.voice.auth.exception.SessionStoreUnavailableException.class)
                .hasMessageNotContaining("private endpoint diagnostic");
    }
    @Test void failedCompletionKeepsOtherInstancesOnDatabaseValidation() {
        cache.lookup(0L);
        var wrapped=spy(redis);var failing=new RedisLoginSessionCache(wrapped,bootstrap,settings);
        failing.lookup(0L);
        var ticket=failing.lookup(1L);failing.publish(1L,ticket.ticket(),Optional.of(active));
        String change=failing.beginChange(1L);
        doThrow(new org.springframework.data.redis.RedisConnectionFailureException("private endpoint diagnostic"))
                .when(wrapped).execute(any(org.springframework.data.redis.core.script.RedisScript.class),anyList(),eq("END"),any(),any(),any());
        failing.finishChange(1L,change);
        assertThat(cache.lookup(1L).kind()).isEqualTo(LoginSessionCache.Kind.BYPASS);
        assertThat(redis.getExpire(key(1))).isEqualTo(-1L);
    }
}
