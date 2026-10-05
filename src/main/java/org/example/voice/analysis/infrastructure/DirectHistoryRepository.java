package org.example.voice.analysis.infrastructure;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public class DirectHistoryRepository {
    private final JdbcTemplate jdbc;
    public DirectHistoryRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean receive(UUID job, UUID execution, String claim, String digest,
                           String result, String script, Long content, String archive) {
        return jdbc.update("""
            INSERT INTO direct_analysis_history(job_id,execution_id,claim_sha256,result_sha256,result_json,
                script_text,content_id,archive_json,archived_at)
            VALUES (?,?,?,?,?,?,?,?,CASE WHEN CAST(? AS text) IS NULL THEN NULL ELSE CURRENT_TIMESTAMP END)
            ON CONFLICT(job_id) DO UPDATE SET archive_json=COALESCE(EXCLUDED.archive_json,direct_analysis_history.archive_json),
                archived_at=COALESCE(direct_analysis_history.archived_at,EXCLUDED.archived_at)
            WHERE direct_analysis_history.execution_id=EXCLUDED.execution_id
                AND direct_analysis_history.claim_sha256=EXCLUDED.claim_sha256
                AND direct_analysis_history.result_sha256=EXCLUDED.result_sha256
                AND direct_analysis_history.result_json=EXCLUDED.result_json
                AND direct_analysis_history.script_text=EXCLUDED.script_text
                AND direct_analysis_history.content_id IS NOT DISTINCT FROM EXCLUDED.content_id
                AND (direct_analysis_history.archive_json IS NULL OR EXCLUDED.archive_json IS NULL
                     OR direct_analysis_history.archive_json=EXCLUDED.archive_json)
            """, job,execution,claim,digest,result,script,content,archive,archive) == 1;
    }

    public boolean link(long user, UUID job, String claim) {
        return jdbc.update("""
            INSERT INTO direct_analysis_history_links(user_id,job_id,claim_sha256) VALUES (?,?,?)
            ON CONFLICT(user_id,job_id) DO UPDATE SET claim_sha256=EXCLUDED.claim_sha256
            WHERE direct_analysis_history_links.claim_sha256=EXCLUDED.claim_sha256
            """,user,job,claim) == 1;
    }

    public List<Map<String,Object>> list(long user) {
        return jdbc.queryForList("""
            SELECT h.job_id,h.result_json,h.script_text,h.content_id,h.created_at,h.archived_at
            FROM direct_analysis_history_links l JOIN direct_analysis_history h
              ON h.job_id=l.job_id AND h.claim_sha256=l.claim_sha256
            WHERE l.user_id=? ORDER BY h.created_at DESC LIMIT 100
            """,user);
    }

    public String state(long user, UUID job) {
        var result=jdbc.queryForList("""
            SELECT CASE WHEN h.job_id IS NULL THEN 'PENDING'
                        WHEN l.claim_sha256<>h.claim_sha256 THEN 'CONFLICT'
                        WHEN h.archived_at IS NULL THEN 'RESULT_RECEIVED' ELSE 'SAVED' END AS state
            FROM direct_analysis_history_links l LEFT JOIN direct_analysis_history h ON h.job_id=l.job_id
            WHERE l.user_id=? AND l.job_id=?
            """,user,job);
        return result.isEmpty() ? "NOT_LINKED" : (String)result.getFirst().get("state");
    }
}
