package org.example.voice.support.infrastructure;

import java.util.Optional;
import org.example.voice.support.domain.entity.Inquiry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface InquiryJpaRepository extends JpaRepository<Inquiry, Long> {
    Page<Inquiry> findByUserId(Long userId, Pageable pageable);
    Optional<Inquiry> findByIdAndUserId(Long id, Long userId);
    Optional<Inquiry> findByUserIdAndIdempotencyKeySha256(Long userId, String keyDigest);
}
