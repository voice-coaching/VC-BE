package org.example.voice.payment.domain.model;
import java.time.OffsetDateTime;
public record MembershipFact(long userId,boolean active,boolean testGrant,OffsetDateTime paidUntil) {}
