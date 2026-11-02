package org.example.voice.title.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity @Table(name = "title_exam_requests")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TitleExamRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = true) private Long userId;
    @Column(name = "key_digest", nullable = true, length = 64) private String keyDigest;
    @Column(name = "exam_id", nullable = true) private Long examId;
    public TitleExamRequest(Long userId, String keyDigest, Long examId) { this.userId=userId; this.keyDigest=keyDigest; this.examId=examId; }
}
