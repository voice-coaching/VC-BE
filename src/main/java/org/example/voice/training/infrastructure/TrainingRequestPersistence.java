package org.example.voice.training.infrastructure;

import lombok.RequiredArgsConstructor;
import org.example.voice.training.domain.model.TrainingRequestReceipt;
import org.example.voice.training.domain.port.TrainingRequestStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class TrainingRequestPersistence implements TrainingRequestStore {
    private final JdbcTemplate jdbc;

    @Override
    public Optional<TrainingRequestReceipt> find(Long userId, String operation, String keySha256) {
        return jdbc.query("""
                SELECT request_sha256, response_json FROM training_request_receipts
                WHERE user_id=? AND operation=? AND key_sha256=?
                """, (rs, n) -> new TrainingRequestReceipt(rs.getString(1), rs.getString(2)),
                userId, operation, keySha256).stream().findFirst();
    }

    @Override
    public void save(Long userId, String operation, String keySha256, TrainingRequestReceipt receipt) {
        jdbc.update("""
                INSERT INTO training_request_receipts(user_id,operation,key_sha256,request_sha256,response_json,created_at)
                VALUES(?,?,?,?,?,CURRENT_TIMESTAMP)
                """, userId, operation, keySha256, receipt.requestSha256(), receipt.responseJson());
    }
}
