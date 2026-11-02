package org.example.voice.practicecontent.infrastructure;

import org.example.voice.practicecontent.domain.entity.PracticeContent;
import org.example.voice.practicecontent.domain.type.PublishStatus;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface PracticeContentJpaRepository extends JpaRepository<PracticeContent, Long>, JpaSpecificationExecutor<PracticeContent> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_READ)
    @org.springframework.data.jpa.repository.Query("select c from PracticeContent c where c.id=:id and c.deletedAt is null")
    java.util.Optional<PracticeContent> findLiveForSession(@org.springframework.data.repository.query.Param("id") Long id);

    boolean existsByIdAndStatus(Long id, PublishStatus status);

    List<PracticeContent> findByStatusOrderByPublishedAtDescCreatedAtDesc(PublishStatus status, Pageable pageable);
}
