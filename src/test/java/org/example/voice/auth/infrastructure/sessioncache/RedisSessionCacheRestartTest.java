package org.example.voice.auth.infrastructure.sessioncache;

import org.example.voice.auth.domain.model.LoginSessionState;
import org.example.voice.auth.domain.port.LoginSessionCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.*;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Owns a separate loopback process; never restarts the suite's shared Redis or an external service. */
@EnabledIfEnvironmentVariable(named="VC_BE_TEST_AUTH_REDIS_EXECUTABLE",matches=".+")
@EnabledIfEnvironmentVariable(named="VC_BE_TEST_AUTH_REDIS_CERT",matches=".+")
class RedisSessionCacheRestartTest {
    @TempDir Path directory;
    private Process process;
    private int port;
    private int starts;

    @Test void restoredStaleRedisSnapshotCannotReauthenticateARevokedSession() throws Exception {
        try(var socket=new ServerSocket(0)) {port=socket.getLocalPort();}
        LettuceConnectionFactory factory=null;
        try {
            start();
            factory=new LettuceConnectionFactory(new RedisStandaloneConfiguration("127.0.0.1",port),
                    LettuceClientConfiguration.builder().useSsl().and().commandTimeout(Duration.ofSeconds(2)).build());
            factory.afterPropertiesSet();factory.start();
            var redis=new StringRedisTemplate(factory);ready(redis);
            var settings=new SessionCacheProperties();settings.setNamespace("test:auth:{sessions}:restart:"+UUID.randomUUID());
            var bootstrap=mock(SessionCacheBootstrap.class);when(bootstrap.awaitOlderTransactions(any())).thenReturn(true);
            var cache=new RedisLoginSessionCache(redis,bootstrap,settings);
            var ticket=cache.lookup(1L);
            cache.publish(1L,ticket.ticket(),Optional.of(new LoginSessionState("revoked-sid",OffsetDateTime.now(ZoneOffset.UTC).plusHours(1))));
            assertThat(cache.lookup(1L).kind()).isEqualTo(LoginSessionCache.Kind.CACHED);
            redis.execute((RedisCallback<Void>)connection->{connection.serverCommands().save();return null;});
            String change=cache.beginChange(1L);cache.finishChange(1L,change);
            assertThat(redis.hasKey(settings.getNamespace()+":user:1")).isFalse();
            crash();start();ready(redis);
            // RDB restored a value from before revocation; this is intentionally unsafe input.
            assertThat(redis.opsForHash().get(settings.getNamespace()+":user:1","value")).isNotNull();
            when(bootstrap.awaitOlderTransactions(any())).thenReturn(false);
            assertThat(cache.lookup(1L).kind()).isEqualTo(LoginSessionCache.Kind.BYPASS);
            var blocked=new RedisLoginSessionCache(redis,bootstrap,settings);
            assertThat(blocked.lookup(1L).kind()).isEqualTo(LoginSessionCache.Kind.BYPASS);
            // Only after the DB transaction barrier confirms a fresh epoch may DB state be loaded.
            when(bootstrap.awaitOlderTransactions(any())).thenReturn(true);
            var recovered=new RedisLoginSessionCache(redis,bootstrap,settings);
            var reload=recovered.lookup(1L);assertThat(reload.kind()).isEqualTo(LoginSessionCache.Kind.LOAD);
            recovered.publish(1L,reload.ticket(),Optional.empty());
            var absent=recovered.lookup(1L);assertThat(absent.kind()).isEqualTo(LoginSessionCache.Kind.CACHED);
            assertThat(absent.session()).isNull();
        } finally {
            if(factory!=null)factory.destroy();
            crash();
        }
    }

    private void start() throws Exception {
        String cert=System.getenv("VC_BE_TEST_AUTH_REDIS_CERT").replace('\\','/');
        String key=System.getenv("VC_BE_TEST_AUTH_REDIS_KEY").replace('\\','/');
        process=new ProcessBuilder(System.getenv("VC_BE_TEST_AUTH_REDIS_EXECUTABLE"),"--bind","127.0.0.1","--port","0",
                "--tls-port",Integer.toString(port),"--tls-cert-file",cert,"--tls-key-file",key,"--tls-ca-cert-file",cert,
                "--tls-auth-clients","no","--save","","--appendonly","no","--dir",directory.toString().replace('\\','/'),
                "--dbfilename","fixture.rdb")
                .directory(directory.toFile()).redirectErrorStream(true)
                .redirectOutput(directory.resolve("redis-"+(++starts)+".log").toFile()).start();
    }
    private void ready(StringRedisTemplate redis) throws Exception {
        long deadline=System.nanoTime()+Duration.ofSeconds(12).toNanos();
        while(System.nanoTime()<deadline) {
            assertThat(process.isAlive()).isTrue();
            try {if("PONG".equals(redis.execute((RedisCallback<String>)connection->connection.ping())))return;}
            catch(RuntimeException reconnecting) { /* Bound wait on our own process only. */ }
            Thread.sleep(100);
        }
        throw new IllegalStateException("Private Redis fixture did not become ready");
    }
    private void crash() throws Exception {
        if(process!=null && process.isAlive()) {
            process.destroyForcibly();assertThat(process.waitFor(5,TimeUnit.SECONDS)).isTrue();
        }
    }
}
