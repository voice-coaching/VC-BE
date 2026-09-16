package org.example.voice.practicecontent.domain.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name="custom_content_requests",uniqueConstraints=@UniqueConstraint(columnNames={"user_id","key_digest"}))
public class CustomContentRequest {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="user_id",nullable=false) private Long userId;
    @Column(name="key_digest",nullable=false,length=64) private String keyDigest;
    @Column(name="request_digest",nullable=false,length=64) private String requestDigest;
    @Column(name="content_id",nullable=false) private Long contentId;
    public CustomContentRequest(Long userId,String key,String digest,Long contentId) {
        this.userId=userId;keyDigest=key;requestDigest=digest;this.contentId=contentId;
    }
}
