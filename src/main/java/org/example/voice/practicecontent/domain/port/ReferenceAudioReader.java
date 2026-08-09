package org.example.voice.practicecontent.domain.port;

import org.example.voice.practicecontent.domain.model.ReferenceAudioData;
import org.example.voice.practicecontent.domain.model.ReferenceAudioPlaybackUrlData;

import java.util.List;
import java.util.Optional;

public interface ReferenceAudioReader {

    boolean existsPracticeContent(Long contentId);

    List<ReferenceAudioData> findReferenceAudiosByContentId(Long contentId);

    Optional<ReferenceAudioPlaybackUrlData> findPlaybackUrl(Long audioId);
}
