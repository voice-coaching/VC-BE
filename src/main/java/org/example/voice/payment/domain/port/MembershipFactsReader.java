package org.example.voice.payment.domain.port;
import java.time.OffsetDateTime;
import java.util.*;
public interface MembershipFactsReader {
    List<org.example.voice.payment.domain.model.MembershipFact> readAll(Collection<Long> userIds,OffsetDateTime now);
}
