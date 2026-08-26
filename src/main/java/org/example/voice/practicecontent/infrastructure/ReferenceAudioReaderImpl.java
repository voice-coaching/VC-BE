package org.example.voice.practicecontent.infrastructure;

import lombok.RequiredArgsConstructor;
import org.example.voice.practicecontent.domain.entity.ReferenceAudio;
import org.example.voice.practicecontent.domain.model.ReferenceAudioData;
import org.example.voice.practicecontent.domain.model.ReferenceAudioListData;
import org.example.voice.practicecontent.domain.model.ReferenceAudioPlaybackUrlData;
import org.example.voice.practicecontent.domain.port.ReferenceAudioReader;
import org.example.voice.practicecontent.domain.type.PublishStatus;
import org.example.voice.practicecontent.infrastructure.cache.PracticeContentCacheNames;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReferenceAudioReaderImpl implements ReferenceAudioReader {

    private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");

    private final PracticeContentJpaRepository practiceContentJpaRepository;
    private final ReferenceAudioJpaRepository referenceAudioJpaRepository;

    @Override
    @Cacheable(
            cacheNames = PracticeContentCacheNames.EXISTS,
            key = "#p0",
            unless = "#result == false"
    )
    public boolean existsPracticeContent(Long contentId) {
        return practiceContentJpaRepository.existsByIdAndStatus(contentId, PublishStatus.PUBLISHED);
    }

    @Override
    @Cacheable(
            cacheNames = PracticeContentCacheNames.REFERENCE_AUDIO_LIST,
            key = "#p0"
    )
    public ReferenceAudioListData findReferenceAudiosByContentId(Long contentId) {
        List<ReferenceAudioData> items = referenceAudioJpaRepository.findByContentIdOrderByPrimaryDescIdAsc(contentId)
                .stream()
                .map(this::toData)
                .toList();
        return new ReferenceAudioListData(items);
    }

    @Override
    public Optional<ReferenceAudioPlaybackUrlData> findPlaybackUrl(Long audioId) {
        return referenceAudioJpaRepository.findById(audioId)
                .map(audio -> new ReferenceAudioPlaybackUrlData(
                        audio.getId(),
                        audio.getAudioUrl(),
                        OffsetDateTime.now(SEOUL_ZONE_ID).plusMinutes(10)
                ));
    }

    private ReferenceAudioData toData(ReferenceAudio audio) {
        return new ReferenceAudioData(
                audio.getId(),
                audio.getSpeakerName(),
                audio.getSpeakerType(),
                audio.getDurationMs(),
                audio.getPrimary()
        );
    }
}
