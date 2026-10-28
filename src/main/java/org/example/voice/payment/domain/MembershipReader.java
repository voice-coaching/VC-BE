package org.example.voice.payment.domain;

public interface MembershipReader {
    Membership read(Long userId);
}
