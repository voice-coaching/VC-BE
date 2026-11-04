package org.example.voice.admin.domain.port;

import org.example.voice.admin.domain.entity.ReferenceAudioUpload;
import org.example.voice.admin.domain.model.AdminAudioData.*;
import org.example.voice.practicecontent.domain.entity.ReferenceAudio;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdminAudioStore {
    void save(ReferenceAudioUpload upload);
    Optional<Upload> upload(UUID id,long actor,long content);
    ReferenceAudioUpload lockUpload(UUID id,long actor,long content);
    List<Audio> audios(long contentId);
    Optional<Audio> audio(long audioId);
    void clearPrimaries(long contentId);
    void makePrimary(long audioId);
    void retire(long audioId,java.time.OffsetDateTime now);
    void save(ReferenceAudio audio);
    void advanceVersion(org.example.voice.practicecontent.domain.entity.PracticeContent content);
}
