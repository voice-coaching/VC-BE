package org.example.voice.auth.infrastructure.sessioncache;

import lombok.extern.slf4j.Slf4j;
import org.example.voice.auth.domain.model.LoginSessionState;
import org.example.voice.auth.domain.port.LoginSessionCache;
import org.example.voice.auth.exception.SessionStoreUnavailableException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

@Slf4j
public class RedisLoginSessionCache implements LoginSessionCache {
    private final StringRedisTemplate redis;
    private final SessionCacheBootstrap bootstrap;
    private final SessionCacheProperties settings;
    private final DefaultRedisScript<List> script;
    private volatile boolean initialized;
    private volatile long retryAfter;

    public RedisLoginSessionCache(StringRedisTemplate redis, SessionCacheBootstrap bootstrap, SessionCacheProperties settings) {
        this.redis=redis;this.bootstrap=bootstrap;this.settings=settings;
        {}
        script=new DefaultRedisScript<>();script.setLocation(new ClassPathResource("auth/session-cache.lua"));script.setResultType(List.class);
    }

    @Override public Lookup lookup(Long userId) {
        if(!initialize())return Lookup.bypass();
        try {
            var response=execute("LOOKUP",userId,UUID.randomUUID().toString(),"",settings.getLoadingTtl().toMillis());
            return switch(tag(response)) {
                case "CACHED" -> new Lookup(Kind.CACHED,parse(response.get(1).toString()),null);
                case "LOAD" -> new Lookup(Kind.LOAD,null,response.get(1).toString());
                case "BOOTSTRAP" -> { initialized=false;retryAfter=0;yield Lookup.bypass(); }
                default -> Lookup.bypass();
            };
        } catch(RuntimeException unavailable) {
            pause();return Lookup.bypass();
        }
    }

    @Override public void publish(Long userId,String ticket,Optional<LoginSessionState> session) {
        if(ticket==null)return;
        long ttl=settings.getTtl().toMillis();
        String value="N";
        if(session.isPresent()) {
            var row=session.get();
            if(row.sessionId()==null || row.expiresAt()==null)return;
            long expires=row.expiresAt().toInstant().toEpochMilli();
            ttl=Math.min(ttl,expires-System.currentTimeMillis());
            if(ttl<=0)return;
            value="A:"+Base64.getUrlEncoder().withoutPadding().encodeToString(row.sessionId().getBytes(StandardCharsets.UTF_8))+":"+expires;
        }
        try { execute("PUBLISH",userId,ticket,value,Math.max(1,ttl)); }
        catch(RuntimeException unavailable) {pause();}
    }

    @Override public String beginChange(Long userId) {
        initialize(); // An incomplete bootstrap leaves the global gate closed; changes can still be fenced.
        String change=UUID.randomUUID().toString();
        try {

            return change;
        } catch(RuntimeException unavailable) {
            pause();throw new SessionStoreUnavailableException();
        }
    }

    @Override public void finishChange(Long userId,String changeId) {
        try { execute("END",userId,changeId,"",0); }
        catch(RuntimeException unavailable) {
            // The persistent marker keeps every instance on DB validation until a safe bootstrap.
            pause();log.warn("Auth session cache completion deferred; DB validation remains required");
        }
    }

    private synchronized boolean initialize() {
        if(initialized)return true;

        try {
            String owner=UUID.randomUUID().toString();
            var begun=execute("BOOT_BEGIN",0L,owner,"",0);
            if(!"BOOT".equals(tag(begun))) {pause();return false;}
            // A non-evictable marker is a correctness requirement, not a performance preference.
            Properties policy=redis.execute((RedisCallback<Properties>)connection->connection.serverCommands().getConfig("maxmemory-policy"));
            if(policy==null || !"noeviction".equals(policy.getProperty("maxmemory-policy"))
                    || !bootstrap.awaitOlderTransactions(settings.getBootstrapTimeout())) {pause();return false;}
            var ended=execute("BOOT_END",0L,owner,begun.get(1).toString(),0);
            initialized="OK".equals(tag(ended));
            if(!initialized)pause();
            return initialized;
        } catch(RuntimeException unavailable) {pause();return false;}
    }

    private void pause() { initialized=false;retryAfter=System.nanoTime()+java.time.Duration.ofSeconds(1).toNanos(); }
    private List<?> execute(String mode,Long userId,String owner,String value,long ttl) {
        return redis.execute(script,List.of(settings.getNamespace()+":meta",settings.getNamespace()+":user:"+userId),
                mode,owner,value,Long.toString(ttl));
    }
    private static String tag(List<?> response) {return response==null || response.isEmpty()?"BYPASS":response.getFirst().toString();}
    private static LoginSessionState parse(String value) {
        if("N".equals(value))return null;
        String[] fields=value.split(":",-1);

        return new LoginSessionState(new String(Base64.getUrlDecoder().decode(fields[1]),StandardCharsets.UTF_8),
                Instant.ofEpochMilli(Long.parseLong(fields[2])).atOffset(ZoneOffset.UTC));
    }
}
