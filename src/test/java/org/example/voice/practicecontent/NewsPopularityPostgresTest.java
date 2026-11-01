package org.example.voice.practicecontent;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.example.voice.common.exception.BaseException;
import org.example.voice.practicecontent.application.NewsPopularityService;
import org.example.voice.practicecontent.controller.dto.PopularNewsResponseDto;
import org.example.voice.practicecontent.domain.type.ContentType;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="VC_BE_TEST_POSTGRES_URL", matches=".+")
class NewsPopularityPostgresTest {
    @Test
    void dailySnapshotsDeduplicateLearnersAndRemainAtomicAcrossConcurrentRequests() throws Exception {
        String url = System.getenv("VC_BE_TEST_POSTGRES_URL");
        String user = System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_USER", "postgres");
        String password = System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_PASSWORD", "");
        String schema = "popular_news_" + UUID.randomUUID().toString().replace("-", "");
        var admin = new JdbcTemplate(new DriverManagerDataSource(url,user,password));
        try {
            migrate(url,user,password,schema,"58");
            var db = new DriverManagerDataSource(url+(url.contains("?")?"&":"?")+"currentSchema="+schema,user,password);
            var jdbc = new JdbcTemplate(db);
            int existing = jdbc.queryForObject("SELECT COUNT(*) FROM practice_contents",Integer.class);
            migrate(url,user,password,schema,"60");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM practice_contents",Integer.class)).isEqualTo(existing);
            int seededNews = jdbc.queryForObject("SELECT COUNT(*) FROM practice_contents WHERE content_type='NEWS' AND status='PUBLISHED' AND owner_id IS NULL",Integer.class);
            var clock = new MutableClock("2050-11-05T03:00:00Z");
            var service = new NewsPopularityService(jdbc,clock);
            var tx = new TransactionTemplate(new DataSourceTransactionManager(db));
            java.util.function.Supplier<PopularNewsResponseDto> read = () -> tx.execute(s -> service.get(ContentType.NEWS,10));
            List<Long> ids = new ArrayList<>();
            for (int i=0;i<12;i++) ids.add(content(jdbc,"NEWS","PUBLISHED",null,"2050-11-01T00:00:00Z",i));
            long a=ids.get(0), b=ids.get(1);
            var initial = read.get();
            assertThat(initial.rankingBasis()).isEqualTo("CATALOG_ORDER");
            assertThat(initial.items()).hasSize(10);
            assertThat(initial.items().getFirst().id()).isEqualTo(ids.getLast());
            assertThat(initial.items()).extracting(PopularNewsResponseDto.Item::rank).containsExactly(1,2,3,4,5,6,7,8,9,10);
            assertThat(initial.periodStart().toInstant()).isEqualTo(Instant.parse("2050-11-03T15:00:00Z"));
            assertThat(initial.periodEnd().toInstant()).isEqualTo(Instant.parse("2050-11-04T15:00:00Z"));
            assertThat(initial.nextUpdateAt().toInstant()).isEqualTo(Instant.parse("2050-11-05T15:00:00Z"));
            long u1=learner(jdbc,"ACTIVE"), u2=learner(jdbc,"ACTIVE");
            for (int i=0;i<5;i++) complete(jdbc,a,u1,"COMPLETED","2050-11-05T01:00:00Z");
            complete(jdbc,a,u2,"COMPLETED","2050-11-05T02:00:00Z");
            // Exact lower bound is included; exact upper bound is excluded.
            complete(jdbc,b,u1,"COMPLETED","2050-11-04T15:00:00Z");
            complete(jdbc,b,u2,"COMPLETED","2050-11-05T02:00:00Z");
            complete(jdbc,b,learner(jdbc,"ACTIVE"),"COMPLETED","2050-11-05T02:00:00Z");
            complete(jdbc,a,learner(jdbc,"ACTIVE"),"COMPLETED","2050-11-05T15:00:00Z");
            complete(jdbc,a,learner(jdbc,"ACTIVE"),"COMPLETED","2050-11-04T14:59:59Z");
            complete(jdbc,a,learner(jdbc,"ACTIVE"),"FAILED","2050-11-05T02:00:00Z");
            complete(jdbc,a,learner(jdbc,"WITHDRAWN"),"COMPLETED","2050-11-05T02:00:00Z");
            long deleted=learner(jdbc,"ACTIVE");
            jdbc.update("UPDATE users SET deleted_at=now() WHERE id=?",deleted);
            complete(jdbc,a,deleted,"COMPLETED","2050-11-05T02:00:00Z");
            List<Long> excluded = List.of(
                    content(jdbc,"NEWS","HIDDEN",null,"2050-11-01T00:00:00Z",20),
                    content(jdbc,"NEWS","HIDDEN",u1,"2050-11-01T00:00:00Z",21),
                    content(jdbc,"NEWS","PUBLISHED",null,"2051-11-01T00:00:00Z",22),
                    content(jdbc,"SENTENCE","PUBLISHED",null,"2050-11-01T00:00:00Z",23));
            for (long id:excluded) complete(jdbc,id,u1,"COMPLETED","2050-11-05T01:00:00Z");
            clock.value.set(Instant.parse("2050-11-05T14:59:59Z"));
            assertThat(read.get()).isEqualTo(initial);
            clock.value.set(Instant.parse("2050-11-05T15:00:00Z"));
            // Each worker uses an independent connection and a real transaction.
            var ready = new CountDownLatch(2);
            var go = new CountDownLatch(1);
            try(var pool=Executors.newFixedThreadPool(2)) {
                java.util.concurrent.Callable<PopularNewsResponseDto> task=()-> {
                    ready.countDown(); assertThat(go.await(5,TimeUnit.SECONDS)).isTrue(); return read.get();
                };
                var first=pool.submit(task); var second=pool.submit(task);
                assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue(); go.countDown();
                var ranked=first.get(10,TimeUnit.SECONDS);
                assertThat(second.get(10,TimeUnit.SECONDS)).isEqualTo(ranked);
                assertThat(ranked.rankingBasis()).isEqualTo("COMPLETED_USERS");
                assertThat(ranked.items().get(0).id()).isEqualTo(b);
                assertThat(ranked.items().get(0).usageCount()).isEqualTo(3);
                assertThat(ranked.items().get(1).id()).isEqualTo(a);
                assertThat(ranked.items().get(1).usageCount()).isEqualTo(2);
                assertThat(ranked.items()).extracting(PopularNewsResponseDto.Item::id).doesNotContainAnyElementsOf(excluded);
            }
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM news_popularity_snapshots",Integer.class)).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM news_popularity_items WHERE ranking_date='2050-11-06'",Integer.class)).isEqualTo(seededNews+12);
            jdbc.update("UPDATE practice_contents SET status='HIDDEN' WHERE id=?",b);
            var hidden=read.get();
            assertThat(hidden.items()).hasSize(10).extracting(PopularNewsResponseDto.Item::id).doesNotContain(b);
            assertThat(hidden.items().getFirst().id()).isEqualTo(a);
            assertThat(hidden.items()).extracting(PopularNewsResponseDto.Item::rank).containsExactly(1,2,3,4,5,6,7,8,9,10);
            assertThat(tx.execute(s->service.get(ContentType.NEWS,1)).items()).hasSize(1);
            for (int limit:List.of(0,11)) assertThatThrownBy(()->tx.execute(s->service.get(ContentType.NEWS,limit))).isInstanceOf(BaseException.class);
            assertThatThrownBy(()->tx.execute(s->service.get(ContentType.SENTENCE,10))).isInstanceOf(BaseException.class);
            jdbc.update("UPDATE practice_contents SET status='HIDDEN' WHERE content_type='NEWS'");
            assertThat(read.get().items()).isEmpty();
        } finally { admin.execute("DROP SCHEMA IF EXISTS "+schema+" CASCADE"); }
    }

    private static void migrate(String url,String user,String password,String schema,String target) {
        var flyway=Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").target(target).load();
        flyway.migrate(); flyway.validate();
    }
    private static long content(JdbcTemplate jdbc,String type,String status,Long owner,String published,int index) {
        return jdbc.queryForObject("""
                INSERT INTO practice_contents(content_type,learning_focus,category,title,script_text,difficulty,
                    estimated_seconds,status,published_at,created_at,updated_at,owner_id,custom_script_ciphertext,custom_title_ciphertext)
                VALUES (?,'PRONUNCIATION','ECONOMY',?,?,'BEGINNER',20,?,?,?,now(),?,?,?) RETURNING id
                """,Long.class,type,owner==null?"Popularity fixture "+index:"\uB0B4 \uBB38\uC7A5",
                owner==null?"First sentence. Next sentence.":"[private]",status,Timestamp.from(Instant.parse(published)),
                Timestamp.from(Instant.parse("2050-11-01T00:00:00Z").plusSeconds(index)),owner,
                owner==null?null:"test-ciphertext",owner==null?null:"test-ciphertext");
    }
    private static long learner(JdbcTemplate jdbc,String status) {
        return jdbc.queryForObject("""
                INSERT INTO users(nickname,status,terms_agreed_at,privacy_agreed_at,created_at,updated_at,role)
                VALUES ('Popularity tester',?,now(),now(),now(),now(),'USER') RETURNING id
                """,Long.class,status);
    }
    private static void complete(JdbcTemplate jdbc,long content,long user,String status,String completed) {
        jdbc.update("""
                INSERT INTO training_sessions(user_id,content_id,learning_focus,status,started_at,completed_at,total_learning_seconds)
                VALUES (?,?,'PRONUNCIATION',?,?,?,10)
                """,user,content,status,Timestamp.from(Instant.parse(completed).minusSeconds(10)),Timestamp.from(Instant.parse(completed)));
    }
    private static class MutableClock extends Clock {
        final AtomicReference<Instant> value;
        MutableClock(String instant) { value=new AtomicReference<>(Instant.parse(instant)); }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return value.get(); }
    }
}
