package org.example.voice.profileimage.domain.model;

import java.time.OffsetDateTime;

public record ProfileImageData(Long id, String imageUrl, String originalFileName, String mimeType,
                               long sizeBytes, OffsetDateTime updatedAt) {}
