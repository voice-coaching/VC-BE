package org.example.voice.scriptlibrary.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
@Table(name="script_library_requests")
public class ScriptLibraryRequest {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="user_id",nullable = true) private Long userId;
    @Column(name="key_digest",nullable = true,length=64) private String keyDigest;
    @Column(name="request_digest",nullable = true,length=64) private String requestDigest;
    @Column(name="content_id",nullable = true) private Long contentId;
    public ScriptLibraryRequest(Long user,String key,String digest,Long content){userId=user;keyDigest=key;requestDigest=digest;contentId=content;}
}
