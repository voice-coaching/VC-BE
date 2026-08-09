package org.example.voice.practicecontent.infrastructure;

import org.example.voice.practicecontent.domain.entity.PracticeContent;
import org.example.voice.practicecontent.domain.type.PublishStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PracticeContentJpaRepository extends JpaRepository<PracticeContent, Long> {

    boolean existsByIdAndStatus(Long id, PublishStatus status);
}
