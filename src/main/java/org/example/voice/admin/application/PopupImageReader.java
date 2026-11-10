package org.example.voice.admin.application;

import java.time.*;
import org.example.voice.admin.domain.model.AdminCommunicationData.PopupImageView;
import lombok.RequiredArgsConstructor;
import org.example.voice.admin.domain.port.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class PopupImageReader {
    private final PopupImageStore images;
    private final PopupImageStorage storage;
    private final Clock clock;
    public PopupImageView image(long popup,OffsetDateTime endsAt) {
        var remaining=Duration.between(OffsetDateTime.now(clock),endsAt);
        if(remaining.isNegative()||remaining.isZero())return null;
        return images.attached(popup).map(image->new PopupImageView(image.getId(),storage.url(image.getObjectKey(),
                remaining.compareTo(Duration.ofMinutes(5))<0?remaining:Duration.ofMinutes(5)),endsAt)).orElse(null);
    }
}
