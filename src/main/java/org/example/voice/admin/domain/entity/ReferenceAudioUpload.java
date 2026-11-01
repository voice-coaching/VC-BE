package org.example.voice.admin.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.voice.admin.domain.model.AdminAudioData.Upload;
import org.example.voice.admin.domain.type.ReferenceUploadState;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Getter @Table(name="admin_reference_audio_uploads")
@NoArgsConstructor(access=AccessLevel.PROTECTED)
public class ReferenceAudioUpload {
    @Id private UUID id;
    @Column(name="actor_user_id",nullable=false) private long actorId;
    @Column(name="content_id",nullable=false) private long contentId;
    @Column(nullable=false,length=5) private String extension;
    @Column(name="content_type",nullable=false,length=30) private String contentType;
    @Column(name="size_bytes",nullable=false) private long size;
    @Column(nullable=false,length=64) private String sha256;
    @Column(name="script_sha256",nullable=false,length=64) private String scriptSha256;
    @Column(name="expires_at",nullable=false) private OffsetDateTime expiresAt;
    @Column(name="created_at",nullable=false) private OffsetDateTime createdAt;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private ReferenceUploadState state;
    @Column(name="reference_audio_id",unique=true) private Long audioId;
    @Column(name="completion_digest",length=64) private String completionDigest;

    public static ReferenceAudioUpload pending(Upload u,OffsetDateTime now) {
        var intent=new ReferenceAudioUpload();intent.id=u.id();intent.actorId=u.actorId();intent.contentId=u.contentId();
        intent.extension=u.extension();intent.contentType=u.contentType();intent.size=u.size();intent.sha256=u.sha256();
        intent.scriptSha256=u.scriptSha256();intent.expiresAt=u.expiresAt();intent.createdAt=now;intent.state=ReferenceUploadState.PENDING;
        return intent;
    }
    public Upload snapshot() { return new Upload(id,actorId,contentId,extension,contentType,size,sha256,scriptSha256,expiresAt,audioId,completionDigest); }
    public void complete(long audioId,String digest) {
        if(state!=ReferenceUploadState.PENDING)throw new IllegalStateException("UPLOAD_ALREADY_COMPLETED");
        this.audioId=audioId;this.completionDigest=digest;this.state=ReferenceUploadState.COMPLETED;
    }
}
