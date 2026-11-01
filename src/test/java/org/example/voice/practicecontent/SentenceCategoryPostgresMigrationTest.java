package org.example.voice.practicecontent;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "VC_BE_TEST_POSTGRES_URL", matches = ".+")
class SentenceCategoryPostgresMigrationTest {
    @Test
    void upgradePublishesCategorizedSentencesWithoutChangingExistingContentAndCanBeReplayed() throws Exception {
        String url = System.getenv("VC_BE_TEST_POSTGRES_URL");
        String user = System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_USER", "postgres");
        String password = System.getenv().getOrDefault("VC_BE_TEST_POSTGRES_PASSWORD", "");
        String schema = "sentence_categories_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection db = DriverManager.getConnection(url, user, password);
             Statement sql = db.createStatement()) {
            try {
                migrate(url, user, password, schema, "57");
                db.setSchema(schema);
                Map<Long, String> before = contents(sql);
                migrate(url, user, password, schema, "58");
                db.setSchema(schema);
                Map<Long, String> after = contents(sql);
                assertThat(after).containsAllEntriesOf(before).hasSize(before.size() + 15);
                try (var rows = sql.executeQuery("""
                        SELECT c.category, COUNT(*) AS total,
                               COUNT(*) FILTER (WHERE c.status='PUBLISHED' AND c.owner_id IS NULL
                                 AND c.published_at <= now() AND c.learning_focus='PRONUNCIATION'
                                 AND length(trim(c.script_text)) > 0
                                 AND jsonb_array_length(c.target_pronunciations) > 0
                                 AND o.content_id IS NOT NULL) AS visible_with_tts
                        FROM practice_contents c LEFT JOIN content_tts_outbox o ON o.content_id=c.id
                        WHERE c.category IN ('FINAL_CONSONANT','CONSONANT','VOWEL')
                        GROUP BY c.category
                        """)) {
                    int categories = 0;
                    while (rows.next()) {
                        categories++;
                        assertThat(rows.getInt("total")).isEqualTo(5);
                        assertThat(rows.getInt("visible_with_tts")).isEqualTo(5);
                    }
                    assertThat(categories).isEqualTo(3);
                }
                try (var rows = sql.executeQuery("""
                        SELECT code, label FROM content_categories WHERE content_type='SENTENCE'
                        AND code IN ('FINAL_CONSONANT','TENSE_CONSONANT','CONSONANT','VOWEL')
                        """)) {
                    Map<String, String> labels = new LinkedHashMap<>();
                    while (rows.next()) labels.put(rows.getString(1), rows.getString(2));
                    assertThat(labels).containsExactlyInAnyOrderEntriesOf(Map.of(
                            "FINAL_CONSONANT", "받침", "TENSE_CONSONANT", "된소리",
                            "CONSONANT", "자음", "VOWEL", "모음"));
                }
                // Replaying the data statements must not create duplicate public scripts.
                try (var resource = getClass().getResourceAsStream(
                        "/db/migration/V58__seed_sentence_pronunciation_categories.sql")) {
                    assertThat(resource).isNotNull();
                    sql.execute(new String(resource.readAllBytes(), StandardCharsets.UTF_8));
                }
                assertThat(contents(sql)).isEqualTo(after);
            } finally {
                db.setSchema("public");
                sql.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        }
    }

    private Map<Long, String> contents(Statement sql) throws Exception {
        Map<Long, String> result = new LinkedHashMap<>();
        try (var rows = sql.executeQuery("SELECT id, row_to_json(c)::text FROM practice_contents c ORDER BY id")) {
            while (rows.next()) result.put(rows.getLong(1), rows.getString(2));
        }
        return result;
    }

    private void migrate(String url, String user, String password, String schema, String target) {
        var flyway = Flyway.configure().dataSource(url, user, password).schemas(schema)
                .defaultSchema(schema).locations("classpath:db/migration").target(target).load();
        flyway.migrate();
        flyway.validate();
    }
}
