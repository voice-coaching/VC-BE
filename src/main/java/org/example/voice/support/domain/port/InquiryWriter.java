package org.example.voice.support.domain.port;

import org.example.voice.support.domain.entity.Inquiry;

public interface InquiryWriter {
    Inquiry save(Inquiry inquiry);
}
