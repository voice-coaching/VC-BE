package org.example.voice.scriptlibrary.domain;

import jakarta.persistence.*;
import lombok.*;
import org.example.voice.practicecontent.domain.model.PrivateText;

@Entity @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
@Table(name="script_library_topics")
public class ScriptLibraryTopic {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="user_id",nullable = true) private Long userId;
    @Column(name="name_ciphertext",nullable = true,columnDefinition="text") private PrivateText name;
    @Column(name="name_digest",nullable = true,length=64) private String nameDigest;
    public ScriptLibraryTopic(Long userId,String name,String digest){this.userId=userId;rename(name,digest);}
    public void rename(String name,String digest){this.name=new PrivateText(name);this.nameDigest=digest;}
}
