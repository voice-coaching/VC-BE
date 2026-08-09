package org.example.voice.practicecontent.infrastructure;

import org.example.voice.practicecontent.domain.entity.ReferenceAudio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReferenceAudioJpaRepository extends JpaRepository<ReferenceAudio, Long> {

    List<ReferenceAudio> findByContentIdOrderByPrimaryDescIdAsc(Long contentId);

    boolean existsByContentId(Long contentId);
}
