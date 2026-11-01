package org.example.voice.auth.infrastructure;

import jakarta.persistence.EntityManager;
import org.example.voice.auth.application.TokenService;
import org.example.voice.auth.domain.model.IssuedTokens;
import org.example.voice.auth.domain.port.RefreshTokenReader;
import org.example.voice.auth.domain.port.LoginSessionCache;
import org.example.voice.auth.domain.port.RefreshTokenWriter;
import org.example.voice.auth.infrastructure.sessioncache.*;
import org.example.voice.common.security.LoginUser;
import org.example.voice.user.domain.entity.User;
import org.example.voice.user.domain.port.UserSessionRevoker;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="VC_BE_TEST_POSTGRES_URL",matches=".+")
@EnabledIfEnvironmentVariable(named="VC_BE_TEST_REDIS_HOST",matches=".+")
@SpringBootTest(properties="auth.session-cache.enabled=true")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SessionCachePostgresIntegrationTest {
    private static final String NAMESPACE="test:auth:{sessions}:postgres:"+UUID.randomUUID();
    @DynamicPropertySource static void redis(DynamicPropertyRegistry properties) {
        properties.add("spring.data.redis.host",()->System.getenv("VC_BE_TEST_REDIS_HOST"));
        properties.add("spring.data.redis.port",()->System.getenv("VC_BE_TEST_REDIS_PORT"));
        properties.add("spring.data.redis.password",()->System.getenv().getOrDefault("VC_BE_TEST_REDIS_PASSWORD",""));
        properties.add("spring.data.redis.ssl.enabled",()->true);
        properties.add("auth.session-cache.namespace",()->NAMESPACE);
    }
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired TokenService tokens;
    @Autowired RefreshTokenReader reader;
    @Autowired RefreshTokenWriter writer;
    @Autowired LoginSessionCache cache;
    @Autowired UserSessionRevoker revoker;
    @Autowired MockMvc mvc;
    @Autowired org.springframework.data.redis.core.StringRedisTemplate redisTemplate;
    @MockitoSpyBean RefreshTokenJpaRepository repository;
    @MockitoSpyBean JwtTokenProvider provider;
    @Value("${auth.jwt.secret}") String jwtSecret;
    @Value("${auth.jwt.issuer}") String issuer;
    private User user;
    private IssuedTokens issued;
    private TransactionTemplate tx;
    @BeforeEach void setup() throws Exception {
        tx=new TransactionTemplate(manager);
        user=tx.execute(status->{var row=User.createLocal(UUID.randomUUID()+"@example.invalid","hash","session cache",OffsetDateTime.now(ZoneOffset.UTC));em.persist(row);return row;});
        issued=tokens.issueSession(user);
        // Cold Redis/DB bootstrap is permitted to fall back. Cache-hit assertions start warm.
        boolean ready=false;
        for(int i=0;i<50;i++) {
            var lookup=cache.lookup(-user.getId());
            if(lookup.kind()==LoginSessionCache.Kind.LOAD) {cache.publish(-user.getId(),lookup.ticket(),Optional.empty());ready=true;break;}
            if(lookup.kind()==LoginSessionCache.Kind.CACHED) {ready=true;break;}
            Thread.sleep(100);
        }
        assertThat(ready).as("session cache bootstrap completed").isTrue();
        clearInvocations(repository,provider);
    }
    @AfterEach void cleanup() {
        tokens.revoke(user.getId(),null);
        tx.executeWithoutResult(status->{var row=em.find(User.class,user.getId());if(row!=null)em.remove(row);});
        redisTemplate.delete(NAMESPACE+":user:"+(-user.getId()));
    }
    @AfterAll void cleanupNamespace() {redisTemplate.delete(NAMESPACE+":meta");}
    private String sid() {return issued.sessionId();}
    private boolean active(String sid){return reader.isActiveSession(user.getId(),sid,OffsetDateTime.now(ZoneOffset.UTC));}
    private void getCapabilities(String token,int status) throws Exception {
        mvc.perform(get("/api/analysis-capabilities").header("Authorization","Bearer "+token)).andExpect(status().is(status));
    }

    @Test void cacheHitRemovesSessionSqlWhileEveryHttpRequestStillVerifiesJwt() throws Exception {
        getCapabilities(issued.accessToken(),200);getCapabilities(issued.accessToken(),200);getCapabilities(issued.accessToken(),200);
        verify(repository,times(1)).findSessionState(user.getId());
        verify(repository,never()).existsByUserIdAndSessionIdAndExpiresAtAfter(any(),any(),any());
        verify(provider,times(3)).parseAccessToken(issued.accessToken());
    }
    @Test void invalidAndExpiredJwtAreRejectedWithoutDatabaseLookupEvenWithWarmSession() throws Exception {
        assertThat(active(sid())).isTrue();clearInvocations(repository);
        getCapabilities("invalid.token",401);
        var key=new javax.crypto.spec.SecretKeySpec(jwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8),"HmacSHA256");
        var encoder=new org.springframework.security.oauth2.jwt.NimbusJwtEncoder(new com.nimbusds.jose.jwk.source.ImmutableSecret<>(key));
        var instant=Instant.now();
        var claims=org.springframework.security.oauth2.jwt.JwtClaimsSet.builder().issuer(issuer).subject(user.getId().toString())
                .issuedAt(instant.minusSeconds(600)).expiresAt(instant.minusSeconds(300)).claim("role",user.getRole().name())
                .claim("sid",sid()).claim("type","access").build();
        var expired=encoder.encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(
                org.springframework.security.oauth2.jwt.JwsHeader.with(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build(),claims));
        getCapabilities(expired.getTokenValue(),401);
        verifyNoInteractions(repository);
    }
    @Test void newLoginImmediatelyRejectsPreviousAccessAndAllowsOnlyTheNewSid() throws Exception {
        assertThat(active(sid())).isTrue();var next=tokens.issueSession(user);
        getCapabilities(issued.accessToken(),401);getCapabilities(next.accessToken(),200);
    }
    @Test void refreshKeepsInflightAccessValidAndLogoutRevokesBoth() throws Exception {
        assertThat(active(sid())).isTrue();var renewed=tokens.rotate(issued.refreshToken());
        getCapabilities(issued.accessToken(),200);getCapabilities(renewed.accessToken(),200);
        tokens.revoke(user.getId(),renewed.refreshToken());
        getCapabilities(issued.accessToken(),401);getCapabilities(renewed.accessToken(),401);
    }
    @Test void rollbackInvalidatesCacheAndRestoresDatabaseAuthoritativeSession() {
        assertThat(active(sid())).isTrue();
        tx.executeWithoutResult(status->{writer.deleteByUserId(user.getId());assertThat(cache.lookup(user.getId()).kind()).isEqualTo(LoginSessionCache.Kind.BYPASS);status.setRollbackOnly();});
        assertThat(active(sid())).isTrue();assertThat(active(sid())).isTrue();
    }
    @Test void userLifecycleRevocationInvalidatesAWarmSession() throws Exception {
        assertThat(active(sid())).isTrue();tx.executeWithoutResult(status->revoker.revokeAll(user.getId()));
        getCapabilities(issued.accessToken(),401);
    }
    @Test void readerCannotPublishOldDatabaseSnapshotAcrossConcurrentLogout() throws Exception {
        var ticket=cache.lookup(user.getId());assertThat(ticket.kind()).isEqualTo(LoginSessionCache.Kind.LOAD);
        var stale=repository.findSessionState(user.getId());
        tokens.revoke(user.getId(),null);cache.publish(user.getId(),ticket.ticket(),stale);
        getCapabilities(issued.accessToken(),401);
    }
    @Test void bootstrapWaitsForOlderDatabaseTransactionBeforeItCanOpenGlobalCache() throws Exception {
        var started=new CountDownLatch(1);var release=new CountDownLatch(1);
        var executor=Executors.newSingleThreadExecutor();
        try {
            Future<?> pending=executor.submit(()->tx.executeWithoutResult(status->{
                em.createNativeQuery("SELECT 1").getSingleResult();started.countDown();
                try {if(!release.await(8,TimeUnit.SECONDS))throw new IllegalStateException("fixture timeout");}
                catch(InterruptedException error){Thread.currentThread().interrupt();throw new IllegalStateException(error);}
            }));
            assertThat(started.await(5,TimeUnit.SECONDS)).isTrue();
            var bootstrap=new SessionCacheBootstrap(new org.springframework.jdbc.core.JdbcTemplate(
                    ((org.springframework.orm.jpa.JpaTransactionManager)manager).getDataSource()));
            assertThat(bootstrap.awaitOlderTransactions(Duration.ofMillis(150))).isFalse();
            release.countDown();pending.get(5,TimeUnit.SECONDS);
            assertThat(bootstrap.awaitOlderTransactions(Duration.ofSeconds(2))).isTrue();
        } finally {release.countDown();executor.shutdownNow();}
    }
    @Test void unavailableTransactionActivityTrackingCannotOpenTheBootstrapGate() {
        tx.executeWithoutResult(status->{
            em.createNativeQuery("SET LOCAL track_activities=off").executeUpdate();
            var bootstrap=new SessionCacheBootstrap(new org.springframework.jdbc.core.JdbcTemplate(
                    ((org.springframework.orm.jpa.JpaTransactionManager)manager).getDataSource()));
            assertThat(bootstrap.awaitOlderTransactions(Duration.ofMillis(100))).isFalse();
        });
    }
}
