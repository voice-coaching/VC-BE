package org.example.voice.practicecontent.infrastructure;

import org.example.voice.practicecontent.domain.entity.PracticeContent;
import org.example.voice.practicecontent.domain.type.PublishStatus;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface PracticeContentJpaRepository extends JpaRepository<PracticeContent, Long>, JpaSpecificationExecutor<PracticeContent> {

    boolean existsByIdAndStatus(Long id, PublishStatus status);

    List<PracticeContent> findByStatusOrderByPublishedAtDescCreatedAtDesc(PublishStatus status, Pageable pageable);
}
