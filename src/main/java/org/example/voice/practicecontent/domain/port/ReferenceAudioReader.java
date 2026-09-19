package org.example.voice.practicecontent.domain.port;

import org.example.voice.practicecontent.domain.model.ReferenceAudioListData;
import org.example.voice.practicecontent.domain.model.ReferenceAudioPlaybackUrlData;

import java.util.Optional;

public interface ReferenceAudioReader {

    boolean existsPracticeContent(Long contentId);

    ReferenceAudioListData findReferenceAudiosByContentId(Long contentId);

    Optional<ReferenceAudioPlaybackUrlData> findPlaybackUrl(Long audioId);
}
