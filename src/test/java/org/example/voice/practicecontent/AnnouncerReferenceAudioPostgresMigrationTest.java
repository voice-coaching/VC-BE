package org.example.voice.practicecontent;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.DriverManager;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "VC_BE_TEST_POSTGRES_URL", matches = ".+")
class AnnouncerReferenceAudioPostgresMigrationTest {
    @Test
    void announcerReadAlongSeedConnectsTenScriptsToPrivateReferenceObjects() throws Exception {
        String url = System.getenv("VC_BE_TEST_POSTGRES_URL");
        String user = System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_USER", "postgres");
        String password = System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_PASSWORD", "");
        String schema = "announcer_reference_audio_test_" + UUID.randomUUID().toString().replace("-", "");

        try (var db = DriverManager.getConnection(url, user, password); var sql = db.createStatement()) {
            try {
                Flyway.configure()
                        .dataSource(url, user, password)
                        .schemas(schema)
                        .defaultSchema(schema)
                        .locations("classpath:db/migration")
                        .target("47")
                        .load()
                        .migrate();
                db.setSchema(schema);

                try (var rows = sql.executeQuery("""
                        select count(*)
                        from practice_contents content
                        join reference_audios audio on audio.content_id=content.id
                        where content.content_type='ANNOUNCER'
                          and content.category='ANNOUNCER_PRACTICE'
                          and content.title like '아나운서 따라 읽기 %'
                          and audio.speaker_type='ANNOUNCER'
                          and audio.is_primary=true
                          and audio.audio_url is null
                          and audio.object_key like 'reference-audios/announcer/%'
                        """)) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getInt(1)).isEqualTo(10);
                }
            } finally {
                db.setSchema("public");
                sql.execute("drop schema if exists " + schema + " cascade");
            }
        }
    }
}
