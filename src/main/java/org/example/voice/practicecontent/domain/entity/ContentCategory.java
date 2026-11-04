package org.example.voice.practicecontent.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.voice.practicecontent.domain.type.ContentType;

@Entity @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
@Table(name="content_categories")
public class ContentCategory {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Enumerated(EnumType.STRING) @Column(name="content_type",nullable = true) private ContentType contentType;
    @Column(nullable = true,length=100) private String code;
    @Column(nullable = true,length=100) private String label;
    @Column(name="sort_order",nullable = true) private Integer sortOrder;
}
