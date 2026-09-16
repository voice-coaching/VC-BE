package org.example.voice.support.infrastructure;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.example.voice.support.domain.entity.Notice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface NoticeJpaRepository extends JpaRepository<Notice, Long> {
    Page<Notice> findByPublishedTrueAndPublishedAtLessThanEqual(OffsetDateTime now, Pageable pageable);
    Optional<Notice> findByIdAndPublishedTrueAndPublishedAtLessThanEqual(Long id, OffsetDateTime now);
}
