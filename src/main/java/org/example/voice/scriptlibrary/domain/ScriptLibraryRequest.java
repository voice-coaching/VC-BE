package org.example.voice.scriptlibrary.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
@Table(name="script_library_requests",uniqueConstraints=@UniqueConstraint(columnNames={"user_id","key_digest"}))
public class ScriptLibraryRequest {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="user_id",nullable=false) private Long userId;
    @Column(name="key_digest",nullable=false,length=64) private String keyDigest;
    @Column(name="request_digest",nullable=false,length=64) private String requestDigest;
    @Column(name="content_id",nullable=false) private Long contentId;
    public ScriptLibraryRequest(Long user,String key,String digest,Long content){userId=user;keyDigest=key;requestDigest=digest;contentId=content;}
}
