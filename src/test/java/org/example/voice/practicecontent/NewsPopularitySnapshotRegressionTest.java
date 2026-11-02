package org.example.voice.practicecontent;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.example.voice.common.exception.GlobalExceptionHandler;
import org.example.voice.practicecontent.application.NewsPopularityService;
import org.example.voice.practicecontent.controller.NewsPopularityController;
import org.example.voice.practicecontent.controller.dto.PopularNewsResponseDto;
import org.example.voice.practicecontent.domain.type.ContentType;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real PostgreSQL at the current V61 schema: never touches production data. */
@EnabledIfEnvironmentVariable(named="VC_BE_TEST_POSTGRES_URL",matches=".+")
class NewsPopularitySnapshotRegressionTest {
    private JdbcTemplate admin;
    private JdbcTemplate jdbc;
    private String schema;
    private TransactionTemplate tx;
    private MutableClock clock;
    private NewsPopularityService service;

    @BeforeEach void database() {
        String url=System.getenv("VC_BE_TEST_POSTGRES_URL");
        assertThat(url).startsWith("jdbc:postgresql://127.0.0.1:");
        String user=System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_USER","postgres");
        String password=System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_PASSWORD","");
        schema="popular_snapshot_"+UUID.randomUUID().toString().replace("-","");
        admin=new JdbcTemplate(new DriverManagerDataSource(url,user,password));
        var migration=Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").target("61").load();
        migration.migrate();migration.validate();
        var db=new DriverManagerDataSource(url+(url.contains("?")?"&":"?")+"currentSchema="+schema,user,password);
        jdbc=new JdbcTemplate(db);tx=new TransactionTemplate(new DataSourceTransactionManager(db));
        clock=new MutableClock();service=new NewsPopularityService(jdbc,clock);
        jdbc.update("insert into practice_contents(content_type,learning_focus,category,title,script_text,difficulty,estimated_seconds,status,published_at,created_at,updated_at) "
                +"values('NEWS','PRONUNCIATION','ECONOMY','Snapshot fixture','First sentence. Second sentence.','BEGINNER',15,'PUBLISHED',now(),now(),now())");
    }
    @AfterEach void cleanup() { if(admin!=null&&schema!=null)admin.execute("drop schema if exists "+schema+" cascade"); }

    @Test void repeatedHttpRequestsRemainSuccessfulWithoutReinsertingTheDailyRows() throws Exception {
        // Standalone MVC has no transaction proxy: explicitly use the same boundary as @Transactional in production.
        var transactional=new NewsPopularityService(jdbc,clock) {
            @Override public PopularNewsResponseDto get(ContentType type,int limit) {
                return tx.execute(status->super.get(type,limit));
            }
        };
        var mvc=MockMvcBuilders.standaloneSetup(new NewsPopularityController(transactional))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        for(int i=0;i<3;i++) {
            mvc.perform(get("/api/practice-contents/popular").param("type","NEWS").param("limit","10"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.result").value(true))
                    .andExpect(jsonPath("$.data.items").isArray());
        }
        assertThat(jdbc.queryForObject("select count(*) from news_popularity_snapshots",Long.class)).isEqualTo(1);
        long eligible=jdbc.queryForObject("select count(*) from practice_contents where content_type='NEWS' and status='PUBLISHED' and owner_id is null and (published_at is null or published_at<=?)",Long.class,OffsetDateTime.now(clock));
        assertThat(jdbc.queryForObject("select count(*) from news_popularity_items",Long.class)).isEqualTo(eligible);
    }
    @Test void concurrentFirstReadsReturnTheSameSnapshotAndInsertItsItemsOnce() throws Exception {
        var ready=new CountDownLatch(2);var go=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<PopularNewsResponseDto> task=()->{ready.countDown();assertThat(go.await(5,TimeUnit.SECONDS)).isTrue();return read();};
            var first=pool.submit(task);var second=pool.submit(task);
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();go.countDown();
            var result=first.get(15,TimeUnit.SECONDS);
            assertThat(second.get(15,TimeUnit.SECONDS)).isEqualTo(result);
            assertThat(result.items()).isNotEmpty();
        }
        assertThat(jdbc.queryForObject("select count(*) from news_popularity_snapshots",Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from (select ranking_date,content_id,count(*) from news_popularity_items group by ranking_date,content_id having count(*)>1) duplicates",Long.class)).isZero();
    }
    @Test void restartAndLaterReadsKeepGeneratedAtUntilTheNextSeoulDay() {
        var initial=read();
        clock.value.set(Instant.parse("2050-11-05T04:00:00Z"));
        service=new NewsPopularityService(jdbc,clock);
        assertThat(read()).isEqualTo(initial);
        clock.value.set(Instant.parse("2050-11-05T15:00:00Z"));
        var next=read();
        assertThat(next.generatedAt()).isNotEqualTo(initial.generatedAt());
        assertThat(next.periodStart()).isEqualTo(initial.periodEnd());
        assertThat(jdbc.queryForObject("select count(*) from news_popularity_snapshots",Long.class)).isEqualTo(2);
    }
    @Test void failedInitialBuildRollsBackTheClaimAndCanBeRetried() {
        jdbc.execute("create function reject_snapshot_fixture() returns trigger language plpgsql as $$ begin raise exception 'fixture failure' using errcode='23514'; end; $$");
        jdbc.execute("create trigger reject_snapshot_fixture before insert on news_popularity_items for each row execute function reject_snapshot_fixture()");
        assertThatThrownBy(this::read).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("select count(*) from news_popularity_snapshots",Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from news_popularity_items",Long.class)).isZero();
        jdbc.execute("drop trigger reject_snapshot_fixture on news_popularity_items");
        assertThat(read().items()).isNotEmpty();
    }
    private PopularNewsResponseDto read() { return tx.execute(status->service.get(ContentType.NEWS,10)); }
    private static class MutableClock extends Clock {
        final AtomicReference<Instant> value=new AtomicReference<>(Instant.parse("2050-11-05T03:00:00Z"));
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){return this;}
        public Instant instant(){return value.get();}
    }
}
