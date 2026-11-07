package org.example.voice.training.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonParser;
import org.erdtman.jcs.JsonCanonicalizer;
import org.example.voice.training.domain.model.MediaPreparationData;
import org.example.voice.training.domain.port.RecordingMediaPreparationStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;

@Repository
public class RecordingMediaPreparationStoreImpl implements RecordingMediaPreparationStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    public RecordingMediaPreparationStoreImpl(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void register(long recordingId, MediaPreparationData preparation) {
        try {
            byte[] raw = new JsonCanonicalizer(mapper.writeValueAsString(preparation)).getEncodedUTF8();
            jdbc.update("""
                INSERT INTO recording_media_preparations(recording_id,schema_version,receipt_bytes,receipt_sha256)
                VALUES (?,?,?,?)
                """, recordingId, preparation.schemaVersion(), raw, digest(raw));
        } catch (java.io.IOException error) { throw new IllegalStateException("MEDIA_PREPARATION_INVALID"); }
    }

    @Override @Transactional(readOnly=true)
    public Optional<MediaPreparationData> find(long recordingId) {
        return jdbc.query("SELECT receipt_bytes,receipt_sha256 FROM recording_media_preparations WHERE recording_id=?",
                (row,index) -> {
                    byte[] raw = row.getBytes("receipt_bytes");
                    if (!digest(raw).equals(row.getString("receipt_sha256"))) throw new IllegalStateException("MEDIA_PREPARATION_INVALID");
                    try { return mapper.readValue(raw, MediaPreparationData.class); }
                    catch (java.io.IOException error) { throw new IllegalStateException("MEDIA_PREPARATION_INVALID"); }
                }, recordingId).stream().findFirst();
    }

    private static String digest(byte[] raw) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)); }
        catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
}
